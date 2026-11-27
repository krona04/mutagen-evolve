package net.krona.mutagen.client.body;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.krona.mutagen.MutagenConfig;
import net.krona.mutagen.data.MutagenData;
import net.krona.mutagen.strain.Stage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * Involuntary body motion.
 * <p>
 * <b>Convulsions.</b> On a stage transition, animate twisting, head jerks, and arm movement for a couple of
 * seconds before the tremor subsides. Nearby players see it too.
 * <p>
 * <b>Twitches.</b> Starting at stage III, a single bone jerks every few seconds. Twitches grow more
 * frequent and stronger at stage IV. Derive their schedule from the player's UUID so all clients see the
 * same rhythm.
 */
@Environment(EnvType.CLIENT)
public final class BodyMotion {
    private static final int CONVULSION_TICKS = 50;
    private static final Map<Integer, Long> CONVULSIONS = new HashMap<>();

    private BodyMotion() {
    }

    /** Start a convulsion for this player when the stage increases. */
    public static void startConvulsion(Player player) {
        CONVULSIONS.put(player.getId(), gameTime());
    }

    public static void apply(PlayerModel<?> model, Player player, MutagenData.Gene gene) {
        if (!MutagenConfig.get().bodyMotion) {
            return;
        }
        float time = player.tickCount + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
        var strain = net.krona.mutagen.strain.Strains.get(gene.strainId());
        float weight = strain != null && MutagenConfig.get().bodyMorph
                ? 1.0F - SpiderMorph.window(SpiderMorph.phase(gene.progress()), 0.60F, 0.95F) : 1.0F;
        if (strain != null && MutagenConfig.get().bodyMorph && CompactMorph.supports(strain))
            weight *= 0.15F * (1.0F - CompactMorph.prepare(gene.progress()));
        convulse(model, player, time, weight);
        twitch(model, player, gene.stage(), weight);
    }

    private static void convulse(PlayerModel<?> model, Player player, float time, float weight) {
        Long start = CONVULSIONS.get(player.getId());
        if (start == null) {
            return;
        }
        long age = gameTime() - start;
        if (age < 0 || age > CONVULSION_TICKS) {
            CONVULSIONS.remove(player.getId());
            return;
        }
        float strength = 1.0F - age / (float) CONVULSION_TICKS;
        strength *= strength * weight;
        model.head.xRot += Mth.sin(time * 2.9F) * 0.22F * strength;
        model.head.yRot += Mth.sin(time * 2.1F + 1.3F) * 0.18F * strength;
        model.head.zRot += Mth.sin(time * 3.7F) * 0.12F * strength;
        model.body.zRot += Mth.sin(time * 1.7F) * 0.05F * strength;
        model.rightArm.xRot += Mth.sin(time * 3.3F + 0.7F) * 0.45F * strength;
        model.rightArm.zRot += Mth.sin(time * 4.1F) * 0.15F * strength;
        model.leftArm.xRot += Mth.sin(time * 3.1F + 2.1F) * 0.45F * strength;
        model.leftArm.zRot -= Mth.sin(time * 3.9F + 0.4F) * 0.15F * strength;
        model.rightLeg.xRot += Mth.sin(time * 2.3F) * 0.20F * strength;
        model.leftLeg.xRot -= Mth.sin(time * 2.3F) * 0.20F * strength;
    }

    /**
     * Schedule one twitch per window of {@code period} ticks. Hash the player's UUID and the window number
     * to choose the bone and timing.
     */
    private static void twitch(PlayerModel<?> model, Player player, Stage stage, float weight) {
        if (!stage.atLeast(Stage.MUTATION)) {
            return;
        }
        boolean strong = stage.atLeast(Stage.DOMINATION);
        int period = strong ? 110 : 200;
        int length = 7;
        long ticks = player.tickCount;
        long window = ticks / period;
        long hash = mix(player.getUUID().getLeastSignificantBits() ^ window * 0x9E3779B97F4A7C15L);
        int offset = (int) Math.floorMod(hash, period - length);
        int inside = (int) (ticks % period) - offset;
        if (inside < 0 || inside >= length) {
            return;
        }
        // Use a half-cycle sine wave for a sharp twitch followed by a falloff.
        float amount = Mth.sin((inside / (float) length) * Mth.PI) * (strong ? 0.30F : 0.18F) * weight;
        float sign = (hash & 1L) == 0 ? 1.0F : -1.0F;
        switch ((int) Math.floorMod(hash >>> 8, 3)) {
            case 0 -> model.head.xRot += amount * 0.8F;
            case 1 -> {
                model.rightArm.xRot -= amount * 1.4F;
                model.rightArm.zRot += amount * 0.4F * sign;
            }
            default -> {
                model.leftArm.xRot -= amount * 1.4F;
                model.leftArm.zRot -= amount * 0.4F * sign;
            }
        }
    }

    private static long mix(long h) {
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        return h;
    }

    private static long gameTime() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? 0L : minecraft.level.getGameTime();
    }
}
