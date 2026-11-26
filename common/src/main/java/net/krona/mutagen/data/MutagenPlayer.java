package net.krona.mutagen.data;

import net.minecraft.world.entity.player.Player;

/**
 * Доступ к состоянию мутации игрока. Реализуется миксином в {@link Player}, поэтому работает
 * одинаково на Fabric и NeoForge без платформенных капабилити.
 */
public interface MutagenPlayer {
    MutagenData mutagen$getData();

    static MutagenData of(Player player) {
        return ((MutagenPlayer) player).mutagen$getData();
    }
}
