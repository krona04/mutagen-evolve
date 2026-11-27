package net.krona.mutagen.ability;

import net.krona.mutagen.strain.Stage;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * An active ability granted by a strain.
 * <p>
 * Power comes with a cost. Using an ability spends hunger or health ({@link #cost()}) and starts a
 * cooldown, during which the strain's restrictions still apply.
 * <p>
 * The apex ability ({@link #apex()}) requires both full form and complete research of the species.
 */
public interface Ability {
    default AbilityRequirement requirement() { return AbilityRequirement.ANY; }
    /** Ability type used to encode and decode JSON. */
    AbilityType<?> type();

    /** Earliest stage at which the ability is available. */
    Stage minStage();

    /** Name shown in the interface. */
    Component name();

    /** Cooldown in ticks. */
    int cooldown();

    /** Cost per use. */
    Cost cost();

    /** Apex abilities require full form and 100% research of the species. */
    boolean apex();

    /**
     * Try to activate the ability. The caller spends resources and starts the cooldown only on success.
     *
     * @return false if the ability did not activate, for example if it found no target
     */
    boolean activate(ServerPlayer player, Stage stage);

    /** Cost of using the ability. Hunger is spent first and health is used only when hunger is insufficient. */
    record Cost(float hunger, float health) {
        public static final Cost FREE = new Cost(0.0F, 0.0F);

        public boolean isFree() {
            return hunger <= 0.0F && health <= 0.0F;
        }
    }
}
