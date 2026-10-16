package net.krona.mutagen.strain;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/**
 * Strain trait: something the strain gives to or takes away from the host.
 * <p>
 * Since 0.2 traits are read from the datapack: each one has a {@link TraitType} that knows its JSON format.
 */
public interface Trait {
    /** Trait type — the trait is written to JSON and recognized on reading by it. */
    TraitType<?> type();

    /** The stage from which the trait takes effect. */
    Stage minStage();

    /** Short description for tooltips and the gene tree. */
    Component description();

    /** Weakness or strength. The gene tree paints weaknesses red, strengths green. */
    default boolean weakness() {
        return false;
    }

    /** Once per second on the server, while the stage is not below {@link #minStage()}. */
    default void tick(ServerPlayer player, Stage stage) {
    }

    /** Permanent attribute modifiers. Rebuilt on every stage change. */
    default void attributes(Stage stage, AttributeSink sink) {
    }

    /** Incoming damage multiplier: weaknesses and immunities. */
    default float modifyIncomingDamage(DamageSource source, float amount, Stage stage) {
        return amount;
    }

    /** Effect immunity. */
    default boolean blocksEffect(Holder<MobEffect> effect, Stage stage) {
        return false;
    }

    @FunctionalInterface
    interface AttributeSink {
        void add(Holder<Attribute> attribute, double amount, AttributeModifier.Operation operation);
    }
}
