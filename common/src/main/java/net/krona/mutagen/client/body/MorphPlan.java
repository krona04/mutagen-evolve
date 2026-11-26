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
 * План превращения для штамма: какая кость игрока в какую часть существа переходит, что у существа
 * вырастает сверх человеческого и какие кости втягиваются.
 * <p>
 * Модель существа запекается из её собственного ванильного слоя, поэтому годится любой моб. И она
 * живая: каждый кадр её двигает собственная анимация существа ({@link #animate}) с походкой, поворотом
 * головы и возрастом игрока. Кости игрока тянутся к части существа в той позе, в какой она сейчас
 * у существа, — а не в позе покоя из файла модели, где у паука, например, ноги лежат горизонтально.
 * Поэтому к концу превращения мутант уже ходит, как существо, и переход в полную форму незаметен.
 * <p>
 * Если в штамме нет списка частей, кости сопоставляются по именам: {@code head}, {@code body},
 * {@code right_arm} и так далее — так устроены все человекоподобные мобы.
 */
@Environment(EnvType.CLIENT)
public final class MorphPlan {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, Optional<ModelPart>> ROOTS = new HashMap<>();
    private static final Map<Strain, MorphPlan> PLANS = new WeakHashMap<>();
    /** Во сколько раз длинная ось части должна превосходить остальные, чтобы кость разворачивалась вдоль неё. */
    private static final float LONG_AXIS = 1.4F;
    /** Ваниль сдвигает любую модель на эту высоту перед отрисовкой: земля модели на y = 24. */
    private static final float MODEL_ORIGIN = 1.501F;

    /**
     * Кость игрока → часть существа, которой она становится.
     *
     * @param chain   путь от корня модели до части включительно: по нему считается её поза в теле
     * @param box     габариты части в её собственных координатах, без отдельно ведомых потомков
     * @param align   прямой поворот, после которого длинная ось части смотрит туда же, куда кость игрока
     *                (вниз по её длине). Лапа паука вытянута вдоль X, рука — вдоль Y: без этого рука
     *                на полпути превращалась бы в бесформенный брусок, а не поворачивалась бы лапой вбок
     * @param aligned та же коробка в координатах кости: {@code align⁻¹(box)}
     * @param hide    потомки, которых превращение ведёт отдельно: при отрисовке части их прячут
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
    /** Поправка рендера существа относительно рендера игрока, в блоках. */
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
                    // Сложенные руки жителей, ведьмы и разбойников — одна часть на обе руки.
                    auto.add(new Strain.Part("arms", bone));
                }
            }
            return auto;
        });

        // Сначала пути: коробки считаются без потомков, которых ведёт своя запись плана.
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
            // Та же часть в дополнительных слоях существа (одежда утопленника, глаза паука).
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
     * Ставит модель существа в позу этого кадра: та же походка, поворот головы и возраст, что у игрока.
     * Модели, которую не удалось оживить, остаётся поза покоя — превращение всё равно работает.
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
     * Своя копия модели существа поверх запечённого здесь корня: тот же класс, что у его рендера,
     * но части — наши, и анимация двигает именно их. Заодно снимается поправка рендера.
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
     * Модель существа и модель игрока рисуются каждая со своим масштабом и сдвигом ({@code scale()}
     * их рендеров): игрок чуть меньше своих координат, фантом — ниже своих. Поправка переводит позу
     * части существа в координаты модели игрока так, чтобы на экране она оказалась там же, где её
     * нарисует настоящий рендер существа.
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

    /** Поза части в теле, в блоках, в координатах модели игрока. Считать после {@link #animate}. */
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
     * Длинная ось части → куда её развернуть, чтобы она легла вдоль кости игрока. Конечность растёт
     * от точки поворота туда, где часть длиннее. Корпус, вытянутый вдоль Z, ложится иначе: верх
     * туловища уходит вперёд, к голове существа (модели смотрят в −Z), — по коробке этого не понять,
     * усики пчелы тянут её вперёд сильнее, чем жало назад.
     */
    private static Quaternionf alignment(Box box, Strain.Bone bone) {
        float sx = box.maxX() - box.minX();
        float sy = box.maxY() - box.minY();
        float sz = box.maxZ() - box.minZ();
        float half = (float) (Math.PI / 2.0D);
        if (sx >= LONG_AXIS * Math.max(sy, sz)) {
            // Кость растёт вниз по +Y; поворот вокруг Z на −90° уводит +Y в +X.
            boolean positive = Math.abs(box.maxX()) >= Math.abs(box.minX());
            return new Quaternionf().rotationZ(positive ? -half : half);
        }
        if (sz >= LONG_AXIS * Math.max(sx, sy)) {
            boolean positive = bone == Strain.Bone.BODY || Math.abs(box.maxZ()) >= Math.abs(box.minZ());
            return new Quaternionf().rotationX(positive ? half : -half);
        }
        if (sy >= LONG_AXIS * Math.max(sx, sz) && Math.abs(box.minY()) > Math.abs(box.maxY())) {
            // Вытянута вверх от точки поворота — кость переворачивается.
            return new Quaternionf().rotationX((float) Math.PI);
        }
        return new Quaternionf();
    }

    /** Путь вида {@code "head"} или {@code "body/tail"}: части от первой после корня до искомой. */
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
     * Корень модели существа. Запекается один раз: геометрия слоёв задаётся кодом и не меняется
     * при перезагрузке ресурсов. Если слоя нет, штамм меняет только кожу, но не форму.
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
