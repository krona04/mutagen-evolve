package net.krona.mutagen.knowledge;

import net.krona.mutagen.MutagenConfig;
import net.krona.mutagen.strain.Strain;
import net.minecraft.network.chat.Component;

/**
 * Пороги изученности вида и то, что каждый из них открывает. Числа взяты из дизайн-документа.
 */
public final class Research {
    /** Виден список генов, доступна слабая сыворотка. */
    public static final float GENES = 25.0F;
    /** Полная сыворотка. */
    public static final float SERUM = 50.0F;
    /** Сыворотка ведёт до полной формы. */
    public static final float FULL_FORM = 75.0F;
    /** Штамм держится без стабилизатора. */
    public static final float MASTERED = 100.0F;

    public static final float[] THRESHOLDS = {GENES, SERUM, FULL_FORM, MASTERED};

    private Research() {
    }

    /**
     * До какого прогресса способна довести сыворотка, синтезированная при такой изученности.
     * Ноль — синтез ещё невозможен.
     */
    public static int serumLimit(float research) {
        if (research >= FULL_FORM) {
            return 100;
        }
        if (research >= SERUM) {
            return 70;
        }
        if (research >= GENES) {
            return 40;
        }
        return 0;
    }

    public static boolean genesVisible(float research) {
        return research >= GENES;
    }

    public static boolean mastered(float research) {
        return research >= MASTERED;
    }

    /**
     * Сколько процентов изученности даёт один прогон секвенатора. Чистый образец даёт больше,
     * сложный вид — меньше: крипер изучается в полтора раза дольше зомби.
     */
    public static float gain(Strain strain, float quality) {
        float base = 4.0F + quality * 0.12F;
        float difficulty = 1.0F + 0.25F * (strain.difficulty() - 1);
        return base / difficulty * MutagenConfig.get().researchSpeed;
    }

    /** Следующий порог после текущей изученности или -1, если всё открыто. */
    public static float nextThreshold(float research) {
        for (float threshold : THRESHOLDS) {
            if (research < threshold) {
                return threshold;
            }
        }
        return -1.0F;
    }

    /** Что открывает порог — для секвенатора, гено-древа и сообщений. */
    public static Component unlockText(float threshold) {
        return Component.translatable("mutagen.research.unlock." + (int) threshold);
    }

    /** Пересечён ли порог при росте изученности с {@code before} до {@code after}. */
    public static float crossed(float before, float after) {
        float result = -1.0F;
        for (float threshold : THRESHOLDS) {
            if (before < threshold && after >= threshold) {
                result = threshold;
            }
        }
        return result;
    }
}
