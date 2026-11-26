package net.krona.mutagen.client.body;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.krona.mutagen.strain.Infection;
import net.krona.mutagen.strain.Strain;
import net.krona.mutagen.strain.TransformationProgress;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.EntityType;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** A single coherent body: fold limbs into the changing thorax before retiring the human anatomy. */
@Environment(EnvType.CLIENT)
public final class BeeMorph {
    private BeeMorph() {}
    public static boolean supports(Strain strain) { return strain.entityType() == EntityType.BEE; }
    public record Pose(Vector3f pivot, Box shape, float cover) {}

    public static Pose reshape(ModelPart body, MorphPlan.Target target, Matrix4f goal, float progress) {
        float t = TransformationProgress.beeBody(progress);
        Quaternionf rotation = Poses.rotation(body).slerp(Poses.rotation(goal).mul(target.align()), t);
        Box original = Box.of(body);
        Box shape = original.lerp(target.aligned().times(Poses.scale(goal)), t);
        // Interpolate centres, rather than pivots: changing cuboid origins must not shift the body twice.
        Vector3f humanCenter = BodyMorph.rotate(body, original.center()).add(body.x, body.y, body.z);
        Vector3f goalCenter = Poses.rotation(goal).mul(target.align())
                .transform(target.aligned().times(Poses.scale(goal)).center()).add(Poses.pivot(goal));
        Vector3f pivot = humanCenter.lerp(goalCenter, t).sub(rotation.transform(shape.center()));
        Poses.setRotation(body, rotation);
        Box.Fit fit = original.fit(shape);
        body.xScale = fit.scale().x;
        body.yScale = fit.scale().y;
        body.zScale = fit.scale().z;
        Vector3f rendered = BodyMorph.rotate(body, fit.offset()).add(pivot);
        body.setPos(rendered.x, rendered.y, rendered.z);
        // The bee face is part of its torso mesh. Delay it until the human head has started retracting.
        float cover = TransformationProgress.window(Infection.overall(progress), 0.60F, 0.96F);
        return new Pose(pivot, shape, cover);
    }

    public static void fold(ModelPart part, Strain.Bone bone, float progress, Vector3f humanPivot,
                            Quaternionf humanRotation, Vector3f pivot, Quaternionf rotation, Box shape) {
        float fold = TransformationProgress.beeFold(progress);
        float retract = TransformationProgress.beeRetract(progress);
        Quaternionf inverse = new Quaternionf(humanRotation).conjugate();
        Vector3f relative = inverse.transform(new Vector3f(part.x, part.y, part.z).sub(humanPivot));
        Vector3f anchor = shape.center();
        if (bone == Strain.Bone.HEAD) {
            anchor.y = shape.minY();
        } else {
            float side = bone == Strain.Bone.RIGHT_ARM || bone == Strain.Bone.RIGHT_LEG ? -1 : 1;
            anchor.x += side * shape.size().x * 0.35F;
            anchor.y += (bone == Strain.Bone.RIGHT_LEG || bone == Strain.Bone.LEFT_LEG ? 1 : -1)
                    * shape.size().y * 0.25F;
        }
        // Connections travel in the torso's local space, so the limbs cannot remain behind as it turns.
        Vector3f position = rotation.transform(relative.lerp(anchor, fold)).add(pivot);
        Quaternionf local = inverse.mul(Poses.rotation(part), new Quaternionf());
        Quaternionf tucked = new Quaternionf().rotationX(bone == Strain.Bone.HEAD ? 0 : -1.2F);
        Poses.setRotation(part, new Quaternionf(rotation).mul(local.slerp(tucked, fold)));
        part.setPos(position.x, position.y, position.z);
        float scale = Math.max(0.001F, 1 - retract);
        part.xScale = scale;
        part.yScale = scale;
        part.zScale = scale;
        if (retract >= 1) part.visible = false;
    }
}
