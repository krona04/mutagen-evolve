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
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Transform the player's bones into the creature's model parts.
 * <p>
 * Each bone matched by {@link MorphPlan} gradually takes on its counterpart's dimensions, position, and
 * rotation in the creature's current animated pose. Shared coordinates keep the ground level across models.
 * The spider lowers onto its legs, the wolf goes on all fours, and the enderman stretches upright.
 * <p>
 * Reshape the whole skeleton with overall progress, letting early-infected bones lead slightly. Creature
 * skin spreads bone by bone. Bones with no creature counterpart retract into the body. {@link BodyMotion}
 * adds convulsions over the pose.
 * <p>
 * The player model is shared. Restore the original bone pose with {@link #resetPose} before vanilla
 * animation to avoid accumulating rotations. Cache the resulting {@link Frame} so creature parts fit the
 * same geometry.
 */
@Environment(EnvType.CLIENT)
public final class BodyMorph {
    private static final Frame FRAME = new Frame();
    /**
     * The first-person arm is attached to the camera. Reshape it while keeping its shoulder position so it
     * does not drift to the middle of the screen when turning into a creeper leg.
     */
    private static boolean firstPerson;
    /** A bone counts as a leg if the transformation lowers it past this threshold in model pixels. */
    private static final float LEG_DROP = 8.0F;
    /** How much the bone swells halfway through its transformation. */
    private static final float SWELL = 0.12F;
    /** Amplitude of the transforming bone's tremor in radians. */
    private static final float TREMOR = 0.035F;

    /** Transformation phase at which clothing falls off a bone with no creature part. */
    private static final float CLOTHES_SHED = 0.5F;
    /**
     * Creature skin coverage at which clothing falls off the bone. The outer skin layer would otherwise
     * hide creature surfaces, such as the spider head under the player's hair.
     */
    private static final float CLOTHES_UNDER_CREATURE = 0.08F;
    /**
     * How far skin coverage leads the shape change. Use the larger of infection and scaled morph progress
     * so a nearly transformed bone is already fully covered in creature skin.
     */
    private static final float COVER_LEAD = 1.35F;
    /** Transformation phase at which the creature part fully replaces the player bone. */
    private static final float FULLY_TURNED = MorphSurface.REPLACED;

    private BodyMorph() {
    }

    /** Restore the original bone poses and scales before vanilla animation. */
    public static void resetPose(PlayerModel<?> model) {
        for (ModelPart part : bones(model)) {
            part.resetPose();
            part.xScale = 1.0F;
            part.yScale = 1.0F;
            part.zScale = 1.0F;
        }
    }

    /** Weight of overall progress relative to the infection of an individual bone when shaping the silhouette. */

    /** Transform the torso first so retracting bones can move toward it. */
    private static final Strain.Bone[] ORDER = {Strain.Bone.BODY, Strain.Bone.HEAD, Strain.Bone.RIGHT_ARM,
            Strain.Bone.LEFT_ARM, Strain.Bone.RIGHT_LEG, Strain.Bone.LEFT_LEG};

    /** Apply the mutant's shape, pose, and motion after vanilla animation, using the usual player animation parameters. */
    public static void apply(PlayerModel<?> model, LivingEntity entity, float limbSwing, float limbSwingAmount,
                             float ageInTicks, float netHeadYaw, float headPitch) {
        // The piglin model extends the player model, so this hook also sees its animation. Skip creature
        // dummies so they cannot overwrite the player's frame.
        if (!(entity instanceof Player player)) {
            return;
        }
        FRAME.clear(entity.getId());
        MutagenData data = MutagenPlayer.of(player);
        MutagenData.Gene gene = data == null ? null : data.primary();
        Strain strain = gene == null ? null : Strains.get(gene.strainId());
        if (gene == null || strain == null || gene.stage() == Stage.FULL || gene.progress() < Infection.START) {
            copyOverlays(model);
            return;
        }

        MorphPlan plan = MorphPlan.of(strain);
        MutagenConfig config = MutagenConfig.get();
        if (config.bodyMorph || config.hybridRender) {
            // The creature in its current animated pose. Bones approach it and grown parts attach to it.
            plan.animate(player, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
            for (Strain.Bone bone : Strain.Bone.values()) {
                MorphPlan.Target target = config.bodyMorph && config.hybridRender && !firstPerson && ContinuousMorph.supports(strain)
                        ? ContinuousMorph.target(strain, plan, bone) : plan.target(bone);
                if (target != null) {
                    FRAME.goal[bone.ordinal()] = plan.pose(target);
                }
            }
            for (MorphPlan.Target grown : plan.grown()) {
                FRAME.grown.add(plan.pose(grown));
            }
        }

        if (config.bodyMorph) {
            posture(model, strain, gene.progress());
            if (!firstPerson && !WolfMorph.supports(strain) && !CompactMorph.supports(strain)) MorphSequence.prepare(model, strain, gene.progress());
            CompactMorph.Frame compact = !firstPerson && config.hybridRender && CompactMorph.supports(strain)
                    ? new CompactMorph.Frame(model, strain.id().getPath(), gene.progress()) : null;

            Vector3f core = null;
            // Original torso used to locate the human shoulders, neck, and hips.
            Vector3f torsoPivot = new Vector3f(model.body.x, model.body.y, model.body.z);
            Quaternionf torsoRotation = Poses.rotation(model.body);
            Torso torso = null;
            for (Strain.Bone bone : ORDER) {
                float t = Infection.bonePhase(strain, bone, gene.progress());
                float g = MorphSequence.phase(strain, bone, gene.progress());
                if (MorphSequence.supports(strain)) t = g;
                ModelPart part = bone(model, bone);
                if (plan.absorbed(bone) && !(config.hybridRender && !firstPerson && ContinuousMorph.supports(strain))) {
                    if (BeeMorph.supports(strain) && !firstPerson && torso != null) {
                        BeeMorph.fold(part, bone, gene.progress(), torso.humanPivot(), torso.humanRotation(),
                                torso.pivot(), torso.rotation(), FRAME.shape(Strain.Bone.BODY));
                    } else {
                        absorb(part, g, torso);
                    }
                    continue;
                }
                MorphPlan.Target target = config.bodyMorph && config.hybridRender && !firstPerson && ContinuousMorph.supports(strain)
                        ? ContinuousMorph.target(strain, plan, bone) : plan.target(bone);
                Matrix4f goal = FRAME.goal[bone.ordinal()];
                if (target != null && goal != null) {
                    if (ContinuousMorph.supports(strain) && config.hybridRender && !firstPerson) {
                        WolfMorph.Pose staged;
                        if (compact != null) {
                            MorphCuboid.Core piece = MorphCuboid.core(target.model());
                            MorphCuboid.Core bodyPiece = MorphCuboid.core(ContinuousMorph.target(strain, plan, Strain.Bone.BODY).model());
                            staged = compact.reshape(bone, part, piece, goal, ContinuousMorph.surface(strain, bone, piece).box(),
                                    bodyPiece, FRAME.goal(Strain.Bone.BODY));
                        } else {
                            staged = ContinuousMorph.reshape(strain, bone, part, MorphCuboid.core(target.model()), goal, gene.progress());
                        }
                        Vector3f pivot = staged.pivot();
                        FRAME.put(bone, staged.material(), pivot.x, pivot.y, pivot.z, staged.shape(),
                                (bone == Strain.Bone.RIGHT_ARM || bone == Strain.Bone.LEFT_ARM)
                                        && Infection.overall(gene.progress()) >= 0.60F);
                    } else if (BeeMorph.supports(strain) && !firstPerson && bone == Strain.Bone.BODY) {
                        BeeMorph.Pose staged = BeeMorph.reshape(part, target, goal, gene.progress());
                        Vector3f pivot = staged.pivot();
                        FRAME.put(bone, staged.cover(), pivot.x, pivot.y, pivot.z, staged.shape(), false);
                    } else if (SpiderMorph.supports(strain) && !firstPerson) {
                        SpiderMorph.Pose staged = SpiderMorph.reshape(bone, part, target, goal, gene.progress());
                        Vector3f pivot = staged.pivot();
                        FRAME.put(bone, staged.cover(), pivot.x, pivot.y, pivot.z, staged.shape(),
                                SpiderMorph.phase(gene.progress()) >= 0.64F);
                    } else {
                        reshape(bone, part, target, goal, g, t, time(player) + bone.ordinal() * 7.3F, torso);
                    }
                    if (bone == Strain.Bone.BODY) {
                        float[] pivot = FRAME.pivot(bone);
                        core = new Vector3f(pivot[0], pivot[1], pivot[2]);
                        Box shape = FRAME.shape(bone);
                        Vector3f center = shape == null ? new Vector3f(core)
                                : rotate(model.body, shape.center()).add(core);
                        torso = new Torso(torsoPivot, torsoRotation, Poses.pivot(goal),
                                Poses.rotation(goal).mul(target.align()), core, Poses.rotation(model.body), center);
                    }
                }
            }
        }

        BodyMotion.apply(model, player, gene);
        copyOverlays(model);
        if (config.bodyMorph) {
            shed(model, strain, gene.progress());
            if (ContinuousMorph.supports(strain) && config.hybridRender && !firstPerson) {
                for (Strain.Bone bone : Strain.Bone.values()) {
                    bone(model, bone).visible = false;
                    overlay(model, bone).visible = false;
                }
            }
        }
    }

    /**
     * Hide clothing halfway through a bone's transformation to prevent overlapping skin layers. Once the
     * creature part fully replaces the bone, hide the human geometry so gaps such as skeleton ribs remain
     * visible.
     */
    private static void shed(PlayerModel<?> model, Strain strain, float progress) {
        MorphPlan plan = MorphPlan.of(strain);
        boolean hybrid = MutagenConfig.get().hybridRender;
        for (Strain.Bone bone : Strain.Bone.values()) {
            float t = Infection.bonePhase(strain, bone, progress);
            boolean covered = hybrid && plan.target(bone) != null && FRAME.owner != Integer.MIN_VALUE;
            float cover = covered ? FRAME.phase(bone) : t;
            if (cover >= (covered ? CLOTHES_UNDER_CREATURE : CLOTHES_SHED)) {
                overlay(model, bone).visible = false;
            }
            if (covered && cover >= FULLY_TURNED) {
                bone(model, bone).visible = false;
            }
        }
    }

    /** Creature skin coverage follows both infection and morph progress. */
    private static float cover(float t, float g) {
        return Mth.clamp(Math.max(t, g) * COVER_LEAD, 0.0F, 1.0F);
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
     * Move the bone's pivot and rotation toward the matching creature part and stretch its bounds toward
     * the part's box.
     * <p>
     * Align the bone to long parts such as spider legs along X or bee bodies along Z using {@link
     * MorphPlan.Target#align}. Then blend their length and thickness so arms swing sideways into legs and
     * the torso tips horizontal.
     * <p>
     * Lower and shorten the bone first, move it forward or backward, and add sideways movement near the
     * end. Thickness changes before length. During its local infection phase ({@code t}), the bone swells
     * and shakes.
     */
    private static void reshape(Strain.Bone bone, ModelPart part, MorphPlan.Target target, Matrix4f goal, float g,
                                float t, float time, @Nullable Torso torso) {
        PartPose own = part.getInitialPose();
        Vector3f goalPivot = Poses.pivot(goal);
        Quaternionf goalRotation = Poses.rotation(goal).mul(target.align());
        Box goalBox = target.aligned().times(Poses.scale(goal));
        boolean becomesLeg = goalPivot.y - own.y > LEG_DROP;

        float travel = firstPerson ? 0.0F : 1.0F;
        float lengthPhase = window(g, 0.0F, 0.75F);
        float pivotX = part.x + (goalPivot.x - own.x) * window(g, 0.50F, 1.0F) * travel;
        float pivotY = part.y + (goalPivot.y - own.y) * lengthPhase * travel;
        float pivotZ = part.z + (goalPivot.z - own.z) * window(g, 0.35F, 1.0F) * travel;
        // Rotate limbs before lowering the torso. The spider's legs spread while the body is still high so
        // it can settle on them.
        boolean limb = bone != Strain.Bone.BODY && bone != Strain.Bone.HEAD;
        float turn = limb ? window(g, 0.0F, 0.6F) : window(g, 0.15F, 1.0F);

        float active = Mth.sin(g * Mth.PI) * (1.0F - window(g, 0.75F, 1.0F));
        Vector3f[] centerTorso = null;
        if (torso != null && !firstPerson) {
            // Interpolate the head and limbs' offsets from the torso, then transform those offsets by the
            // current torso pose so the shoulders and neck follow its tilt. Blend limb rotation in world
            // space so a human arm turning into a wolf foreleg can keep pointing down as the torso tilts.
            Quaternionf humanInverse = new Quaternionf(torso.humanRotation()).conjugate();
            Quaternionf creatureInverse = new Quaternionf(torso.creatureRotation()).conjugate();
            Vector3f human = humanInverse.transform(new Vector3f(part.x, part.y, part.z).sub(torso.humanPivot()));
            Vector3f creature = creatureInverse.transform(new Vector3f(goalPivot).sub(torso.creaturePivot()));
            Vector3f pivot = torso.rotation().transform(human.lerp(creature, window(g, 0.0F, 0.9F)))
                    .add(torso.pivot());
            pivotX = pivot.x;
            pivotY = pivot.y;
            pivotZ = pivot.z;

            Poses.setRotation(part, Poses.rotation(part).slerp(goalRotation, turn));
        } else if (!firstPerson) {
            // Blend the player pose toward the creature's current pose so the bone follows the creature's
            // gait at the end.
            Quaternionf before = Poses.rotation(part);
            Poses.setRotation(part, new Quaternionf(before).slerp(goalRotation, turn));
            if (bone == Strain.Bone.BODY) {
                centerTorso = new Vector3f[]{rotate(before, Box.of(part).center()).add(part.x, part.y, part.z),
                        goalRotation.transform(goalBox.center()).add(goalPivot)};
            }
        }
        part.xRot += Mth.sin(time * 1.9F) * TREMOR * active;
        part.zRot += Mth.sin(time * 2.3F + 1.1F) * TREMOR * active;

        float thickness = window(g, 0.0F, 0.70F);
        float swell = 1.0F + SWELL * active;
        // Change attachment height and bone length together to keep the feet from passing through the
        // ground midway.
        Box shape = Box.of(part).lerp(goalBox, thickness, lengthPhase, thickness)
                .scaled(swell, 1.0F + SWELL * 0.4F * active, swell);
        if (centerTorso != null) {
            // Rotate the torso about its center. The shoulders move forward and down while the hips move
            // back.
            Vector3f center = centerTorso[0].lerp(centerTorso[1], window(g, 0.0F, 1.0F));
            Vector3f pivot = center.sub(rotate(part, shape.center()));
            pivotX = pivot.x;
            pivotY = pivot.y;
            pivotZ = pivot.z;
        }
        Box.Fit fit = Box.of(part).fit(shape);
        part.xScale = fit.scale().x();
        part.yScale = fit.scale().y();
        part.zScale = fit.scale().z();
        Vector3f shift = rotate(part, fit.offset());
        part.x = pivotX + shift.x;
        part.y = pivotY + shift.y;
        part.z = pivotZ + shift.z;

        FRAME.put(bone, cover(t, g), pivotX, pivotY, pivotZ, shape, becomesLeg && g >= 0.5F);
    }

    /**
     * Torso poses for this frame before transformation, at the creature target, and at current progress.
     * Pivots use model pixels and rotations use bone coordinates.
     */
    private record Torso(Vector3f humanPivot, Quaternionf humanRotation, Vector3f creaturePivot,
                         Quaternionf creatureRotation, Vector3f pivot, Quaternionf rotation, Vector3f center) {
    }

    /** Portion of the transformation in [from, to] with a smooth start and end. */
    private static float window(float t, float from, float to) {
        float x = Mth.clamp((t - from) / (to - from), 0.0F, 1.0F);
        return x * x * (3.0F - 2.0F * x);
    }

    private static float time(Player player) {
        return (player.tickCount + net.minecraft.client.Minecraft.getInstance().getTimer()
                .getGameTimeDeltaPartialTick(true)) * 0.35F;
    }

    /**
     * Bones without counterparts shrink into the torso while following its tilt. This keeps the human head
     * from floating above the bee abdomen as the body lowers.
     */
    private static void absorb(ModelPart part, float g, @Nullable Torso torso) {
        float scale = Math.max(0.001F, 1.0F - g);
        part.xScale = scale;
        part.yScale = scale;
        part.zScale = scale;
        if (torso == null || firstPerson) {
            return;
        }
        Quaternionf humanInverse = new Quaternionf(torso.humanRotation()).conjugate();
        // Keep the bone attached to the torso while retracting it toward the center.
        Vector3f relative = humanInverse.transform(new Vector3f(part.x, part.y, part.z).sub(torso.humanPivot()));
        Vector3f pivot = torso.rotation().transform(relative).add(torso.pivot())
                .lerp(torso.center(), window(g, 0.1F, 1.0F));
        part.x = pivot.x;
        part.y = pivot.y;
        part.z = pivot.z;
        Quaternionf rotation = humanInverse.mul(Poses.rotation(part), new Quaternionf());
        Poses.setRotation(part, new Quaternionf(torso.rotation()).mul(rotation));
    }

    /** Bone rotation in degrees from the strain definition, such as the zombie's outstretched arms. */
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

    /** Convert a part-local offset to a pivot offset in parent coordinates. */
    public static Vector3f rotate(ModelPart part, Vector3f local) {
        return new Quaternionf().rotationZYX(part.zRot, part.yRot, part.xRot).transform(new Vector3f(local));
    }

    private static Vector3f rotate(Quaternionf rotation, Vector3f local) {
        return rotation.transform(new Vector3f(local));
    }

    /** Update the clothing layers because vanilla copies the bones before the strain changes them. */
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

    /** Most recent shape calculated for this player, if available. */
    @Nullable
    public static Frame frame(Player player) {
        return FRAME.owner == player.getId() ? FRAME : null;
    }

    /**
     * Frame result with each bone's phase, pivot before the box offset, and current bounds. Fit the
     * creature part into those same bounds so the surfaces align.
     */
    public static final class Frame {
        private int owner = Integer.MIN_VALUE;
        private final float[] phase = new float[Strain.Bone.values().length];
        private final float[][] pivot = new float[Strain.Bone.values().length][3];
        private final Box[] shape = new Box[Strain.Bone.values().length];
        private final boolean[] leg = new boolean[Strain.Bone.values().length];
        /** Target pose of the creature part for this frame, in blocks and in player model coordinates. */
        private final Matrix4f[] goal = new Matrix4f[Strain.Bone.values().length];
        /** Poses of the grown parts in {@link MorphPlan#grown()} order. */
        private final List<Matrix4f> grown = new ArrayList<>();

        private void clear(int entity) {
            owner = entity;
            java.util.Arrays.fill(phase, 0.0F);
            java.util.Arrays.fill(shape, null);
            java.util.Arrays.fill(leg, false);
            java.util.Arrays.fill(goal, null);
            grown.clear();
        }

        @Nullable
        public Matrix4f goal(Strain.Bone bone) {
            return goal[bone.ordinal()];
        }

        @Nullable
        public Matrix4f grown(int index) {
            return index < grown.size() ? grown.get(index) : null;
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

        /** Once the arm has become mostly a leg, skip rendering any item held in it. */
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
