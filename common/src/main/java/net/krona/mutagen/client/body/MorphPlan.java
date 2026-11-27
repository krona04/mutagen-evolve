package net.krona.mutagen.client.body;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.krona.mutagen.client.FullFormRenderer;
import net.krona.mutagen.mixin.client.LivingEntityRendererInvoker;
import net.krona.mutagen.mixin.client.ModelPartAccessor;
import net.krona.mutagen.strain.Strain;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Strain transformation plan mapping player bones, growing extra creature parts, and retracting unmatched
 * bones.
 * <p>
 * Bake the creature's own vanilla model layer. Animate it every frame with the player's gait, head
 * rotation, and age via {@link #animate}. Target the animated pose so the player's gait matches the
 * creature before the switch to full form.
 * <p>
 * Without an explicit mapping, match humanoid part names such as {@code head}, {@code body}, and {@code
 * right_arm}.
 */
@Environment(EnvType.CLIENT)
public final class MorphPlan {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, Optional<ModelPart>> ROOTS = new HashMap<>();
    private static final Map<Strain, MorphPlan> PLANS = new WeakHashMap<>();
    /** Minimum ratio of a part's longest axis to the others before aligning the bone to it. */
    private static final float LONG_AXIS = 1.4F;
    /** Vanilla rendering offsets the model so its ground level is at y = 24. */
    private static final float MODEL_ORIGIN = 1.501F;

    /**
     * Mapping from a player bone to its target creature part.
     *
     * @param chain path from the model root through the part, used to calculate its pose
     * @param box bounds in the part's local coordinates, excluding descendants handled separately
     * @param align quarter-turn that aligns the part's long axis with the length of the player bone. A
     * spider leg lies along X and the arm lies along Y, so rotate instead of collapsing the arm to a box
     * during the transition
     * @param aligned the same bounds in bone coordinates, given by {@code align⁻¹(box)}
     * @param hide descendants handled by separate morph entries and hidden when rendering this part
     */
    public record Target(int index, Strain.Part part, ModelPart model, List<ModelPart> chain, Box box,
                         Quaternionf align, Box aligned, List<ModelPart> hide, List<Optional<ModelPart>> overlays) {
    }

    private final Strain strain;
    @Nullable
    private final ModelPart root;
    private final Target[] targets = new Target[Strain.Bone.values().length];
    private final List<Target> grown = new ArrayList<>();
    private final Set<Strain.Bone> absorbed = EnumSet.noneOf(Strain.Bone.class);

    @Nullable
    private EntityModel<LivingEntity> animator;
    private boolean animatorTried;
    /** Difference between creature and player renderer transforms, in blocks. */
    private Matrix4f correction = new Matrix4f();

    private MorphPlan(Strain strain, @Nullable ModelPart root) {
        this.strain = strain;
        this.root = root;
    }

    public static MorphPlan of(Strain strain) {
        return PLANS.computeIfAbsent(strain, MorphPlan::build);
    }

    private static MorphPlan build(Strain strain) {
        ModelPart root = root(strain);
        MorphPlan plan = new MorphPlan(strain, root);
        plan.absorbed.addAll(strain.body().absorb());
        if (root == null) {
            return plan;
        }
        List<Strain.Part> parts = strain.body().parts().orElseGet(() -> {
            List<Strain.Part> auto = new ArrayList<>();
            for (Strain.Bone bone : Strain.Bone.values()) {
                if (root.hasChild(bone.key())) {
                    auto.add(new Strain.Part(bone.key(), bone));
                } else if ((bone == Strain.Bone.RIGHT_ARM || bone == Strain.Bone.LEFT_ARM) && root.hasChild("arms")) {
                    // Villagers, witches, and illagers share one model part for both crossed arms.
                    auto.add(new Strain.Part("arms", bone));
                }
            }
            return auto;
        });

        // Resolve the paths first so bounds exclude descendants with their own plan entries.
        List<List<ModelPart>> chains = new ArrayList<>();
        Set<ModelPart> planned = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Strain.Part part : parts) {
            List<ModelPart> chain = chain(root, part.part());
            chains.add(chain);
            if (chain == null) {
                LOGGER.warn("[Mutagen] Strain {}: no part '{}' in model {}", strain.id(), part.part(),
                        strain.modelLayer());
            } else {
                planned.add(chain.get(chain.size() - 1));
            }
        }

        for (int i = 0; i < parts.size(); i++) {
            List<ModelPart> chain = chains.get(i);
            if (chain == null) {
                continue;
            }
            Strain.Part part = parts.get(i);
            ModelPart model = chain.get(chain.size() - 1);
            Set<ModelPart> hide = Collections.newSetFromMap(new IdentityHashMap<>());
            descendants(model, planned, hide);
            Box box = Box.of(model, hide).scaled(part.scale().x(), part.scale().y(), part.scale().z());
            Quaternionf align = alignment(box, part.bone());
            Box aligned = box.rotated(new Quaternionf(align).conjugate());
            // The same part in extra creature layers such as drowned clothing or spider eyes.
            List<Optional<ModelPart>> overlays = new ArrayList<>();
            for (Strain.Overlay overlay : strain.body().overlays()) {
                ModelPart layer = root(overlay.modelLayer().orElse(strain.modelLayer()), strain);
                List<ModelPart> same = layer == null ? null : chain(layer, part.part());
                overlays.add(same == null ? Optional.empty() : Optional.of(same.get(same.size() - 1)));
            }
            Target target = new Target(i, part, model, chain, box, align, aligned, List.copyOf(hide),
                    List.copyOf(overlays));
            if (part.grow()) {
                plan.grown.add(target);
            } else if (!plan.absorbed.contains(part.bone())) {
                plan.targets[part.bone().ordinal()] = target;
            }
        }
        return plan;
    }

    @Nullable
    public Target target(Strain.Bone bone) {
        return targets[bone.ordinal()];
    }

    public List<Target> grown() {
        return grown;
    }

    public boolean absorbed(Strain.Bone bone) {
        return absorbed.contains(bone);
    }

    /**
     * Apply the player's gait, head rotation, and age to the creature model for this frame. If animation is
     * unavailable, use its rest pose.
     */
    @SuppressWarnings("unchecked")
    public void animate(Player player, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
                        float headPitch) {
        if (root == null) {
            return;
        }
        root.getAllParts().forEach(part -> {
            part.resetPose();
            part.xScale = 1.0F;
            part.yScale = 1.0F;
            part.zScale = 1.0F;
            part.visible = true;
        });

        LivingEntity dummy = FullFormRenderer.dummy(strain.entityType(), player.level());
        if (dummy == null) {
            return;
        }
        if (!animatorTried) {
            animatorTried = true;
            prepare(player, dummy);
        }
        if (animator == null) {
            return;
        }
        float partialTick = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
        FullFormRenderer.copyState(player, dummy);
        try {
            animator.attackTime = player.getAttackAnim(partialTick);
            animator.riding = false;
            animator.young = false;
            float walk = dummy.walkAnimation.position(partialTick);
            float stride = dummy.walkAnimation.speed(partialTick);
            animator.prepareMobModel(dummy, walk, stride, partialTick);
            EntityRenderer<?> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(dummy);
            float animationParameter = animationParameter(renderer instanceof LivingEntityRenderer<?, ?> living
                    ? (LivingEntityRendererInvoker) living : null, dummy, partialTick, ageInTicks);
            float headYaw = Mth.wrapDegrees(Mth.rotLerp(partialTick, dummy.yHeadRotO, dummy.yHeadRot)
                    - Mth.rotLerp(partialTick, dummy.yBodyRotO, dummy.yBodyRot));
            float pitch = Mth.lerp(partialTick, dummy.xRotO, dummy.getXRot());
            animator.setupAnim(dummy, walk, stride, animationParameter, headYaw, pitch);
            if (dummy instanceof net.minecraft.world.entity.monster.Phantom) {
                // The hybrid renderer doesn't call PhantomRenderer.setupRotations. In model space
                // the inverted Y axis requires the opposite sign to its world-space rotation.
                root.getChild("body").xRot -= pitch * Mth.DEG_TO_RAD;
            }
        } catch (RuntimeException e) {
            LOGGER.warn("[Mutagen] Strain {}: the creature's animation failed, using its rest pose", strain.id(), e);
            animator = null;
            root.getAllParts().forEach(ModelPart::resetPose);
        }
    }

    public static float animationParameter(@Nullable LivingEntityRendererInvoker renderer, LivingEntity entity,
                                            float partialTick, float age) {
        return renderer == null ? age : renderer.mutagen$getBob(entity, partialTick);
    }

    /**
     * Instantiate the renderer's model class with our baked model root so its animation updates our parts.
     * Also calculate the renderer's transform.
     */
    @SuppressWarnings("unchecked")
    private void prepare(Player player, LivingEntity dummy) {
        EntityRenderer<?> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(dummy);
        if (!(renderer instanceof LivingEntityRenderer<?, ?> living)) {
            return;
        }
        try {
            animator = (EntityModel<LivingEntity>) living.getModel().getClass()
                    .getConstructor(ModelPart.class).newInstance(root);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.info("[Mutagen] Strain {}: model {} cannot be animated separately, using its rest pose",
                    strain.id(), living.getModel().getClass().getSimpleName());
            animator = null;
        }
        correction = correction(player, dummy, living);
    }

    /**
     * Creature and player renderers apply their own scales and offsets through {@code scale()}. Convert the
     * creature part pose into player model coordinates so it lines up with the native creature rendering.
     */
    private static Matrix4f correction(Player player, LivingEntity dummy, LivingEntityRenderer<?, ?> mobRenderer) {
        try {
            float partialTick = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
            FullFormRenderer.copyState(player, dummy);
            PoseStack mob = new PoseStack();
            ((LivingEntityRendererInvoker) mobRenderer).mutagen$scale(dummy, mob, partialTick);
            PoseStack own = new PoseStack();
            EntityRenderer<?> playerRenderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(player);
            if (playerRenderer instanceof LivingEntityRenderer<?, ?> living) {
                ((LivingEntityRendererInvoker) living).mutagen$scale(player, own, partialTick);
            }
            Matrix4f result = new Matrix4f().translate(0.0F, MODEL_ORIGIN, 0.0F);
            result.mul(own.last().pose().invert(new Matrix4f()));
            result.mul(mob.last().pose());
            result.translate(0.0F, -MODEL_ORIGIN, 0.0F);
            return result;
        } catch (RuntimeException e) {
            return new Matrix4f();
        }
    }

    /** Part pose in player model coordinates and in blocks. Calculate after {@link #animate}. */
    public Matrix4f pose(Target target) {
        Matrix4f matrix = new Matrix4f(correction);
        if (root != null) {
            Poses.apply(matrix, root);
        }
        for (ModelPart part : target.chain()) {
            Poses.apply(matrix, part);
        }
        return matrix;
    }

    /**
     * Align the part's long axis with the player bone. Limbs extend from the pivot toward the longer side.
     * A torso along Z instead tips its upper end forward toward the head, along −Z. Its bounds alone are
     * insufficient because features such as bee antennae skew them.
     */
    private static Quaternionf alignment(Box box, Strain.Bone bone) {
        float sx = box.maxX() - box.minX();
        float sy = box.maxY() - box.minY();
        float sz = box.maxZ() - box.minZ();
        float half = (float) (Math.PI / 2.0D);
        if (sx >= LONG_AXIS * Math.max(sy, sz)) {
            // The bone extends downward along +Y. A −90° rotation about Z maps +Y to +X.
            boolean positive = Math.abs(box.maxX()) >= Math.abs(box.minX());
            return new Quaternionf().rotationZ(positive ? -half : half);
        }
        if (sz >= LONG_AXIS * Math.max(sx, sy)) {
            boolean positive = bone == Strain.Bone.BODY || Math.abs(box.maxZ()) >= Math.abs(box.minZ());
            return new Quaternionf().rotationX(positive ? half : -half);
        }
        if (sy >= LONG_AXIS * Math.max(sx, sz) && Math.abs(box.minY()) > Math.abs(box.maxY())) {
            // Flip the bone if the part extends upward from the pivot.
            return new Quaternionf().rotationX((float) Math.PI);
        }
        return new Quaternionf();
    }

    /** Part path from the first child of the root, such as {@code "head"} or {@code "body/tail"}. */
    @Nullable
    private static List<ModelPart> chain(ModelPart root, String path) {
        List<ModelPart> chain = new ArrayList<>();
        ModelPart current = root;
        for (String name : path.split("/")) {
            if (!current.hasChild(name)) {
                return null;
            }
            current = current.getChild(name);
            chain.add(current);
        }
        return chain;
    }

    private static void descendants(ModelPart part, Set<ModelPart> planned, Set<ModelPart> out) {
        for (ModelPart child : ((ModelPartAccessor) (Object) part).mutagen$getChildren().values()) {
            if (planned.contains(child)) {
                out.add(child);
            } else {
                descendants(child, planned, out);
            }
        }
    }

    /**
     * Bake the creature model root once because resource reloads do not change the layer geometry. If the
     * layer is unavailable, the strain changes only the skin.
     */
    @Nullable
    private static ModelPart root(Strain strain) {
        return root(strain.modelLayer(), strain);
    }

    @Nullable
    private static ModelPart root(String layer, Strain strain) {
        return ROOTS.computeIfAbsent(layer, key -> {
            try {
                int split = key.indexOf('#');
                ResourceLocation model = ResourceLocation.parse(split < 0 ? key : key.substring(0, split));
                String name = split < 0 ? "main" : key.substring(split + 1);
                return Optional.of(Minecraft.getInstance().getEntityModels()
                        .bakeLayer(new ModelLayerLocation(model, name)));
            } catch (Exception e) {
                LOGGER.warn("[Mutagen] No model layer {} for strain {}: the body will change skin, not shape",
                        key, strain.id());
                return Optional.empty();
            }
        }).orElse(null);
    }
}
