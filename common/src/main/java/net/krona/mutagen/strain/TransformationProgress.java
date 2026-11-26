package net.krona.mutagen.strain;

/** Shared by server collision/camera dimensions and client anatomy; independent of renderer classes. */
public final class TransformationProgress {
    private TransformationProgress() {}

    public static float window(float t, float start, float end) {
        float x = Math.max(0, Math.min(1, (t - start) / (end - start)));
        return x * x * (3 - 2 * x);
    }

    public static float dimensions(String strain, float progress) {
        float p = Infection.overall(progress);
        float start = switch (strain) {
            case "bee", "guardian" -> 0.30F;
            case "spider", "cave_spider" -> 0.34F;
            case "phantom" -> 0.30F;
            case "wolf", "enderman" -> 0.24F;
            case "creeper" -> 0.12F;
            case "zombie" -> 0.34F;
            case "drowned" -> 0.32F;
            case "skeleton" -> 0.25F;
            case "piglin" -> 0.36F;
            case "blaze" -> 0.48F;
            default -> 0.0F;
        };
        return window(p, start, 1);
    }

    public static boolean compact(String strain) {
        return strain.equals("guardian") || strain.equals("bee") || strain.equals("phantom");
    }

    public static float compactPrepare(float progress) {
        return window(Infection.overall(progress), 0.18F, 0.58F);
    }

    public static float compactFuse(float progress) {
        return window(Infection.overall(progress), 0.58F, 1.0F);
    }

    /** Nonhuman bodies gradually stop using the human crawling/crouching proportions. */
    public static float poseOverride(String strain, float progress) {
        return switch (strain) {
            case "guardian", "bee", "phantom" -> compactPrepare(progress);
            case "wolf", "spider", "cave_spider", "creeper", "blaze" -> dimensions(strain, progress);
            default -> 0.0F;
        };
    }

    public static float beeBody(float progress) { return dimensions("bee", progress); }
    public static float beeFold(float progress) { return window(Infection.overall(progress), 0.22F, 0.78F); }
    public static float beeRetract(float progress) { return window(Infection.overall(progress), 0.52F, 0.90F); }
}
