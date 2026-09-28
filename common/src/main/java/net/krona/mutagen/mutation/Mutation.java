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
 * Вся серверная логика трансформации: усвоение дозы, откат, стадии и цена за них.
 * <p>
 * Правило скоростей взято из дизайн-документа: доза не даёт прогресс сразу, она кладётся
 * в запас усвоения, а тело перерабатывает его медленно. Без поддержки прогресс откатывается.
 */
public final class Mutation {
    /** Сколько процентов прогресса тело усваивает за секунду. 1% за 30 секунд. */
    private static final float ABSORB_PER_SECOND = 1.0F / 30.0F;
    /** Откат без поддержки: 1% за 8 игровых минут. */
    private static final float DECAY_PER_SECOND = 1.0F / (8.0F * 60.0F);

    private Mutation() {
    }

    /**
     * Ввести дозу: прогресс не прыгает, в тело кладётся запас усвоения.
     * <p>
     * {@code limit} — потолок сыворотки из изученности вида. Запас кладётся только до него:
     * слабая сыворотка не поднимет прогресс выше 40%, сколько её ни вводи.
     */
    public static boolean dose(ServerPlayer player, ResourceLocation strainId, float quality, int limit) {
        Strain strain = Strains.get(strainId);
        if (strain == null) {
            return false;
        }
        MutagenData data = MutagenPlayer.of(player);
        MutagenData.Gene existing = data.gene(strainId);
        if (existing == null && data.genes().size() >= MutagenConfig.get().maxGenes) {
            MutagenData.Gene occupied = data.primary();
            Strain held = occupied == null ? null : Strains.get(occupied.strainId());
            Component heldName = held == null ? Component.literal("?") : held.displayName();
            // В чат, а не в строку над хотбаром: отказ легко не заметить, а он объясняет, что делать.
            player.sendSystemMessage(Component.translatable("mutagen.message.too_many_genes", heldName)
                    .withStyle(net.minecraft.ChatFormatting.RED));
            return false;
        }
        float reached = existing == null ? 0.0F : existing.progress() + existing.pending();
        float room = limit - reached;
        if (room <= 0.01F) {
            // Отказ до укола: ампула остаётся у игрока, а сообщение говорит, чего не хватает.
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

    /** Антимутаген: болезненный откат прогресса. */
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

    /** Вызывается каждый тик; тяжёлая часть работает раз в секунду. */
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

        // Сеть сама решает, достаточно ли изменилось состояние, чтобы его отправлять.
        MutagenNetwork.syncPlayer(player, data.isDirty());
        data.clearDirty();
    }

    private static void advance(ServerPlayer player, MutagenData data) {
        float speed = MutagenConfig.get().transformationSpeed;
        List<MutagenData.Gene> finished = new ArrayList<>();
        MutagenKnowledge knowledge = MutagenKnowledge.get(player.server);

        for (MutagenData.Gene gene : data.genes()) {
            Stage before = gene.stage();

            if (gene.pending() > 0.0F) {
                float step = Math.min(gene.pending(), ABSORB_PER_SECOND * speed);
                gene.setPending(gene.pending() - step);
                gene.setProgress(gene.progress() + step);
            } else if (!gene.locked() && gene.progress() > 0.0F && !gene.stage().atLeast(Stage.FULL)
                    && !Research.mastered(knowledge.research(player.getUUID(), gene.strainId()))) {
                // Полная форма не откатывается сама: человеческой природы, которая тянула бы назад,
                // в теле уже не осталось. Обратно — только антимутагеном или полным сбросом.
                // Полностью изученный вид тоже держится сам: тело знает этот геном целиком.
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

        // Ломка: переход между стадиями всегда ощущается телом.
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
     * Пересобирает модификаторы атрибутов под текущие стадии.
     * Идемпотентно: выполняется каждую секунду и переживает смерть и перезаход.
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

        // Тело (0.3): рост модели, хитбокс, глаза и дальность рук идут плавно вместе с прогрессом.
        // Через что именно — ванильные атрибуты или Pehkui — решает прослойка.
        BodyShape shape = BodyShape.of(data.primary());
        Bodies.scaler().apply(player, shape, (attribute, amount, operation) -> {
            ResourceLocation id = Mutagen.id("body/" + attribute.unwrapKey()
                    .map(key -> key.location().getPath()).orElse("unknown"));
            desired.put(id, new DesiredModifier(attribute, amount, operation));
        });
        applyModifiers(player, desired);
        refreshDimensions(player);
    }

    /** Пересчитывает хитбокс, если форма тела его поменяла. Дёшево, когда ничего не изменилось. */
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

    /** Слабости и иммунитеты к урону. */
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

    /** Иммунитет к эффектам. */
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

    /** Множитель урона стрел носителя. */
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
