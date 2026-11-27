package net.krona.mutagen.item;

import net.krona.mutagen.Mutagen;
import net.krona.mutagen.data.GeneData;
import net.krona.mutagen.knowledge.MutagenKnowledge;
import net.krona.mutagen.registry.MutagenItems;
import net.krona.mutagen.strain.Strain;
import net.krona.mutagen.strain.Strains;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Right-click a mob to collect a tissue sample. Sampling angers the mob and cannot be repeated immediately.
 * <p>
 * Iron syringes are single-use. Gold and netherite syringes last longer and add 10 and 25 purity points,
 * respectively.
 */
public class SyringeItem extends Item {
    /** Tag mobs from spawners when they spawn. See {@code MobMixin}. */
    public static final String SPAWNER_TAG = Mutagen.MOD_ID + ".spawner";

    /** Recently sampled marker with a ten-minute in-game cooldown. */
    private static final long COOLDOWN_TICKS = 12000L;
    private static final Map<UUID, Long> RECENT_SAMPLES = new HashMap<>();

    private final float qualityBonus;
    private final boolean reusable;

    public SyringeItem(Properties properties, float qualityBonus, boolean reusable) {
        super(properties);
        this.qualityBonus = qualityBonus;
        this.reusable = reusable;
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
                                                  InteractionHand hand) {
        if (player.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (target instanceof Player) {
            player.displayClientMessage(Component.translatable("mutagen.message.no_player_samples")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }

        Strain strain = Strains.byEntity(target.getType());
        if (strain == null) {
            player.displayClientMessage(Component.translatable("mutagen.message.unknown_species",
                    target.getType().getDescription()).withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }

        long time = player.level().getGameTime();
        Long last = RECENT_SAMPLES.get(target.getUUID());
        if (last != null && time - last < COOLDOWN_TICKS) {
            player.displayClientMessage(Component.translatable("mutagen.message.sample_exhausted")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }
        if (RECENT_SAMPLES.size() > 512) {
            RECENT_SAMPLES.entrySet().removeIf(entry -> time - entry.getValue() > COOLDOWN_TICKS);
        }
        RECENT_SAMPLES.put(target.getUUID(), time);

        float quality = quality(target, qualityBonus);
        ItemStack sample = GeneItem.withGene(new ItemStack(MutagenItems.SAMPLE.get()),
                new GeneData(strain.id(), quality));

        if (reusable) {
            stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
        } else {
            stack.shrink(1);
        }
        if (!player.getInventory().add(sample)) {
            player.drop(sample, false);
        }

        target.hurt(player.damageSources().playerAttack(player), 1.0F);
        if (target instanceof Mob mob) {
            mob.setTarget(player);
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.BOTTLE_FILL,
                SoundSource.PLAYERS, 0.8F, 1.4F);
        player.displayClientMessage(Component.translatable("mutagen.message.sample_taken",
                strain.displayName(), String.format("%.0f", quality)), true);

        if (player instanceof ServerPlayer serverPlayer) {
            discover(serverPlayer, strain);
        }
        return InteractionResult.CONSUME;
    }

    /** The first sample of a species adds it to the gene tree. */
    private static void discover(ServerPlayer player, Strain strain) {
        MutagenKnowledge knowledge = MutagenKnowledge.get(player.server);
        if (knowledge.discover(player.getUUID(), strain.id())) {
            knowledge.broadcast(player.server, player.getUUID(), strain.id());
            player.sendSystemMessage(Component.translatable("mutagen.message.species_discovered",
                    strain.displayName()).withStyle(ChatFormatting.AQUA));
        }
    }

    /**
     * Sample quality improves if the creature is weakened or restrained. Babies yield almost nothing and
     * spawner mobs yield lower-quality samples.
     */
    public static float quality(LivingEntity target, float bonus) {
        float quality = 50.0F + bonus;
        if (target.getHealth() < target.getMaxHealth() * 0.3F) {
            quality += 15.0F;
        }
        if (restrained(target)) {
            quality += 10.0F;
        }
        if (target.isBaby()) {
            quality -= 20.0F;
        } else {
            quality += 10.0F;
        }
        if (target.getTags().contains(SPAWNER_TAG)) {
            quality -= 30.0F;
        }
        return Math.max(5.0F, Math.min(100.0F, quality));
    }

    /** Treat sleeping, riding a boat or mount, being leashed, or being heavily slowed as restrained. */
    private static boolean restrained(LivingEntity target) {
        if (target.isSleeping() || target.isPassenger() || (target instanceof Mob mob && mob.isLeashed())) {
            return true;
        }
        MobEffectInstance slowness = target.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        return slowness != null && slowness.getAmplifier() >= 2;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (qualityBonus > 0.0F) {
            tooltip.add(Component.translatable("mutagen.tooltip.syringe_bonus", String.format("%.0f", qualityBonus))
                    .withStyle(ChatFormatting.GREEN));
        }
    }
}
