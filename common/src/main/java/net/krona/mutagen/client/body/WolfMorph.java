package net.krona.mutagen.client.body;

import net.krona.mutagen.strain.Infection;
import net.krona.mutagen.strain.Strain;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.EntityType;
import org.joml.*;

/** Every human core cuboid persists and deforms into its wolf counterpart. No limb alpha or collapse. */
public final class WolfMorph {
    private WolfMorph() {}
    public static boolean supports(Strain strain) { return strain.entityType() == EntityType.WOLF; }
    public record Pose(Vector3f pivot, Box shape, float material) {}

    public static Pose reshape(Strain.Bone bone, ModelPart part, MorphCuboid.Core core,
                               Matrix4f rootGoal, float progress) {
        float p = Infection.overall(progress);
        float crouch = MorphSequence.window(p, 0.24F, 0.60F);
        float settle = MorphSequence.window(p, 0.60F, 1);
        float anatomy = MorphSequence.bone("wolf", bone, p);
        Matrix4f goal = new Matrix4f(rootGoal).mul(core.relative());
        Vector3f intermediate;
        Quaternionf middle;
        switch (bone) {
            case BODY -> { intermediate = new Vector3f(0, 9, 1); middle = new Quaternionf().rotationX(0.9F); }
            case HEAD -> { intermediate = new Vector3f(0, 7, -5); middle = new Quaternionf(); }
            case RIGHT_ARM, LEFT_ARM -> {
                float side = bone == Strain.Bone.RIGHT_ARM ? -1 : 1;
                intermediate = new Vector3f(side * 4, 11, -5); middle = new Quaternionf();
            }
            default -> {
                float side = bone == Strain.Bone.RIGHT_LEG ? -1 : 1;
                intermediate = new Vector3f(side * 2, 14, 5); middle = new Quaternionf().rotationX(0.4F);
            }
        }
        Quaternionf originalRotation = Poses.rotation(part);
        intermediate.add(part.x - part.getInitialPose().x, part.y - part.getInitialPose().y,
                part.z - part.getInitialPose().z);
        Vector3f pivot = new Vector3f(part.x,part.y,part.z).lerp(intermediate,crouch).lerp(Poses.pivot(goal),settle);
        Poses.setRotation(part, originalRotation.slerp(middle,crouch).slerp(Poses.rotation(goal),settle));
        Box original = Box.of(part);
        Box shape = original.lerp(core.box().times(Poses.scale(goal)),anatomy);
        Box.Fit fit = original.fit(shape);
        part.xScale=fit.scale().x; part.yScale=fit.scale().y; part.zScale=fit.scale().z;
        Vector3f position = BodyMorph.rotate(part,fit.offset()).add(pivot);
        part.setPos(position.x,position.y,position.z);
        return new Pose(pivot,shape,MorphSequence.window(p,0.10F,1));
    }
}
