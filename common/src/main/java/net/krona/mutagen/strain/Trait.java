package net.krona.mutagen.strain;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/**
 * Черта штамма: то, что штамм даёт или отнимает у носителя.
 * <p>
 * С 0.2 черты читаются из датапака: у каждой есть {@link TraitType}, который знает её JSON-формат.
 */
public interface Trait {
    /** Тип черты — по нему черта пишется в JSON и узнаётся при чтении. */
    TraitType<?> type();

    /** Стадия, начиная с которой черта работает. */
    Stage minStage();

    /** Короткое описание для подсказок и гено-древа. */
    Component description();

    /** Слабость или сила. Гено-древо красит слабости красным, силы — зелёным. */
    default boolean weakness() {
        return false;
    }

    /** Раз в секунду на сервере, пока стадия не ниже {@link #minStage()}. */
    default void tick(ServerPlayer player, Stage stage) {
    }

    /** Постоянные модификаторы атрибутов. Пересобираются при каждой смене стадии. */
    default void attributes(Stage stage, AttributeSink sink) {
    }

    /** Множитель входящего урона: слабости и иммунитеты. */
    default float modifyIncomingDamage(DamageSource source, float amount, Stage stage) {
        return amount;
    }

    /** Иммунитет к эффектам. */
    default boolean blocksEffect(Holder<MobEffect> effect, Stage stage) {
        return false;
    }

    @FunctionalInterface
    interface AttributeSink {
        void add(Holder<Attribute> attribute, double amount, AttributeModifier.Operation operation);
    }
}
