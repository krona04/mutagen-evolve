package net.krona.mutagen.body;

import net.krona.mutagen.strain.BodyShape;
import net.krona.mutagen.strain.Trait;
import net.minecraft.server.level.ServerPlayer;

/**
 * Прослойка, которая меняет тело носителя: ванильный атрибут масштаба или Pehkui.
 * <p>
 * Мод никогда не зовёт Pehkui напрямую — только через эту прослойку, поэтому Pehkui остаётся
 * рекомендуемой, но не обязательной зависимостью.
 */
public interface BodyScaler {
    /** Имя для логов и {@code /mutagen status}. */
    String name();

    /**
     * Приводит тело игрока к форме {@code shape}. Раз в секунду на сервере.
     * Модификаторы атрибутов, если они нужны, отдаются в {@code sink}: их снимает и ставит общий код.
     */
    void apply(ServerPlayer player, BodyShape shape, Trait.AttributeSink sink);

    /** Хитбокс и высоту глаз считает мод сам (миксин размеров игрока), а не сторонняя библиотека. */
    boolean ownsDimensions();
}
