package net.krona.mutagen.mutation;

import net.krona.mutagen.MutagenConfig;
import net.krona.mutagen.body.Bodies;
import net.krona.mutagen.Mutagen;
import net.krona.mutagen.data.MutagenData;
import net.krona.mutagen.data.MutagenPlayer;
import net.krona.mutagen.knowledge.MutagenKnowledge;
import net.krona.mutagen.knowledge.Research;
import net.krona.mutagen.network.MutagenNetwork;
import net.krona.mutagen.strain.BodyShape;
import net.krona.mutagen.strain.Stage;
import net.krona.mutagen.strain.Strain;
import net.krona.mutagen.strain.Strains;
import net.krona.mutagen.strain.Trait;
import net.krona.mutagen.strain.Traits;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Server-side transformation logic for absorption, decay, stages, and their effects.
 * <p>
 * Doses add to the absorption reserve, which the body processes gradually. Progress decays without support.
 */
public final class Mutation {
    /** Progress absorbed per second, at 1 percentage point per 30 seconds. */
    private static final float ABSORB_PER_SECOND = 1.0F / 30.0F;
    /** Unsupported progress decays by 1 percentage point every 8 in-game minutes. */
    private static final float DECAY_PER_SECOND = 1.0F / (8.0F * 60.0F);

    private Mutation() {
    }

    /**
     * Put a dose into the absorption reserve for gradual processing.
     * <p>
     * The research-based {@code limit} caps progress plus pending absorption. A weak serum cannot push
     * progress beyond 40%.
     */
    public static boolean dose(ServerPlayer player, ResourceLocation strainId, float quality, int limit) {
        Strain strain = Strains.get(strainId);
        if (strain == null) {
            return false;
        }
        MutagenData data = MutagenPlayer.of(player);
        MutagenData.Gene existing = data.gene(strainId);
        if (existing == null && data.activeGeneCount() >= MutagenConfig.get().maxGenes) {
            MutagenData.Gene occupied = data.primary();
            Strain held = occupied == null ? null : Strains.get(occupied.strainId());
            Component heldName = held == null ? Component.literal("?") : held.displayName();
            // Use chat so the rejection and its instructions are easier to notice.
            player.sendSystemMessage(Component.translatable("mutagen.message.too_many_genes", heldName)
                    .withStyle(net.minecraft.ChatFormatting.RED));
            return false;
        }
        float reached = existing == null ? 0.0F : existing.progress() + existing.pending();
        float room = limit - reached;
        if (room <= 0.01F) {
            // Reject before injection so the player keeps the vial and sees what is missing.
            player.sendSystemMessage(Component.translatable("mutagen.message.serum_too_weak",
                    strain.displayName(), limit).withStyle(net.minecraft.ChatFormatting.RED));
            return false;
        }

        MutagenData.Gene gene = data.getOrCreate(strainId);
        float strength = MutagenConfig.get().doseStrength * Math.max(0.05F, quality / 100.0F);
        float amount = Math.min(strength, room);
        gene.addPending(amount);

        player.level().playSound(null, player.blockPosition(), SoundEvents.HONEY_BLOCK_SLIDE,
                SoundSource.PLAYERS, 0.6F, 1.6F);
        if (amount < strength) {
            player.displayClientMessage(Component.translatable("mutagen.message.dose_limited",
                    strain.displayName(), String.format("%.1f", amount), limit), true);
        } else {
            player.displayClientMessage(Component.translatable("mutagen.message.dose",
                    strain.displayName(), String.format("%.1f", amount)), true);
        }
        return true;
    }

