import java.lang.reflect.Field;
import java.util.*;
import net.krona.mutagen.client.body.*;
import net.krona.mutagen.strain.Strain;
import net.minecraft.client.model.geom.*;
import org.joml.*;

public class SpiderGeometryCheck {
    static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        try (var stream = SpiderGeometryCheck.class.getClassLoader().getResourceAsStream(
                "assets/mutagen/geo/spider_limb.geo.json")) {
            check(stream != null, "Missing GeckoLib limb asset");
            var raw = software.bernie.geckolib.loading.json.typeadapter.KeyFramesAdapter.GEO_GSON.fromJson(
                    new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8),
                    software.bernie.geckolib.loading.json.raw.Model.class);
            var baked = software.bernie.geckolib.loading.object.BakedModelFactory.DEFAULT_FACTORY.constructGeoModel(
                    software.bernie.geckolib.loading.object.GeometryTree.fromModel(raw));
            check(baked.topLevelBones().size() == 1, "Expected one limb root");
            var upper = baked.topLevelBones().getFirst();
            var lower = upper.getChildBones().getFirst();
            var tip = lower.getChildBones().getFirst();
            check(upper.getName().equals("upper") && lower.getName().equals("lower")
                    && tip.getName().equals("tip"), "Wrong joint hierarchy");
            check(lower.getPivotX() == 8 && tip.getPivotX() == 14, "Wrong joint pivot conversion");
            check(upper.getCubes().size() == 1 && lower.getCubes().size() == 1
                    && tip.getCubes().size() == 1, "Missing limb segments");
        }
        CompactMorphCheck.main(args);
        BeeGeometryCheck.main(args);
        WolfGeometryCheck.main(args);
        MorphBufferCheck.main(args);
        PixelMorphCheck.main(args);
        MergedGeometryCheck.main(args);
        Field field = Box.class.getDeclaredField("CACHE");
        field.setAccessible(true);
        @SuppressWarnings("unchecked") Map<ModelPart, Box> cache = (Map<ModelPart, Box>) field.get(null);
        var reshape = Arrays.stream(BodyMorph.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("reshape")).findFirst().orElseThrow();
        reshape.setAccessible(true);
        String[] profiles = {"zombie", "drowned", "skeleton", "piglin", "enderman", "creeper",
                "wolf", "phantom", "bee", "guardian", "blaze"};
        for (Strain.Bone bone : Strain.Bone.values()) {
            ModelPart part = new ModelPart(List.of(), Map.of());
            PartPose initial = PartPose.offset(3, 12, 0);
            part.setInitialPose(initial);
            Box human = new Box(-2, 0, -2, 2, 12, 2);
            cache.put(part, human); // Equivalent to baked cube bounds, without launching the mixin runtime.
            Box creature = new Box(-1, 0, -1, 1, 16, 1);
            MorphPlan.Target target = new MorphPlan.Target(0, new Strain.Part("head", bone), part,
                    List.of(part), creature, new Quaternionf(), creature, List.of(), List.of());
            Matrix4f goal = new Matrix4f().translate(-0.25f, 0.75f, -0.5f).rotateY(0.3f).scale(0.7f);
            for (int i = 0; i <= 1000; i++) {
                part.resetPose();
                SpiderMorph.Pose pose = SpiderMorph.reshape(bone, part, target, goal, 21 + 79f * i / 1000);
                check(Float.isFinite(part.x + part.y + part.z + part.xRot + part.yRot + part.zRot), "Invalid pose");
                check(part.xScale > 0 && part.yScale > 0 && part.zScale > 0, "Collapsed geometry");
                check(pose.cover() >= 0 && pose.cover() <= 1, "Invalid cover");
                if (i == 0) {
                    check(pose.pivot().distance(new Vector3f(3, 12, 0)) < 0.0001f, "Human endpoint moved");
                    check(pose.shape().equals(human), "Human endpoint changed shape");
                }
                if (i == 1000) {
                    check(pose.pivot().distance(Poses.pivot(goal)) < 0.0001f, "Mob pivot mismatch");
                    check(pose.shape().size().distance(creature.times(0.7f).size()) < 0.0001f, "Mob size mismatch");
                    check(java.lang.Math.abs(Poses.rotation(part).dot(Poses.rotation(goal))) > 0.9999f, "Mob rotation mismatch");
                }
            }
        }
        for (String profile : profiles) {
            for (Strain.Bone bone : Strain.Bone.values()) {
                ModelPart part = new ModelPart(List.of(), Map.of());
                part.setInitialPose(PartPose.offset(3, 12, 0));
                Box human = new Box(-2, 0, -2, 2, 12, 2);
                Box creature = new Box(-1, 0, -1, 1, 16, 1);
                cache.put(part, human);
                MorphPlan.Target target = new MorphPlan.Target(0, new Strain.Part("head", bone), part,
                        List.of(part), creature, new Quaternionf(), creature, List.of(), List.of());
                Matrix4f goal = new Matrix4f().translate(-0.25f, 0.75f, -0.5f).rotateY(0.3f).scale(0.7f);
                float previous = 0;
                for (int i = 0; i <= 1000; i++) {
                    float g = MorphSequence.bone(profile, bone, i / 1000f);
                    check(g >= previous && g >= 0 && g <= 1, "Invalid profile: " + profile);
                    previous = g;
                    part.resetPose();
                    reshape.invoke(null, bone, part, target, goal, g, g, 0f, null);
                    check(Float.isFinite(part.x + part.y + part.z + part.xRot + part.yRot + part.zRot), "Invalid profile pose");
                    check(part.xScale > 0 && part.yScale > 0 && part.zScale > 0, "Collapsed profile geometry");
                    if (i == 0) check(g == 0 && part.xScale == 1 && part.yScale == 1, "Changed human endpoint");
                    if (i == 1000) {
                        check(g == 1, "Incomplete profile endpoint");
                        check(human.size().mul(part.xScale, part.yScale, part.zScale)
                                .distance(creature.times(0.7f).size()) < 0.0001f, "Profile endpoint dimensions");
                        check(java.lang.Math.abs(Poses.rotation(part).dot(Poses.rotation(goal))) > 0.9999f,
                                "Profile endpoint rotation");
                    }
                }
            }
        }
        for (int i = 0; i <= 10000; i++) {
            float phase = i / 10000f;
            float clearance = MorphSurface.clearance(phase);
            if (!MorphSurface.replaced(phase)) check(clearance >= 1.0299f, "Visible skins approach same depth");
            check(clearance >= 1 && Float.isFinite(clearance), "Invalid surface clearance");
            float progress = 21 + phase * 79;
            boolean vanilla = MorphSurface.vanillaSpiderLeg(progress);
            check(vanilla == (net.krona.mutagen.strain.Infection.overall(progress) >= 0.98f), "Leg ownership mismatch");
        }
        check(MorphSurface.clearance(1) == 1, "Final surface must match vanilla dimensions");
        check(MorphSequence.growth("bee", "bone/right_wing", 1, 0.25f)
                > MorphSequence.bone("bee", Strain.Bone.BODY, 0.25f), "Bee wings must precede body compression");
        check(MorphSequence.growth("bee", "bone/right_wing", 1, 0.4f)
                == MorphSequence.growth("bee", "bone/left_wing", 2, 0.4f), "Asymmetric bee wings");
        check(MorphSequence.growth("guardian", "head/tail0", 1, 0.5f) > 0
                && CompactMorph.fuse(21 + 79 * 0.5f) == 0, "Guardian tail must precede absorption");
        check(MorphSequence.bone("phantom", Strain.Bone.RIGHT_ARM, 0.3f) > 0
                && MorphSequence.bone("phantom", Strain.Bone.RIGHT_LEG, 0.3f) == 0, "Phantom wings must precede absorption");
        check(MorphSequence.growth("blaze", "part0", 0, 0.3f) > 0
                && MorphSequence.bone("blaze", Strain.Bone.BODY, 0.3f) == 0, "Blaze rods must precede absorption");
        float limbStage = 21 + 79 * 0.30F;
        float previousFront = 0, previousRear = 0;
        for (int i = 0; i <= 1000; i++) {
            float progress = 21 + 79f * i / 1000;
            float front = growth("right_middle_front_leg", progress);
            float rear = growth("right_middle_hind_leg", progress);
            check(front >= previousFront && rear >= previousRear, "Limb growth regressed");
            check(front == growth("left_middle_front_leg", progress), "Asymmetric front pair");
            check(rear == growth("left_middle_hind_leg", progress), "Asymmetric rear pair");
            previousFront = front;
            previousRear = rear;
        }
        check(growth("right_middle_front_leg", limbStage) == 1, "First pair must precede crouch");
        check(growth("right_middle_hind_leg", limbStage) > 0.8F, "Second pair must precede crouch");
        check(growth("body1", limbStage) == 0, "Abdomen grew before limbs");
        System.out.println("PASS: 72072 poses across 12 strains, scaled endpoints, surface clearance, exclusive leg ownership, growth ordering and GeckoLib baking");
    }
    private static float growth(String name, float progress) {
        MorphPlan.Target target = new MorphPlan.Target(0,
                new Strain.Part(name, Strain.Bone.BODY, true, new Vector3f(1)), null,
                List.of(), null, null, null, List.of(), List.of());
        return SpiderMorph.growth(target, progress);
    }
}