package net.krona.mutagen.knowledge;

import net.krona.mutagen.MutagenConfig;
import net.krona.mutagen.strain.Strain;
import net.minecraft.network.chat.Component;

/**
 * Species research thresholds and what each of them unlocks. The numbers come from the design document.
 */
public final class Research {
    /** The gene list is visible, a weak serum is available. */
    public static final float GENES = 25.0F;
    /** Full serum. */
    public static final float SERUM = 50.0F;
    /** The serum leads to the full form. */
    public static final float FULL_FORM = 75.0F;
    /** The strain holds without a stabilizer. */
    public static final float MASTERED = 100.0F;

    public static final float[] THRESHOLDS = {GENES, SERUM, FULL_FORM, MASTERED};

    private Research() {
    }

    /**
     * The progress a serum synthesized at this research level can bring the host to.
     * Zero — synthesis is not possible yet.
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
     * How many percent of research one sequencer run yields. A pure sample yields more,
     * a complex species less: a creeper takes one and a half times longer to research than a zombie.
     */
    public static float gain(Strain strain, float quality) {
        float base = 4.0F + quality * 0.12F;
        float difficulty = 1.0F + 0.25F * (strain.difficulty() - 1);
        return base / difficulty * MutagenConfig.get().researchSpeed;
    }

    /** The next threshold after the current research, or -1 if everything is unlocked. */
    public static float nextThreshold(float research) {
        for (float threshold : THRESHOLDS) {
            if (research < threshold) {
                return threshold;
            }
        }
        return -1.0F;
    }

    /** What a threshold unlocks — for the sequencer, the gene tree and messages. */
    public static Component unlockText(float threshold) {
        return Component.translatable("mutagen.research.unlock." + (int) threshold);
    }

    /** Whether a threshold is crossed when research grows from {@code before} to {@code after}. */
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
