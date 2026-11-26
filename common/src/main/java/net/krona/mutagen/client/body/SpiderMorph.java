package net.krona.mutagen.client.body;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.krona.mutagen.strain.Infection;
import net.krona.mutagen.strain.Strain;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Authored intermediate anatomy, evaluated from infection progress rather than wall-clock time. */
@Environment(EnvType.CLIENT)
public final class SpiderMorph {
    private SpiderMorph() {}

    public static boolean supports(Strain strain) {
        return strain.entityType() == EntityType.SPIDER || strain.entityType() == EntityType.CAVE_SPIDER;
    }

    public static float phase(float progress) {
        return Infection.overall(progress);
    }

    public static float window(float t, float from, float to) {
        float x = Mth.clamp((t - from) / (to - from), 0, 1);
        return x * x * (3 - 2 * x);
    }

    public record Pose(Vector3f pivot, Box shape, float cover) {}

    /** Additional limbs emerge first; the human crouches only after the new supports unfold. */
    public static Pose reshape(Strain.Bone bone, ModelPart part, MorphPlan.Target target,
                               Matrix4f goal, float progress) {
        float p = phase(progress);
        float crouch = window(p, 0.34F, 0.64F);
        float settle = window(p, 0.64F, 1);
        float anatomy = window(p, 0.48F, 0.98F);
        Vector3f intermediate;
        Quaternionf rotation;
        switch (bone) {
            case BODY -> {
                intermediate = new Vector3f(0, 10, -3);
                rotation = new Quaternionf().rotationX(1.05F);
            }
            case HEAD -> {
                intermediate = new Vector3f(0, 8, -7);
                rotation = new Quaternionf();
            }
            case RIGHT_ARM, LEFT_ARM -> {
                float side = bone == Strain.Bone.RIGHT_ARM ? -1 : 1;
                intermediate = new Vector3f(side * 5, 12, -7);
                rotation = new Quaternionf().rotationZ(-side * 0.18F);
            }
            default -> {
                float side = bone == Strain.Bone.RIGHT_LEG ? -1 : 1;
                intermediate = new Vector3f(side * 3, 14, 4);
                rotation = new Quaternionf().rotationX(0.5F);
            }
        }
        // Keep a little of the live walk/look pose during the crouch; fully use the mob at the end.
        intermediate.add(part.x - part.getInitialPose().x,
                part.y - part.getInitialPose().y, part.z - part.getInitialPose().z);
        Quaternionf human = Poses.rotation(part);
        rotation.slerp(human, 0.15F);
        Vector3f pivot = new Vector3f(part.x, part.y, part.z).lerp(intermediate, crouch)
                .lerp(Poses.pivot(goal), settle);
        Poses.setRotation(part, human.slerp(rotation, crouch)
                .slerp(Poses.rotation(goal).mul(target.align()), settle));
        Box original = Box.of(part);
        Box shape = original.lerp(target.aligned().times(Poses.scale(goal)), anatomy);
        Box.Fit fit = original.fit(shape);
        part.xScale = fit.scale().x;
        part.yScale = fit.scale().y;
        part.zScale = fit.scale().z;
        Vector3f offset = BodyMorph.rotate(part, fit.offset()).add(pivot);
        part.setPos(offset.x, offset.y, offset.z);
        return new Pose(pivot, shape, window(p, 0.40F, 0.98F));
    }

    /** Additional front and rear pairs first; abdomen grows after the supports have emerged. */
    public static float growth(MorphPlan.Target target, float progress) {
        float p = phase(progress);
        String name = target.part().part();
        if (name.contains("middle_front")) return window(p, 0.04F, 0.27F);
        if (name.contains("middle_hind")) return window(p, 0.13F, 0.36F);
        return window(p, 0.50F, 0.84F);
    }

    /** Grow legs along their long axis and unfold them from the thorax, keeping their roots attached. */
    public static void unfold(ModelPart part, MorphPlan.Target target, float growth) {
        if (!target.part().part().contains("leg")) return;
        float side = target.part().part().contains("right") ? -1 : 1;
        Quaternionf folded = new Quaternionf().rotationZ(side * 1.25F);
        Poses.setRotation(part, Poses.rotation(part).mul(folded.slerp(new Quaternionf(), growth)));
        float scale = part.xScale / Math.max(growth, 0.0001F);
        part.xScale = scale * growth;
        part.yScale = scale * window(growth, 0, 0.35F);
        part.zScale = part.yScale;
    }
}
