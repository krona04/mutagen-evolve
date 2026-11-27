package net.krona.mutagen.client.body;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * Infection mask specifying the progress threshold for each texture pixel.
 * <p>
 * Combine 3–4 pixel spots with fine noise to create uneven patch edges. Seed the pattern with the player's
 * UUID so it stays consistent between frames and sessions.
 */
@Environment(EnvType.CLIENT)
public final class InfectionMask {
    private static final int CELL = 3;

    private final long seed;

    public InfectionMask(long seed) {
        this.seed = seed;
    }

    /** Threshold from 0 to 1. The pixel becomes infected when the bone phase exceeds it. */
    public float at(int x, int y) {
        float cells = smoothNoise(x / (float) CELL, y / (float) CELL, seed);
        float grain = hash(x, y, seed ^ 0x5DEECE66DL);
        return cells * 0.72F + grain * 0.28F;
    }

    /** Use separate tint noise to give infected skin a mottled appearance. */
    public float shade(int x, int y) {
        return hash(x * 7 + 3, y * 13 + 1, seed ^ 0x2545F4914F6CDD1DL);
    }

    private static float smoothNoise(float x, float y, long seed) {
        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        float fx = smooth(x - x0);
        float fy = smooth(y - y0);
        float a = hash(x0, y0, seed);
        float b = hash(x0 + 1, y0, seed);
        float c = hash(x0, y0 + 1, seed);
        float d = hash(x0 + 1, y0 + 1, seed);
        float top = a + (b - a) * fx;
        float bottom = c + (d - c) * fx;
        return top + (bottom - top) * fy;
    }

    private static float smooth(float t) {
        return t * t * (3.0F - 2.0F * t);
    }

    private static float hash(int x, int y, long seed) {
        long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL);
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return (h >>> 40) / (float) (1L << 24);
    }
}
