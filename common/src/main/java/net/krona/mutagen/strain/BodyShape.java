package net.krona.mutagen.strain;

import net.krona.mutagen.data.MutagenData;
import net.krona.mutagen.data.MutagenPlayer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Габариты тела носителя в данный момент: хитбокс, высота глаз, прибавка к дальности рук и масштаб модели.
 * <p>
 * Всё идёт по прогрессу основного штамма: на 0% — человек, на 100% — существо. Считается одинаково
 * на сервере и на клиенте, потому что оба знают штамм и прогресс.
 */
public record BodyShape(float width, float height, float eyeHeight, float reach, float renderScale) {
    public static final BodyShape HUMAN = new BodyShape(0.6F, 1.8F, 1.62F, 0.0F, 1.0F);

    /** Базовая дальность взаимодействия игрока: нужна прослойке Pehkui, где дальность — множитель. */
    public static final float BASE_BLOCK_REACH = 4.5F;
    public static final float BASE_ENTITY_REACH = 3.0F;

    public static BodyShape of(Player player) {
        MutagenData data = MutagenPlayer.of(player);
        return data == null ? HUMAN : of(data.primary());
    }

    public static BodyShape of(@Nullable MutagenData.Gene gene) {
        if (gene == null) {
            return HUMAN;
        }
        Strain strain = Strains.get(gene.strainId());
        if (strain == null) {
            return HUMAN;
        }
        float t = Math.max(0.0F, Math.min(1.0F, gene.progress() / 100.0F));
        return new BodyShape(
                lerp(HUMAN.width, strain.targetWidth(), t),
                lerp(HUMAN.height, strain.targetHeight(), t),
                lerp(HUMAN.eyeHeight, strain.targetEyeHeight(), t),
                strain.body().reach() * t,
                lerp(1.0F, strain.targetScale(), t));
    }

    public boolean isHuman() {
        return Math.abs(width - HUMAN.width) < 1.0E-4F && Math.abs(height - HUMAN.height) < 1.0E-4F
                && Math.abs(eyeHeight - HUMAN.eyeHeight) < 1.0E-4F && Math.abs(reach) < 1.0E-4F
                && Math.abs(renderScale - 1.0F) < 1.0E-4F;
    }

    public float widthFactor() {
        return width / HUMAN.width;
    }

    public float heightFactor() {
        return height / HUMAN.height;
    }

    public float eyeFactor() {
        return eyeHeight / HUMAN.eyeHeight;
    }

    private static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }
}
