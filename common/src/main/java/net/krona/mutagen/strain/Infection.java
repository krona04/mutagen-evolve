package net.krona.mutagen.strain;

import java.util.ArrayList;
import java.util.List;

/**
 * Как трансформация проходит по телу. Заражение не накрывает всё тело разом: оно начинается там,
 * куда вошла игла, и кость за костью расходится дальше. Каждая кость меняется в своём окне прогресса,
 * и окна идут внахлёст — когда рука уже почти чужая, до ног заражение только доходит.
 * <p>
 * Чистая математика: одинаково считается везде, где известен прогресс.
 */
public final class Infection {
    /** Заражение начинается со стадии II. */
    public static final float START = 21.0F;
    /** Сколько процентов прогресса занимает перестройка одной кости. */
    public static final float SPAN = 45.0F;
    /** Последняя кость заканчивает ровно к полной форме. */
    private static final float LAST_START = 100.0F - SPAN;

    /** Порядок по умолчанию: игла входит в правую руку. */
    private static final List<Strain.Bone> DEFAULT_ORDER = List.of(Strain.Bone.RIGHT_ARM, Strain.Bone.BODY,
            Strain.Bone.LEFT_ARM, Strain.Bone.HEAD, Strain.Bone.RIGHT_LEG, Strain.Bone.LEFT_LEG);

    private Infection() {
    }

    /** Порядок заражения костей: сначала то, что задал штамм, потом остальные в порядке по умолчанию. */
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

    /** Насколько перестроена кость, 0–1. */
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
