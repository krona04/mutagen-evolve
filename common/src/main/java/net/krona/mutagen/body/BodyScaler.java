package net.krona.mutagen.body;

import net.krona.mutagen.strain.BodyShape;
import net.krona.mutagen.strain.Trait;
import net.minecraft.server.level.ServerPlayer;

/**
 * Layer that changes the host's body: the vanilla scale attribute or Pehkui.
 * <p>
 * The mod never calls Pehkui directly — only through this layer, so Pehkui remains
 * a recommended but optional dependency.
 */
public interface BodyScaler {
    /** Name for logs and {@code /mutagen status}. */
    String name();

    /**
     * Brings the player's body to the {@code shape}. Once per second on the server.
     * Attribute modifiers, if needed, are handed to {@code sink}: shared code removes and applies them.
     */
    void apply(ServerPlayer player, BodyShape shape, Trait.AttributeSink sink);

    /** The hitbox and eye height are computed by the mod itself (player dimensions mixin), not by a third-party library. */
    boolean ownsDimensions();
}
