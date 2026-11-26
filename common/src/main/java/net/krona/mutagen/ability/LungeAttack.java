package net.krona.mutagen.ability;

import net.krona.mutagen.data.MutagenPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import java.util.*;

/** The launch spends the ability; damage occurs once at contact, and a dodged charge can miss. */
public final class LungeAttack {
    private record Pending(UUID target,ResourceLocation strain,long deadline,float damage) {}
    private static final Map<MinecraftServer,Map<UUID,Pending>> CASTS=new WeakHashMap<>();
    private LungeAttack() {}
    public static void start(ServerPlayer player,LivingEntity target,float damage) {
        var gene=MutagenPlayer.of(player).primary();
        if(gene==null) return;
        CASTS.computeIfAbsent(player.server,key->new HashMap<>()).put(player.getUUID(),
                new Pending(target.getUUID(),gene.strainId(),player.level().getGameTime()+20,damage));
    }
    public static void tick(ServerPlayer player) {
        var casts=CASTS.get(player.server);
        if(casts==null) return;
        long now=player.level().getGameTime();
        casts.values().removeIf(cast->cast.deadline<now);
        var cast=casts.get(player.getUUID());
        if(cast==null) return;
        var gene=MutagenPlayer.of(player).primary();
        var entity=player.serverLevel().getEntity(cast.target);
        if(!player.isAlive()||gene==null||!gene.strainId().equals(cast.strain)||!(entity instanceof LivingEntity target)||!target.isAlive()) {
            casts.remove(player.getUUID());return;
        }
        double contact=Math.min(3,1+(player.getBbWidth()+target.getBbWidth())*0.5);
        if(player.getBoundingBox().inflate(0.3).intersects(target.getBoundingBox())
                && AbilityTargets.valid(player,target,contact)) {
            casts.remove(player.getUUID());
            target.hurt(player.damageSources().playerAttack(player),cast.damage);
        }
    }
}
