package net.krona.mutagen.ability;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;

public final class AbilityTargets {
    private AbilityTargets() {}
    public static boolean valid(ServerPlayer player,LivingEntity target,double radius) {
        return target!=player&&eligible(target.isAlive(),target.isSpectator(),
                target instanceof Player other&&other.isCreative(),player.isAlliedTo(target)||target.isAlliedTo(player)
                        || target instanceof Player other&&(!player.server.isPvpAllowed()||!player.canHarmPlayer(other)),
                target instanceof TamableAnimal pet&&pet.isOwnedBy(player),player.distanceToSqr(target),radius,
                player.hasLineOfSight(target));
    }
    public static boolean eligible(boolean alive,boolean spectator,boolean creative,boolean allied,boolean owned,
                                    double distanceSquared,double radius,boolean visible) {
        return alive&&!spectator&&!creative&&!allied&&!owned&&visible&&distanceSquared<=radius*radius;
    }
}
