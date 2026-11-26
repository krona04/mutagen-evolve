package net.krona.mutagen.ability;

import net.krona.mutagen.strain.Stage;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Активная способность штамма: то, ради чего в него вообще лезут.
 * <p>
 * Правило мода — сила и её цена приходят вместе. У способности цена двойная: разовая
 * ({@link #cost()} в голоде и здоровье) и постоянная — перезарядка, на время которой
 * носитель остаётся с одними лишь ограничениями своего тела.
 * <p>
 * Апекс ({@link #apex()}) — единственная способность, которой мало полной формы: нужен ещё
 * полностью прочитанный геном вида.
 */
public interface Ability {
    default AbilityRequirement requirement() { return AbilityRequirement.ANY; }
    /** Тип способности — по нему она пишется в JSON и узнаётся при чтении. */
    AbilityType<?> type();

    /** Стадия, начиная с которой способность доступна. */
    Stage minStage();

    /** Название для интерфейса. */
    Component name();

    /** Перезарядка в тиках. */
    int cooldown();

    /** Цена одного применения. */
    Cost cost();

    /** Апекс: требует полной формы и стопроцентной изученности вида. */
    boolean apex();

    /**
     * Применить способность. Цена и перезарядка снимаются снаружи и только при успехе,
     * поэтому неудачная попытка ничего не стоит.
     *
     * @return false, если способность не сработала — например, не нашла цели
     */
    boolean activate(ServerPlayer player, Stage stage);

    /** Цена применения. Голод тратится первым, здоровье — только если голода не хватает. */
    record Cost(float hunger, float health) {
        public static final Cost FREE = new Cost(0.0F, 0.0F);

        public boolean isFree() {
            return hunger <= 0.0F && health <= 0.0F;
        }
    }
}
