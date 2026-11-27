package net.krona.mutagen.body;

import net.krona.mutagen.strain.BodyShape;
import net.krona.mutagen.strain.Trait;
import net.minecraft.server.level.ServerPlayer;

/**
 * Apply the player's body shape through the vanilla scale attribute or Pehkui.
 * <p>
 * Calls to Pehkui go through this adapter so Pehkui can remain an optional dependency.
 */
public interface BodyScaler {
    /** Name shown in logs and {@code /mutagen status}. */
    String name();

    /**
     * Apply {@code shape} once per second on the server. Send any required attribute modifiers to {@code
     * sink} so the shared code can apply and remove them.
     */
    void apply(ServerPlayer player, BodyShape shape, Trait.AttributeSink sink);

    /** The player dimensions mixin calculates the hitbox and eye height. */
    boolean ownsDimensions();
}
