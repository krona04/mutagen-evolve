package net.krona.mutagen.knowledge;

import net.krona.mutagen.MutagenConfig;
import net.krona.mutagen.strain.Strain;
import net.minecraft.network.chat.Component;

/**
 * Species research thresholds and their unlocks from the design document.
 */
public final class Research {
    /** Reveal the gene list and unlock weak serum. */
    public static final float GENES = 25.0F;
    /** Unlock stronger serum. */
    public static final float SERUM = 50.0F;
    /** Unlock serum that reaches full form. */
    public static final float FULL_FORM = 75.0F;
    /** The strain holds without a stabilizer. */
    public static final float MASTERED = 100.0F;

    public static final float[] THRESHOLDS = {GENES, SERUM, FULL_FORM, MASTERED};

    private Research() {
    }

    /**
     * Maximum progress for serum made at this research level. Zero means synthesis is unavailable.
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
     * Research percentage gained per sequencer run. Purer samples give more and complex species give less.
     * Creepers take 1.5 times as long to research as zombies.
     */
    public static float gain(Strain strain, float quality) {
        float base = 4.0F + quality * 0.12F;
        float difficulty = 1.0F + 0.25F * (strain.difficulty() - 1);
        return base / difficulty * MutagenConfig.get().researchSpeed;
    }

    /** Next research threshold, or -1 when all are unlocked. */
    public static float nextThreshold(float research) {
        for (float threshold : THRESHOLDS) {
            if (research < threshold) {
                return threshold;
            }
        }
        return -1.0F;
    }

    /** Threshold unlock text for the sequencer, gene tree, and messages. */
    public static Component unlockText(float threshold) {
        return Component.translatable("mutagen.research.unlock." + (int) threshold);
    }

    /** Whether research crossed a threshold between {@code before} and {@code after}. */
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
