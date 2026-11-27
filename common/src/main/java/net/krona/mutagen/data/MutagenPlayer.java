package net.krona.mutagen.data;

import net.minecraft.world.entity.player.Player;

/**
 * Access player mutation state through a {@link Player} mixin shared by Fabric and NeoForge.
 */
public interface MutagenPlayer {
    MutagenData mutagen$getData();

    static MutagenData of(Player player) {
        return ((MutagenPlayer) player).mutagen$getData();
    }
}