    /** Reversal serum removes progress and causes pain. */
    public static boolean reverse(ServerPlayer player, float amount) {
        MutagenData data = MutagenPlayer.of(player);
        MutagenData.Gene gene = data.primary();
        if (gene == null) {
            return false;
        }
        Stage before = gene.stage();
        gene.setPending(Math.max(0.0F, gene.pending() - amount));
        gene.setProgress(gene.progress() - amount);
        data.markDirty();

        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200, 0));
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 300, 0));
        player.hurt(player.damageSources().magic(), 2.0F);

        Stage after = gene.stage();
        if (after != before) {
            onStageChanged(player, gene, before, after);
        }
        if (gene.progress() <= 0.0F) {
            data.remove(gene.strainId());
            clearAttributes(player);
        }
        return true;
    }

    /** Called every tick. The expensive work runs once per second. */
    public static void tick(ServerPlayer player) {
        MutagenData data = MutagenPlayer.of(player);
        if (player.tickCount % 20 != 0) {
            return;
        }

        if (!data.isEmpty()) {
            advance(player, data);
            runTraits(player, data);
            spawnParticles(player, data);
        }
        refreshAttributes(player, data);

        // The network layer decides whether the state changed enough to send.
        MutagenNetwork.syncPlayer(player, data.isDirty());
        data.clearDirty();
    }

    private static void advance(ServerPlayer player, MutagenData data) {
        float speed = MutagenConfig.get().transformationSpeed;
        List<MutagenData.Gene> finished = new ArrayList<>();
        MutagenKnowledge knowledge = MutagenKnowledge.get(player.server);

        for (MutagenData.Gene gene : data.genes()) {
            if (Strains.get(gene.strainId()) == null) {
                continue;
            }
            Stage before = gene.stage();

            if (gene.pending() > 0.0F) {
                float step = Math.min(gene.pending(), ABSORB_PER_SECOND * speed);
                gene.setPending(gene.pending() - step);
                gene.setProgress(gene.progress() + step);
            } else if (!gene.locked() && gene.progress() > 0.0F && !gene.stage().atLeast(Stage.FULL)
                    && !Research.mastered(knowledge.research(player.getUUID(), gene.strainId()))) {
                // Full form only reverses through reversal serum or a reset. Fully researched strains also
                // resist natural decay.
                gene.setProgress(gene.progress() - DECAY_PER_SECOND * speed);
                if (gene.progress() <= 0.0F) {
                    finished.add(gene);
                }
            }

            Stage after = gene.stage();
            if (after != before) {
                onStageChanged(player, gene, before, after);
            }
        }

        for (MutagenData.Gene gene : finished) {
            data.remove(gene.strainId());
            clearAttributes(player);
            player.displayClientMessage(Component.translatable("mutagen.message.strain_gone"), true);
        }
    }

    private static void runTraits(ServerPlayer player, MutagenData data) {
        for (MutagenData.Gene gene : data.genes()) {
            Strain strain = Strains.get(gene.strainId());
            if (strain == null) {
                continue;
            }
            Stage stage = gene.stage();
            for (Trait trait : strain.activeTraits(stage)) {
                trait.tick(player, stage);
            }
        }
    }

    private static void spawnParticles(ServerPlayer player, MutagenData data) {
        MutagenData.Gene gene = data.primary();
        if (gene == null || !gene.stage().atLeast(Stage.INFECTION)) {
            return;
        }
        Strain strain = Strains.get(gene.strainId());
        if (strain == null) {
            return;
        }
        int count = gene.stage().ordinal();
        Vector3f color = new Vector3f(
                ((strain.color() >> 16) & 0xFF) / 255.0F,
                ((strain.color() >> 8) & 0xFF) / 255.0F,
                (strain.color() & 0xFF) / 255.0F);
        player.serverLevel().sendParticles(new DustParticleOptions(color, 1.0F),
                player.getX(), player.getY() + player.getBbHeight() * 0.6D, player.getZ(),
                count, 0.35D, 0.5D, 0.35D, 0.01D);
    }

    private static void onStageChanged(ServerPlayer player, MutagenData.Gene gene, Stage from, Stage to) {
        Strain strain = Strains.get(gene.strainId());
        if (strain == null) {
            return;
        }
        boolean up = to.ordinal() > from.ordinal();

        // Apply withdrawal effects on stage transitions.
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 4, false, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 80, 0, false, false, false));
        player.level().playSound(null, player.blockPosition(),
                up ? SoundEvents.ZOMBIE_INFECT : SoundEvents.ZOMBIE_VILLAGER_CURE,
                SoundSource.PLAYERS, 0.8F, up ? 0.8F : 1.2F);

        player.sendSystemMessage(Component.translatable(
                up ? "mutagen.message.stage_up" : "mutagen.message.stage_down",
                strain.displayName(),
                to.displayName().copy().withStyle(to.color())));

        MutagenNetwork.sendStageEvent(player, to, up, strain.color());
        refreshAttributes(player, MutagenPlayer.of(player));
    }

    /**
     * Rebuild attribute modifiers for the current stages. This is idempotent and runs after death, on
     * reconnect, and every second.
     */
    public static void refreshAttributes(ServerPlayer player, MutagenData data) {
        Map<ResourceLocation, DesiredModifier> desired = new LinkedHashMap<>();

        for (MutagenData.Gene gene : data.genes()) {
            Strain strain = Strains.get(gene.strainId());
            if (strain == null) {
                continue;
            }
            Stage stage = gene.stage();
            String strainPath = gene.strainId().getPath();

            for (Trait trait : strain.activeTraits(stage)) {
                trait.attributes(stage, (attribute, amount, operation) -> {
                    ResourceLocation id = modifierId(strainPath, attribute, operation);
                    DesiredModifier current = desired.get(id);
                    double total = (current == null ? 0.0D : current.amount()) + amount;
                    desired.put(id, new DesiredModifier(attribute, total, operation));
                });
            }
        }

        // Scale the model, hitbox, eye height, and reach with transformation progress (0.3). The adapter
        // selects vanilla attributes or Pehkui.
        BodyShape shape = BodyShape.of(data.primary());
        Bodies.scaler().apply(player, shape, (attribute, amount, operation) -> {
            ResourceLocation id = Mutagen.id("body/" + attribute.unwrapKey()
                    .map(key -> key.location().getPath()).orElse("unknown"));
            desired.put(id, new DesiredModifier(attribute, amount, operation));
        });
        applyModifiers(player, desired);
        refreshDimensions(player);
    }

    /** Refresh the hitbox when the body shape changes. */
    public static void refreshDimensions(Player player) {
        EntityDimensions target = player.getDimensions(player.getPose());
        if (Math.abs(target.width() - player.getBbWidth()) > 1.0E-4F
                || Math.abs(target.height() - player.getBbHeight()) > 1.0E-4F
                || Math.abs(target.eyeHeight() - player.getEyeHeight()) > 1.0E-4F) {
            player.refreshDimensions();
        }
    }

    private static void applyModifiers(ServerPlayer player, Map<ResourceLocation, DesiredModifier> desired) {
        Map<ResourceLocation, DesiredModifier> remaining = new HashMap<>(desired);

        for (Holder<Attribute> attribute : Strains.trackedAttributes()) {
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance == null) {
                continue;
            }
            for (AttributeModifier modifier : List.copyOf(instance.getModifiers())) {
                if (!modifier.id().getNamespace().equals(Mutagen.MOD_ID)) {
                    continue;
                }
                DesiredModifier target = remaining.get(modifier.id());
                if (target != null && target.matches(modifier)) {
                    remaining.remove(modifier.id());
                } else {
                    instance.removeModifier(modifier.id());
                }
            }
        }

        for (Map.Entry<ResourceLocation, DesiredModifier> entry : remaining.entrySet()) {
            DesiredModifier target = entry.getValue();
            AttributeInstance instance = player.getAttribute(target.attribute());
            if (instance == null) {
                continue;
            }
            instance.removeModifier(entry.getKey());
            instance.addTransientModifier(new AttributeModifier(entry.getKey(), target.amount(), target.operation()));
        }
    }

    public static void clearAttributes(ServerPlayer player) {
        for (Holder<Attribute> attribute : Strains.trackedAttributes()) {
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance == null) {
                continue;
            }
            for (AttributeModifier modifier : List.copyOf(instance.getModifiers())) {
                if (modifier.id().getNamespace().equals(Mutagen.MOD_ID)) {
                    instance.removeModifier(modifier.id());
                }
            }
        }
    }

    private static ResourceLocation modifierId(String strainPath, Holder<Attribute> attribute,
                                               AttributeModifier.Operation operation) {
        String attributeKey = attribute.unwrapKey()
                .map(key -> key.location().getPath())
                .orElse("unknown");
        return Mutagen.id("strain/" + strainPath + "/" + attributeKey + "/" + operation.ordinal());
    }

    /** Damage weaknesses and immunities. */
    public static float modifyIncomingDamage(LivingEntity entity, DamageSource source, float amount) {
        if (!(entity instanceof Player player)) {
            return amount;
        }
        MutagenData data = MutagenPlayer.of(player);
        float result = amount;
        for (MutagenData.Gene gene : data.genes()) {
            Strain strain = Strains.get(gene.strainId());
            if (strain == null) {
                continue;
            }
            Stage stage = gene.stage();
            for (Trait trait : strain.activeTraits(stage)) {
                result = trait.modifyIncomingDamage(source, result, stage);
            }
        }
        return result;
    }

    /** Status-effect immunity. */
    /** Whether this species recognizes the player and refrains from attacking them. */
    public static boolean pacified(Player player, net.minecraft.world.entity.EntityType<?> kind) {
        MutagenData data = MutagenPlayer.of(player);
        for (MutagenData.Gene gene : data.genes()) {
            Strain strain = Strains.get(gene.strainId());
            if (strain == null) {
                continue;
            }
            Stage stage = gene.stage();
            for (Trait trait : strain.activeTraits(stage)) {
                if (trait.pacifies(kind, stage)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean blocksEffect(LivingEntity entity, Holder<MobEffect> effect) {
        if (!(entity instanceof Player player)) {
            return false;
        }
        MutagenData data = MutagenPlayer.of(player);
        for (MutagenData.Gene gene : data.genes()) {
            Strain strain = Strains.get(gene.strainId());
            if (strain == null) {
                continue;
            }
            Stage stage = gene.stage();
            for (Trait trait : strain.activeTraits(stage)) {
                if (trait.blocksEffect(effect, stage)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Whether any active trait passes the predicate. Runs on either side. */
    private static boolean anyTrait(Player player, java.util.function.BiPredicate<Trait, Stage> test) {
        MutagenData data = MutagenPlayer.of(player);
        if (data == null) {
            return false;
        }
        for (MutagenData.Gene gene : data.genes()) {
            Strain strain = Strains.get(gene.strainId());
            if (strain == null) {
                continue;
            }
            Stage stage = gene.stage();
            for (Trait trait : strain.activeTraits(stage)) {
                if (test.test(trait, stage)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void eachTrait(Player player, java.util.function.BiConsumer<Trait, Stage> action) {
        anyTrait(player, (trait, stage) -> {
            action.accept(trait, stage);
            return false;
        });
    }

    /** Whether the player can climb walls. */
    public static boolean climbs(Player player) {
        return anyTrait(player, Trait::climbs);
    }

    /** Whether the player can pass through cobwebs. */
    public static boolean walksThroughWebs(Player player) {
        return anyTrait(player, Trait::walksThroughWebs);
    }

    /** Whether the player's body refuses to drop this item. */
    public static boolean hoards(Player player, net.minecraft.world.item.ItemStack stack) {
        return anyTrait(player, (trait, stage) -> trait.hoards(stack, stage));
    }

    /**
     * Direct melee contact with a hand, claw, or stinger. Arrows and thorns do not count. Apply attacker
     * traits to the target and target traits to the attacker.
     */
    public static void onDamaged(LivingEntity target, DamageSource source) {
        if (target.level().isClientSide || source.is(net.minecraft.world.damagesource.DamageTypes.THORNS)) {
            return;
        }
        net.minecraft.world.entity.Entity direct = source.getDirectEntity();
        if (direct == null || direct != source.getEntity() || direct == target) {
            return;
        }
        if (direct instanceof ServerPlayer attacker) {
            eachTrait(attacker, (trait, stage) -> trait.onMeleeHit(attacker, target, stage));
        }
        if (target instanceof ServerPlayer victim && direct instanceof LivingEntity attacker) {
            eachTrait(victim, (trait, stage) -> trait.onMeleeHurt(victim, attacker, stage));
        }
    }

    /** Arrow damage multiplier for the player. */
    public static double arrowFactor(Player player) {
        MutagenData data = MutagenPlayer.of(player);
        double factor = 1.0D;
        for (MutagenData.Gene gene : data.genes()) {
            Strain strain = Strains.get(gene.strainId());
            if (strain == null) {
                continue;
            }
            for (Trait trait : strain.activeTraits(gene.stage())) {
                if (trait instanceof Traits.ArrowPower power) {
                    factor *= power.factor();
                }
            }
        }
        return factor;
    }

    private record DesiredModifier(Holder<Attribute> attribute, double amount,
                                   AttributeModifier.Operation operation) {
        boolean matches(AttributeModifier modifier) {
            return modifier.operation() == operation && Math.abs(modifier.amount() - amount) < 0.0001D;
        }
    }
}
