package net.krona.mutagen.client.body;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.krona.mutagen.MutagenConfig;
import net.krona.mutagen.data.MutagenData;
import net.krona.mutagen.data.MutagenPlayer;
import net.krona.mutagen.strain.Infection;
import net.krona.mutagen.strain.Stage;
import net.krona.mutagen.strain.Strain;
import net.krona.mutagen.strain.Strains;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Body shape transformation: the player's bones themselves become parts of the creature.
 * <p>
 * Every bone that has a counterpart in the creature's model ({@link MorphPlan}) smoothly takes on its size,
 * position in the body and initial tilt as it becomes infected. Everything is computed in shared model
 * coordinates, where the ground is at the same height for any mob, so the body neither floats nor sinks:
 * a creeper's legs get shorter, and the torso and head sink down to them along with it. Bones the
 * creature doesn't have retract into the body. On top of the shape, {@link BodyMotion} adds convulsions and twitches.
 * <p>
 * The player model is shared by everyone, so before the vanilla animation the bones are reset to the initial pose
 * ({@link #resetPose}) — otherwise offsets would accumulate frame to frame and the body would start spinning.
 * The frame's result is stored ({@link Frame}): the creature's parts are fitted onto the same shape using it.
 */
@Environment(EnvType.CLIENT)
public final class BodyMorph {
    private static final Frame FRAME = new Frame();
    /**
     * The first-person arm is being rendered right now. There the arm is attached to the camera, not the body: it changes shape
     * but doesn't leave the shoulder — otherwise an arm turning into a creeper leg would float up into the middle of the screen.
     */
    private static boolean firstPerson;
    /** A bone that the transformation lowers below this (in model pixels) becomes a leg. */
    private static final float LEG_DROP = 8.0F;
    /** How much a bone swells at the height of restructuring. */
    private static final float SWELL = 0.12F;
    /** Tremor amplitude of a restructuring bone, in radians. */
    private static final float TREMOR = 0.035F;

    /** From which phase clothing falls off the bone. */
    private static final float CLOTHES_SHED = 0.5F;
    /** From which phase the player's bone fully gives way to the creature's part. */
    private static final float FULLY_TURNED = 0.98F;

    private BodyMorph() {
    }

    /** Before the vanilla animation: initial pose and scale for all bones. */
    public static void resetPose(PlayerModel<?> model) {
        for (ModelPart part : bones(model)) {
            part.resetPose();
            part.xScale = 1.0F;
            part.yScale = 1.0F;
            part.zScale = 1.0F;
        }
    }

    /** After the vanilla animation: the mutant's shape, pose and movements. */
    public static void apply(PlayerModel<?> model, LivingEntity entity) {
        FRAME.clear(entity.getId());
        if (!(entity instanceof Player player)) {
            return;
        }
        MutagenData data = MutagenPlayer.of(player);
        MutagenData.Gene gene = data == null ? null : data.primary();
        Strain strain = gene == null ? null : Strains.get(gene.strainId());
        if (gene == null || strain == null || gene.stage() == Stage.FULL || gene.progress() < Infection.START) {
            copyOverlays(model);
            return;
        }

        if (MutagenConfig.get().bodyMorph) {
            // Leg stride before any edits: an arm becoming a leg takes it over from the diagonally opposite leg.
            float[] rightStep = {model.rightLeg.xRot, model.rightLeg.yRot, model.rightLeg.zRot};
            float[] leftStep = {model.leftLeg.xRot, model.leftLeg.yRot, model.leftLeg.zRot};
            // Pose first: the shape offset is computed from the bone's new tilt.
            posture(model, strain, gene.progress());
            MorphPlan plan = MorphPlan.of(strain);
            for (Strain.Bone bone : Strain.Bone.values()) {
                float t = Infection.bonePhase(strain, bone, gene.progress());
                if (t <= 0.0F) {
                    continue;
                }
                ModelPart part = bone(model, bone);
                if (plan.absorbed(bone)) {
                    absorb(part, t);
                    continue;
                }
                MorphPlan.Target target = plan.target(bone);
                if (target != null) {
                    float[] step = bone == Strain.Bone.RIGHT_ARM ? leftStep
                            : bone == Strain.Bone.LEFT_ARM ? rightStep : null;
                    reshape(bone, part, target, t, time(player) + bone.ordinal() * 7.3F, step);
                }
            }
        }

        BodyMotion.apply(model, player, gene);
        copyOverlays(model);
        if (MutagenConfig.get().bodyMorph) {
            shed(model, strain, gene.progress());
        }
    }

    /**
     * Clothing (the second skin layer) falls off a bone once it has transformed halfway: from then on it has the creature's
     * hide, and two layers of skin on top of each other would look like a bug. A fully transformed bone
     * is hidden entirely — what remains is the creature's part, with all its gaps, like between a skeleton's ribs.
     */
    private static void shed(PlayerModel<?> model, Strain strain, float progress) {
        MorphPlan plan = MorphPlan.of(strain);
        for (Strain.Bone bone : Strain.Bone.values()) {
            float t = Infection.bonePhase(strain, bone, progress);
            if (t >= CLOTHES_SHED) {
                overlay(model, bone).visible = false;
            }
            if (t >= FULLY_TURNED && plan.target(bone) != null && MutagenConfig.get().hybridRender) {
                bone(model, bone).visible = false;
            }
        }
    }

    private static ModelPart overlay(PlayerModel<?> model, Strain.Bone bone) {
        return switch (bone) {
            case HEAD -> model.hat;
            case BODY -> model.jacket;
            case RIGHT_ARM -> model.rightSleeve;
            case LEFT_ARM -> model.leftSleeve;
            case RIGHT_LEG -> model.rightPants;
            case LEFT_LEG -> model.leftPants;
        };
    }

    /**
     * The bone takes the shape of the creature's part: the pivot moves to its place in the body, the tilt to its
     * initial tilt, and the bone's box stretches and shifts towards its box.
     * <p>
     * As with a living body, none of this happens at once or in a straight line. The limb first moves forward,
     * then drops, and only at the end pulls in sideways — this is how a creeper's arm becomes a front leg,
     * going around the chest instead of through it. The bone's thickness changes before its length. While the bone
     * is restructuring it swells and trembles slightly, and calms down towards the end.
     */
    private static void reshape(Strain.Bone bone, ModelPart part, MorphPlan.Target target, float t, float time,
                                float[] step) {
        PartPose own = part.getInitialPose();
        PartPose goal = target.pose();
        boolean becomesLeg = goal.y - own.y > LEG_DROP;
        if (becomesLeg && step != null) {
            // An arm that has become a leg no longer swings like an arm or holds a grip pose: it steps
            // in pair with the diagonally opposite leg, the way quadrupeds walk.
            float gait = window(t, 0.15F, 0.70F);
            part.xRot = Mth.lerp(gait, part.xRot, step[0]);
            part.yRot = Mth.lerp(gait, part.yRot, step[1]);
            part.zRot = Mth.lerp(gait, part.zRot, step[2]);
        }
        float travel = firstPerson ? 0.0F : 1.0F;
        // First the body settles — the bone drops along the side as it shortens — and only then
        // moves forward or back and pulls in under the torso. This way a creeper's arm doesn't stick forward like a zombie's.
        float lengthPhase = window(t, 0.0F, 0.75F);
        float pivotX = part.x + (goal.x - own.x) * window(t, 0.50F, 1.0F) * travel;
        float pivotY = part.y + (goal.y - own.y) * lengthPhase * travel;
        float pivotZ = part.z + (goal.z - own.z) * window(t, 0.35F, 1.0F) * travel;
        float turn = window(t, 0.20F, 1.0F);
        float active = Mth.sin(t * Mth.PI);
        part.xRot += goal.xRot * turn + Mth.sin(time * 1.9F) * TREMOR * active;
        part.yRot += goal.yRot * turn;
        part.zRot += goal.zRot * turn + Mth.sin(time * 2.3F + 1.1F) * TREMOR * active;

        float thickness = window(t, 0.0F, 0.70F);
        float swell = 1.0F + SWELL * active;
        // Attachment height and bone length change together: otherwise the feet would sink into the ground halfway through.
        Box shape = Box.of(part).lerp(target.box(), thickness, lengthPhase, thickness)
                .scaled(swell, 1.0F + SWELL * 0.4F * active, swell);
        Box.Fit fit = Box.of(part).fit(shape);
        part.xScale = fit.scale().x();
        part.yScale = fit.scale().y();
        part.zScale = fit.scale().z();
        Vector3f shift = rotate(part, fit.offset());
        part.x = pivotX + shift.x;
        part.y = pivotY + shift.y;
        part.z = pivotZ + shift.z;

        FRAME.put(bone, t, pivotX, pivotY, pivotZ, shape, becomesLeg && t >= 0.5F);
    }

    /** The portion of the transformation falling into the window [from, to], with a smoothed start and end. */
    private static float window(float t, float from, float to) {
        float x = Mth.clamp((t - from) / (to - from), 0.0F, 1.0F);
        return x * x * (3.0F - 2.0F * x);
    }

    private static float time(Player player) {
        return (player.tickCount + net.minecraft.client.Minecraft.getInstance().getTimer()
                .getGameTimeDeltaPartialTick(true)) * 0.35F;
    }

    /** Bones the creature doesn't have shrivel and retract into the body. */
    private static void absorb(ModelPart part, float t) {
        float scale = Math.max(0.001F, 1.0F - t);
        part.xScale = scale;
        part.yScale = scale;
        part.zScale = scale;
    }

    /** Pose from the strain data: bone rotation in degrees, e.g. a zombie's arms stretched forward. */
    private static void posture(PlayerModel<?> model, Strain strain, float progress) {
        for (Strain.Morph morph : strain.body().morph()) {
            float t = Infection.bonePhase(strain, morph.bone(), progress);
            if (t <= 0.0F) {
                continue;
            }
            ModelPart part = bone(model, morph.bone());
            part.xRot += morph.rotation().x() * Mth.DEG_TO_RAD * t;
            part.yRot += morph.rotation().y() * Mth.DEG_TO_RAD * t;
            part.zRot += morph.rotation().z() * Mth.DEG_TO_RAD * t;
        }
    }

    /** Offset in part coordinates → pivot offset in parent coordinates. */
    public static Vector3f rotate(ModelPart part, Vector3f local) {
        return new Quaternionf().rotationZYX(part.zRot, part.yRot, part.xRot).transform(new Vector3f(local));
    }

    /** Clothing layers follow the bones: vanilla copies them before the strain gets to change the bone. */
    private static void copyOverlays(PlayerModel<?> model) {
        model.hat.copyFrom(model.head);
        model.jacket.copyFrom(model.body);
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftSleeve.copyFrom(model.leftArm);
        model.rightPants.copyFrom(model.rightLeg);
        model.leftPants.copyFrom(model.leftLeg);
    }

    private static ModelPart[] bones(PlayerModel<?> model) {
        return new ModelPart[]{model.head, model.body, model.rightArm, model.leftArm, model.rightLeg,
                model.leftLeg};
    }

    public static ModelPart bone(PlayerModel<?> model, Strain.Bone bone) {
        return switch (bone) {
            case HEAD -> model.head;
            case BODY -> model.body;
            case RIGHT_ARM -> model.rightArm;
            case LEFT_ARM -> model.leftArm;
            case RIGHT_LEG -> model.rightLeg;
            case LEFT_LEG -> model.leftLeg;
        };
    }

    public static void setFirstPerson(boolean value) {
        firstPerson = value;
    }

    /** This player's last-frame shape, if it has already been computed. */
    @Nullable
    public static Frame frame(Player player) {
        return FRAME.owner == player.getId() ? FRAME : null;
    }

    /**
     * The transformation result for a frame: for each bone — the phase, the pivot before the box offset, and the box
     * the bone currently occupies. The creature's part is fitted into the same box, and the surfaces line up.
     */
    public static final class Frame {
        private int owner = Integer.MIN_VALUE;
        private final float[] phase = new float[Strain.Bone.values().length];
        private final float[][] pivot = new float[Strain.Bone.values().length][3];
        private final Box[] shape = new Box[Strain.Bone.values().length];
        private final boolean[] leg = new boolean[Strain.Bone.values().length];

        private void clear(int entity) {
            owner = entity;
            java.util.Arrays.fill(phase, 0.0F);
            java.util.Arrays.fill(shape, null);
            java.util.Arrays.fill(leg, false);
        }

        private void put(Strain.Bone bone, float t, float x, float y, float z, Box box, boolean becameLeg) {
            int i = bone.ordinal();
            phase[i] = t;
            leg[i] = becameLeg;
            pivot[i][0] = x;
            pivot[i][1] = y;
            pivot[i][2] = z;
            shape[i] = box;
        }

        public float phase(Strain.Bone bone) {
            return phase[bone.ordinal()];
        }

        /** The arm is already more leg than arm: an item can't be held in it or rendered. */
        public boolean becameLeg(Strain.Bone bone) {
            return leg[bone.ordinal()];
        }

        @Nullable
        public Box shape(Strain.Bone bone) {
            return shape[bone.ordinal()];
        }

        public float[] pivot(Strain.Bone bone) {
            return pivot[bone.ordinal()];
        }
    }
}
