import net.krona.mutagen.client.body.CompactMorph;
import net.krona.mutagen.client.body.CreatureKinematics;
import net.krona.mutagen.strain.BodyShape;
import net.krona.mutagen.strain.TransformationProgress;
import net.minecraft.world.entity.EntityDimensions;

public final class MotionAndCollisionCheck {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    private static void near(float actual, float expected, String message) {
        check(Math.abs(actual - expected) < 0.00001F, message + ": " + actual + " != " + expected);
    }
    public static void main(String[] args) {
        GroundedFlightCheck.main(args);
        AquaticAdaptationCheck.main(args);
        BodyShape guardian = new BodyShape(.85F, .85F, .7225F, 0, 1);
        BodyShape previous = BodyShape.HUMAN;
        for (int i = 0; i <= 10000; i++) {
            float progress = i / 100F;
            BodyShape current = BodyShape.transition("guardian", progress, guardian);
            check(current.height() > 0 && current.eyeHeight() <= current.height(), "Camera inside body");
            check(current.height() <= previous.height() + .00001F, "Monotonic guardian height");
            check(Math.abs(current.height() - previous.height()) < .002F, "No collision height steps");
            check(Math.abs(current.width() - previous.width()) < .002F, "No collision width steps");
            near(CompactMorph.prepare(progress), TransformationProgress.compactPrepare(progress), "Shared folding phase");
            near(CompactMorph.fuse(progress), TransformationProgress.compactFuse(progress), "Shared fusion phase");
            if (progress <= 21) near(current.height(), 1.8F, "Human before visible transformation");
            previous = current;
        }
        check(CompactMorph.prepare(40) > 0, "Visible guardian folding has begun");
        check(BodyShape.transition("guardian", 40, guardian).height() < 1.8F,
                "Guardian collider follows early folding instead of waiting until 45 percent");
        near(previous.width(), guardian.width(), "Final width");
        near(previous.height(), guardian.height(), "Final height");
        near(previous.eyeHeight(), guardian.eyeHeight(), "Final eye height");
        for (float poseHeight : new float[]{1.8F, 1.5F, .6F}) {
            for (float scale : new float[]{.5F, 1, 2}) {
                EntityDimensions pose = EntityDimensions.scalable(.6F * scale, poseHeight * scale)
                        .withEyeHeight(poseHeight * .9F * scale);
                EntityDimensions result = guardian.posed(pose, scale, 1);
                near(result.width(), guardian.width(), "Guardian never uses human crawling width");
                near(result.height(), guardian.height(), "Guardian never uses human swimming height");
                near(result.eyeHeight(), guardian.eyeHeight(), "Guardian camera independent of human pose");
            }
        }
        EntityDimensions crouch = EntityDimensions.scalable(.6F, 1.5F).withEyeHeight(1.27F);
        near(BodyShape.HUMAN.posed(crouch, 1, 0).height(), 1.5F, "Humanoid crouching preserved");

        var wolf = new CreatureKinematics.State(179);
        wolf.advance("wolf", 0, 0, 0, 0, false, true, 179, 0);
        wolf.advance("wolf", 1, .05, 0, -.2, false, true, -179, 0);
        check(Math.abs(wolf.bodyYaw - wolf.bodyYawOld) <= 10.001F, "Bounded turn across angle wrap");
        float phase = wolf.walkPosition;
        for (int frame = 0; frame < 240; frame++) wolf.advance("wolf", 1, .05, 0, -.2, false, true, -179, 0);
        near(wolf.walkPosition, phase, "240 render passes cannot accelerate gait");
        for (int tick = 2; tick < 40; tick++) wolf.advance("wolf", tick, 0, 0, 0, false, true, -179, 0);
        check(wolf.walkSpeed < .0001F, "Legs stop when player stops");
        var fish = new CreatureKinematics.State(0);
        fish.advance("guardian", 0, 0, 0, 0, true, false, 0, 0);
        for (int tick = 1; tick <= 30; tick++) fish.advance("guardian", tick, 0, .05, .1, true, false, 0, 80);
        check(fish.pitch < -20 && fish.pitch > -45, "Swimmer tilts with upward trajectory, independently of camera");
        check(fish.spikes > .95F, "Swimming guardian keeps visible orange spines");
        near(fish.walkSpeed, 0, "Guardian cannot walk with human legs");
        float tail = fish.tail;
        for (int tick = 31; tick <= 60; tick++) fish.advance("guardian", tick, 0, 0, 0, true, false, 0, 0);
        check(fish.tail > tail && fish.spikes > .7F, "Idle swimmer keeps slow tail and extends spines");
        fish.advance("guardian", 61, 100, 0, 100, true, false, 0, 0);
        check(Float.isFinite(fish.pitch + fish.bodyYaw + fish.tail), "Teleport cannot corrupt animation");
        for (float from : new float[]{-720, -181, -179, 0, 179, 181, 720}) {
            for (float to : new float[]{-179, 0, 179}) {
                check(Math.abs(CreatureKinematics.turn(from, to, 7) - from) <= 7, "Shortest bounded body turn");
            }
        }
        System.out.println("Motion/collision checks passed: " + checks);
    }
}
