package net.krona.mutagen.data;

import net.minecraft.world.entity.player.Player;

/**
 * Access to a player's mutation state. Implemented by a mixin into {@link Player}, so it works
 * the same on Fabric and NeoForge without platform capabilities.
 */
public interface MutagenPlayer {
    MutagenData mutagen$getData();

    static MutagenData of(Player player) {
        return ((MutagenPlayer) player).mutagen$getData();
    }
}
