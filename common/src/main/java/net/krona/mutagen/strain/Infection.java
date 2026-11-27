package net.krona.mutagen.strain;

import java.util.ArrayList;
import java.util.List;

/**
 * Infection spreads from the injection site, one bone at a time. Bone progress windows overlap, so legs can
 * start changing while the arm is almost transformed.
 * <p>
 * The calculation is deterministic and needs only the progress value.
 */
public final class Infection {
    /** Infection starts at stage II. */
    public static final float START = 21.0F;
    /** Share of overall progress spent transforming one bone. */
    public static final float SPAN = 45.0F;
    /** The final bone finishes when the player reaches full form. */
    private static final float LAST_START = 100.0F - SPAN;

    /** The default order starts at the right arm, the injection site. */
    private static final List<Strain.Bone> DEFAULT_ORDER = List.of(Strain.Bone.RIGHT_ARM, Strain.Bone.BODY,
            Strain.Bone.LEFT_ARM, Strain.Bone.HEAD, Strain.Bone.RIGHT_LEG, Strain.Bone.LEFT_LEG);

    private Infection() {
    }

    /** Use the strain's bone order first, followed by any remaining bones in default order. */
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

    /** Bone transformation progress from 0 to 1. */
    public static float bonePhase(Strain strain, Strain.Bone bone, float progress) {
        int index = strain.spreadIndex(bone);
        int last = Strain.Bone.values().length - 1;
        float start = START + (LAST_START - START) * index / last;
        return clamp((progress - start) / SPAN);
    }

    /** Overall body transformation from 0 to 1, from the start of infection through full form. */
    public static float overall(float progress) {
        return clamp((progress - START) / (100.0F - START));
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
