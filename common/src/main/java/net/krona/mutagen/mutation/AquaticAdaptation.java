package net.krona.mutagen.mutation;

import net.krona.mutagen.data.MutagenPlayer;
import net.krona.mutagen.strain.Strains;
import net.krona.mutagen.strain.TraitType;
import net.krona.mutagen.strain.Traits;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

/** Shared water/land physiology. Vanilla's air data also drives the ordinary bubble HUD on land. */
public final class AquaticAdaptation {
    private AquaticAdaptation() {}

    private static boolean has(Player player, TraitType<?> type) {
        var data = MutagenPlayer.of(player);
        var gene = data == null ? null : data.primary();
        var strain = gene == null ? null : Strains.get(gene.strainId());
        return strain != null && strain.activeTraits(gene.stage()).stream().anyMatch(trait -> trait.type() == type);
    }
    public static boolean aquatic(Player player) { return has(player, Traits.AQUATIC_BODY); }
    public static boolean dependent(Player player) { return has(player, Traits.WATER_DEPENDENCE); }
    public static boolean wet(Player player) { return player.isInWaterOrBubble(); }
    public static boolean landVision(Player player) {
        return !player.isSpectator() && dependent(player) && !wet(player);
    }

    public record Breath(int air, boolean damage) {}
    public static Breath breathe(int air, int maximum, boolean wet) {
        if (wet) return new Breath(maximum, false);
        int remaining = Math.min(air, maximum) - 1;
        return remaining <= -20 ? new Breath(0, true) : new Breath(remaining, false);
    }

    public static float swimAcceleration(float progress, float movementMultiplier) {
        float weight = Math.max(0, Math.min(1, (progress - 41) / 59));
        return (.035F + .025F * weight) * Math.max(0, Math.min(3, movementMultiplier));
    }

    public static void tick(ServerPlayer player) {
        if (!player.isAlive() || player.isCreative() || player.isSpectator() || !aquatic(player)) return;
        boolean wet = wet(player);
        if (wet) {
            player.setAirSupply(player.getMaxAirSupply());
            if (player.tickCount % 20 == 0) {
                // Keep duration above vanilla's blinking-night-vision threshold while immersed.
                player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 280, 0, true, false, false));
            }
        } else if (dependent(player)) {
            Breath result = breathe(player.getAirSupply(), player.getMaxAirSupply(), false);
            player.setAirSupply(result.air());
            if (result.damage()) player.hurt(player.damageSources().drown(), 2);
        }
    }
}
