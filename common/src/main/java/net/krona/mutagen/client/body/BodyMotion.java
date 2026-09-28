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
 * Тело, которое не до конца слушается хозяина.
 * <p>
 * <b>Судороги.</b> Переход на новую стадию — это ломка: пару секунд тело выкручивает, голова дёргается,
 * руки ходят ходуном, и дрожь затухает. Видят это все, кто смотрит на носителя, а не только он сам.
 * <p>
 * <b>Подёргивания.</b> Со стадии III раз в несколько секунд одна кость коротко и резко дёргается
 * сама по себе — на IV чаще и сильнее. Расписание берётся из UUID игрока, поэтому у каждого носителя
 * свой ритм, одинаковый на всех клиентах.
 */
@Environment(EnvType.CLIENT)
public final class BodyMotion {
    private static final int CONVULSION_TICKS = 50;
    private static final Map<Integer, Long> CONVULSIONS = new HashMap<>();

    private BodyMotion() {
    }

    /** Стадия выросла: запустить судороги у этого игрока. */
    public static void startConvulsion(Player player) {
        CONVULSIONS.put(player.getId(), gameTime());
    }

    public static void apply(PlayerModel<?> model, Player player, MutagenData.Gene gene) {
        if (!MutagenConfig.get().bodyMotion) {
            return;
        }
        float time = player.tickCount + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
        convulse(model, player, time);
        twitch(model, player, gene.stage());
    }

    private static void convulse(PlayerModel<?> model, Player player, float time) {
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
        strength *= strength;
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
     * Одно подёргивание за окно в {@code period} тиков: момент и кость выбираются хешем от UUID и номера окна.
     */
    private static void twitch(PlayerModel<?> model, Player player, Stage stage) {
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
        // Резкий рывок и спад: синус по полупериоду.
        float amount = Mth.sin((inside / (float) length) * Mth.PI) * (strong ? 0.30F : 0.18F);
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
