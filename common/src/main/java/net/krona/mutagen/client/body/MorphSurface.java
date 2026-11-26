package net.krona.mutagen.client.body;

/** Shared surface ownership: never collapse two visible skins onto the same depth. */
public final class MorphSurface {
    public static final float REPLACED = 0.98F;
    private MorphSurface() {}

    public static boolean replaced(float phase) { return phase >= REPLACED; }

    public static float clearance(float phase) {
        // The player surface is hidden before clearance starts approaching zero.
        return 1.03F - 0.03F * MorphSequence.window(phase, REPLACED, 1);
    }

    public static boolean vanillaSpiderLeg(float progress) {
        return net.krona.mutagen.strain.Infection.overall(progress) >= 0.98F;
    }
}
