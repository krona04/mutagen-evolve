package net.krona.mutagen.strain;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;

/**
 * A strain trait that grants a benefit or imposes a restriction.
 * <p>
 * Since 0.2, traits come from datapacks. Each {@link TraitType} defines its JSON schema.
 */
public interface Trait {
    /** Trait type used for JSON encoding and decoding. */
    TraitType<?> type();

    /** Earliest stage at which the trait is active. */
    Stage minStage();

    /** Short description for tooltips and the gene tree. */
    Component description();

    /** The gene tree shows weaknesses in red and strengths in green. */
    default boolean weakness() {
        return false;
    }

    /** Runs once per second on the server at or above {@link #minStage()}. */
    default void tick(ServerPlayer player, Stage stage) {
    }

    /** Persistent attribute modifiers rebuilt when the stage changes. */
    default void attributes(Stage stage, AttributeSink sink) {
    }

    /** Incoming damage multiplier for weaknesses and immunities. */
    default float modifyIncomingDamage(DamageSource source, float amount, Stage stage) {
        return amount;
    }

    /** Whether the species recognizes this player and becomes non-hostile. */
    default boolean pacifies(net.minecraft.world.entity.EntityType<?> kind, Stage stage) {
        return false;
    }

    /** Status-effect immunity. */
    default boolean blocksEffect(Holder<MobEffect> effect, Stage stage) {
        return false;
    }

    /** Whether the player can climb walls. Check both sides because the client handles movement. */
    default boolean climbs(Stage stage) {
        return false;
    }

    /** Whether the player can move through cobwebs. Check both sides. */
    default boolean walksThroughWebs(Stage stage) {
        return false;
    }

    /** Prevent dropping this item from the inventory. */
    default boolean hoards(ItemStack stack, Stage stage) {
        return false;
    }

    /** The player hit a target in melee. Server only. */
    default void onMeleeHit(ServerPlayer player, LivingEntity target, Stage stage) {
    }

    /** The player was hit in melee. Server only. */
    default void onMeleeHurt(ServerPlayer player, LivingEntity attacker, Stage stage) {
    }

    @FunctionalInterface
    interface AttributeSink {
        void add(Holder<Attribute> attribute, double amount, AttributeModifier.Operation operation);
    }
}
