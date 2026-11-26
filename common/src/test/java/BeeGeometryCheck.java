import java.lang.reflect.Field;
import java.util.*;
import net.krona.mutagen.client.body.*;
import net.krona.mutagen.strain.*;
import net.minecraft.client.model.geom.*;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class BeeGeometryCheck {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        String[] strains = {"zombie", "drowned", "skeleton", "piglin", "enderman", "creeper",
                "wolf", "phantom", "bee", "guardian", "blaze", "spider"};
        for (String strain : strains) {
            float previous = 0;
            for (int i = 0; i <= 1000; i++) {
                float progress = i / 10f;
                float t = TransformationProgress.dimensions(strain, progress);
                check(t >= previous && t <= 1, "Invalid dimension timeline: " + strain);
                if (progress <= Infection.START) check(t == 0, "Premature collision change: " + strain);
                if (t > 0 && !strain.equals("spider")) {
                    check(MorphSequence.bone(strain, Strain.Bone.BODY, Infection.overall(progress)) > 0,
                            "Collision precedes visible body: " + strain);
                }
                previous = t;
            }
            check(previous == 1, "Incomplete final collision: " + strain);
        }
        Field field = Box.class.getDeclaredField("CACHE");
        field.setAccessible(true);
        @SuppressWarnings("unchecked") Map<ModelPart, Box> cache = (Map<ModelPart, Box>) field.get(null);
        ModelPart body = new ModelPart(List.of(), Map.of());
        body.setInitialPose(PartPose.ZERO);
        Box human = new Box(-4, 0, -2, 4, 12, 2);
        cache.put(body, human);
        Box creature = new Box(-3.5F, -4, -5, 3.5F, 3, 5);
        Quaternionf alignment = new Quaternionf().rotationX((float)Math.PI / 2);
        Box aligned = creature.rotated(new Quaternionf(alignment).conjugate());
        MorphPlan.Target target = new MorphPlan.Target(0, new Strain.Part("bone/body", Strain.Bone.BODY),
                body, List.of(body), creature, alignment, aligned, List.of(), List.of());
        Matrix4f goal = new Matrix4f().translate(0, 19 / 16f, 0).rotateY(0.2F).scale(0.7F);
        Strain.Bone[] bones = {Strain.Bone.HEAD, Strain.Bone.RIGHT_ARM, Strain.Bone.LEFT_ARM,
                Strain.Bone.RIGHT_LEG, Strain.Bone.LEFT_LEG};
        ModelPart[] limbs = new ModelPart[bones.length];
        Vector3f[] previous = new Vector3f[bones.length];
        for (int i = 0; i < limbs.length; i++) {
            limbs[i] = new ModelPart(List.of(), Map.of());
            float side = i % 2 == 1 ? -1 : 1;
            limbs[i].setInitialPose(i == 0 ? PartPose.ZERO
                    : PartPose.offset(side * (i < 3 ? 5 : 2), i < 3 ? 2 : 12, 0));
        }
        for (int i = 0; i <= 1000; i++) {
            float progress = i / 10f;
            body.resetPose();
            BeeMorph.Pose pose = BeeMorph.reshape(body, target, goal, progress);
            check(Float.isFinite(body.x + body.y + body.z + body.xRot + body.yRot), "Invalid bee body");
            check(pose.cover() >= 0 && pose.cover() <= 1, "Invalid bee coverage");
            for (int j = 0; j < limbs.length; j++) {
                ModelPart limb = limbs[j];
                limb.resetPose();
                limb.visible = true;
                BeeMorph.fold(limb, bones[j], progress, new Vector3f(), new Quaternionf(),
                        pose.pivot(), Poses.rotation(body), pose.shape());
                Vector3f position = new Vector3f(limb.x, limb.y, limb.z);
                check(position.isFinite() && Float.isFinite(limb.xRot + limb.yRot + limb.zRot), "Invalid folded limb");
                check(limb.xScale > 0 && limb.xScale <= 1, "Invalid folded limb scale");
                if (previous[j] != null) check(previous[j].distance(position) < 0.5F, "Bee limb teleported");
                previous[j] = position;
                if (MorphSurface.replaced(pose.cover())) check(!limb.visible, "Human anatomy remains under complete bee shell");
            }
            if (TransformationProgress.beeBody(progress) == 0) {
                check(pose.shape().equals(human) && pose.pivot().length() < 0.0001F, "Early invisible model deformation");
            }
            if (i == 1000) {
                check(pose.pivot().distance(Poses.pivot(goal)) < 0.0001F, "Final bee pivot mismatch");
                check(pose.shape().size().distance(aligned.times(0.7F).size()) < 0.0001F, "Final bee dimensions mismatch");
                check(Math.abs(Poses.rotation(body).dot(Poses.rotation(goal).mul(alignment))) > 0.9999F,
                        "Final bee rotation mismatch");
            }
        }
        System.out.println("PASS: 12 collision timelines, 1001 bee body poses and 5005 coherent folded-limb poses");
    }
}
