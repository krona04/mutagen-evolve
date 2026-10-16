package net.krona.mutagen.strain;

import java.util.ArrayList;
import java.util.List;

/**
 * How the transformation spreads through the body. The infection doesn't cover the whole body at once: it starts where
 * the needle went in and spreads bone by bone. Each bone changes in its own progress window,
 * and the windows overlap — when the arm is almost foreign, the infection is only just reaching the legs.
 * <p>
 * Pure math: computed the same everywhere progress is known.
 */
public final class Infection {
    /** Infection starts at stage II. */
    public static final float START = 21.0F;
    /** How many percent of progress the restructuring of one bone takes. */
    public static final float SPAN = 45.0F;
    /** The last bone finishes exactly at the full form. */
    private static final float LAST_START = 100.0F - SPAN;

    /** Default order: the needle goes into the right arm. */
    private static final List<Strain.Bone> DEFAULT_ORDER = List.of(Strain.Bone.RIGHT_ARM, Strain.Bone.BODY,
            Strain.Bone.LEFT_ARM, Strain.Bone.HEAD, Strain.Bone.RIGHT_LEG, Strain.Bone.LEFT_LEG);

    private Infection() {
    }

    /** Bone infection order: first what the strain specified, then the rest in default order. */
    public static List<Strain.Bone> order(Strain.Body body) {
        List<Strain.Bone> order = new ArrayList<>(DEFAULT_ORDER.size());
        for (Strain.Bone bone : body.spread()) {
            if (!order.contains(bone)) {
                order.add(bone);
            }
        }
        for (Strain.Bone bone : DEFAULT_ORDER) {
            if (!order.contains(bone)) {
                order.add(bone);
            }
        }
        return order;
    }

    /** How restructured the bone is, 0–1. */
    public static float bonePhase(Strain strain, Strain.Bone bone, float progress) {
        int index = strain.spreadIndex(bone);
        int last = Strain.Bone.values().length - 1;
        float start = START + (LAST_START - START) * index / last;
        return clamp((progress - start) / SPAN);
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
