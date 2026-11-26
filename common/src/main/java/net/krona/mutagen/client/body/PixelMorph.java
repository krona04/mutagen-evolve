package net.krona.mutagen.client.body;

/** Persistent source-skin texels change individually, independent of atlas resolution or model deformation. */
public final class PixelMorph {
    private PixelMorph() {}

    public static int colour(int human, int creature, InfectionMask mask, float skinU, float skinV, float phase) {
        if (phase <= 0) return human;
        if (phase >= 1) return creature;
        int x = Math.max(0, Math.min(63, (int)Math.floor(skinU * 64)));
        int y = Math.max(0, Math.min(63, (int)Math.floor(skinV * 64)));
        return mask.at(x,y) < phase ? creature : human;
    }
}
