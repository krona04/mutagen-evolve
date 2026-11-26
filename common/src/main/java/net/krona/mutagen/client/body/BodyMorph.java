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
 * Превращение формы тела: кости игрока сами становятся частями существа.
 * <p>
 * Каждая кость, у которой есть пара в модели существа ({@link MorphPlan}), плавно принимает её размеры,
 * место в теле и наклон — в той позе, в какой часть сейчас у живого существа, с его походкой. Всё
 * считается в общих координатах модели, где у любого моба земля на одной высоте, поэтому тело не парит
 * и не проваливается: паук опускается на лапы, волк встаёт на четвереньки, эндермен вытягивается.
 * <p>
 * Скелет перестраивается целиком: силуэт идёт за общим прогрессом, а кость, в которую зараза вошла
 * раньше, лишь немного его опережает. Кожа существа, наоборот, расходится кость за костью. Кости,
 * которых у существа нет, втягиваются в тело. Поверх формы {@link BodyMotion} добавляет судороги.
 * <p>
 * Модель игрока одна на всех, поэтому перед ванильной анимацией кости возвращаются в исходную позу
 * ({@link #resetPose}) — иначе добавки копились бы от кадра к кадру и тело начинало бы вращаться.
 * Итог кадра запоминается ({@link Frame}): по нему части существа ложатся на ту же форму.
 */
@Environment(EnvType.CLIENT)
public final class BodyMorph {
    private static final Frame FRAME = new Frame();
    /**
     * Сейчас рисуется рука от первого лица. Там рука привязана к камере, а не к телу: форму она меняет,
     * но с плеча не уходит — иначе рука, становящаяся ногой крипера, всплывала бы посреди экрана.
     */
    private static boolean firstPerson;
    /** Кость, которую превращение опускает ниже этого (в пикселях модели), становится ногой. */
    private static final float LEG_DROP = 8.0F;
    /** Насколько кость набухает в разгар перестройки. */
    private static final float SWELL = 0.12F;
    /** Амплитуда дрожи перестраивающейся кости, в радианах. */
    private static final float TREMOR = 0.035F;

    /** С какой фазы кости с неё спадает одежда, если части существа на ней нет. */
    private static final float CLOTHES_SHED = 0.5F;
    /**
     * С какого покрытия спадает одежда с кости, на которой проступает часть существа. Второй слой скина
     * (волосы, куртка) выступает над костью и иначе закрывал бы шкуру существа — голова паука пряталась
     * бы под причёской игрока.
     */
    private static final float CLOTHES_UNDER_CREATURE = 0.08F;
    /**
     * Насколько шкура существа опережает форму. Покрытие кости — наибольшее из её заражения и её формы,
     * с этим множителем: когда кость на три четверти приняла форму части существа, она уже целиком в его
     * шкуре, а не в коже игрока, натянутой на чужую форму.
     */
    private static final float COVER_LEAD = 1.35F;
    /** С какой фазы кость игрока полностью уступает место части существа. */
    private static final float FULLY_TURNED = MorphSurface.REPLACED;

    private BodyMorph() {
    }

    /** Перед ванильной анимацией: исходная поза и масштаб для всех костей. */
    public static void resetPose(PlayerModel<?> model) {
        for (ModelPart part : bones(model)) {
            part.resetPose();
            part.xScale = 1.0F;
            part.yScale = 1.0F;
            part.zScale = 1.0F;
        }
    }

    /** Насколько силуэт следует за общим прогрессом, а не за заражением отдельной кости. */

    /** Порядок перестройки: сначала корпус — втягивающиеся кости уходят к нему. */
    private static final Strain.Bone[] ORDER = {Strain.Bone.BODY, Strain.Bone.HEAD, Strain.Bone.RIGHT_ARM,
            Strain.Bone.LEFT_ARM, Strain.Bone.RIGHT_LEG, Strain.Bone.LEFT_LEG};

    /** После ванильной анимации: форма, поза и движения мутанта. Параметры — те же, что у анимации игрока. */
    public static void apply(PlayerModel<?> model, LivingEntity entity, float limbSwing, float limbSwingAmount,
                             float ageInTicks, float netHeadYaw, float headPitch) {
        // Модель пиглина — наследница модели игрока, и её анимацию, запущенную для превращения,
        // этот хук тоже видит. Манекен существа — не игрок: кадр игрока он трогать не должен.
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
            // Живое существо в позе этого кадра: к нему тянутся кости, на нём держатся выросшие части.
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
            // Корпус до перестройки: от него считается, где у человека плечи, шея и бёдра.
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
     * Одежда (второй слой скина) спадает с кости, когда та превратилась наполовину: дальше на ней шкура
     * существа, и два слоя кожи друг над другом выглядели бы как ошибка. Кость, превратившаяся до конца,
     * прячется целиком — остаётся часть существа, со всеми её просветами, как между рёбрами скелета.
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

    /** Покрытие кости шкурой существа: не отстаёт ни от заражения, ни от формы. */
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
     * Кость принимает форму части существа: точка поворота идёт к её месту в теле, наклон — к её
     * наклону в этом кадре, а коробка кости растягивается к её коробке.
     * <p>
     * Длинная часть, лежащая поперёк кости (лапа паука вдоль X, тело пчелы вдоль Z), не сплющивается
     * в брусок: кость сперва разворачивается вдоль неё ({@link MorphPlan.Target#align}), и дальше
     * меняется только длина и толщина. Поэтому рука уходит в сторону лапой, а корпус ложится горизонтально.
     * <p>
     * Как у живого тела, всё идёт не разом и не по прямой. Кость сперва опускается вместе с укорачиванием,
     * затем уходит вперёд или назад и лишь под конец подтягивается вбок. Толщина меняется раньше длины.
     * Пока кость перестраивается ({@code t} — её собственное заражение), она набухает и мелко дрожит.
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
        // Конечности разворачиваются раньше, чем опускается корпус: лапы паука расходятся в стороны,
        // пока тело ещё высоко, и тело садится на них, а не стоит на прямых ногах, как на ходулях.
        boolean limb = bone != Strain.Bone.BODY && bone != Strain.Bone.HEAD;
        float turn = limb ? window(g, 0.0F, 0.6F) : window(g, 0.15F, 1.0F);

        float active = Mth.sin(g * Mth.PI) * (1.0F - window(g, 0.75F, 1.0F));
        Vector3f[] centerTorso = null;
        if (torso != null && !firstPerson) {
            // Голова, руки и ноги держатся за корпус: где они у человека относительно него и где
            // у существа — смешивается, а потом переносится на корпус в его нынешней позе. Наклонился
            // корпус — плечи и шея ушли вместе с ним, а не остались висеть на прежнем месте.
            // Наклон же конечности смешивается как есть, в мире: рука и у человека висит к земле,
            // и передняя лапа волка смотрит в землю — пока корпус ложится, рука так и тянется вниз,
            // а не падает вперёд вместе с ним.
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
            // От позы игрока — к позе существа в этом кадре: к концу кость шагает его походкой.
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
        // Высота крепления и длина кости меняются вместе: иначе ступни на середине пути уходили бы под землю.
        Box shape = Box.of(part).lerp(goalBox, thickness, lengthPhase, thickness)
                .scaled(swell, 1.0F + SWELL * 0.4F * active, swell);
        if (centerTorso != null) {
            // Корпус поворачивается вокруг своей середины, а не вокруг шеи: плечи уходят вперёд и вниз,
            // таз — назад, как у человека, который сгибается, а не у доски, откинутой на петле.
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
     * Корпус в этом кадре: у человека до перестройки, у существа в цели и сейчас. Точки поворота —
     * в пикселях модели, повороты — в координатах кости.
     */
    private record Torso(Vector3f humanPivot, Quaternionf humanRotation, Vector3f creaturePivot,
                         Quaternionf creatureRotation, Vector3f pivot, Quaternionf rotation, Vector3f center) {
    }

    /** Часть превращения, пришедшаяся на окно [from, to], со сглаженным началом и концом. */
    private static float window(float t, float from, float to) {
        float x = Mth.clamp((t - from) / (to - from), 0.0F, 1.0F);
        return x * x * (3.0F - 2.0F * x);
    }

    private static float time(Player player) {
        return (player.tickCount + net.minecraft.client.Minecraft.getInstance().getTimer()
                .getGameTimeDeltaPartialTick(true)) * 0.35F;
    }

    /**
     * Кости, которых у существа нет, усыхают и уходят в тело. Они держатся за корпус: наклонился он —
     * наклонились и они, и с каждым шагом сидят к нему ближе, пока не скроются в нём. Голова пчелы
     * не повисает в воздухе над опустившимся брюшком, а втягивается в него.
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
        // Кость держится за корпус и одновременно уходит в его середину.
        Vector3f relative = humanInverse.transform(new Vector3f(part.x, part.y, part.z).sub(torso.humanPivot()));
        Vector3f pivot = torso.rotation().transform(relative).add(torso.pivot())
                .lerp(torso.center(), window(g, 0.1F, 1.0F));
        part.x = pivot.x;
        part.y = pivot.y;
        part.z = pivot.z;
        Quaternionf rotation = humanInverse.mul(Poses.rotation(part), new Quaternionf());
        Poses.setRotation(part, new Quaternionf(torso.rotation()).mul(rotation));
    }

    /** Поза из данных штамма: поворот кости в градусах, например вытянутые вперёд руки зомби. */
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

    /** Сдвиг в координатах части → сдвиг точки поворота в координатах родителя. */
    public static Vector3f rotate(ModelPart part, Vector3f local) {
        return new Quaternionf().rotationZYX(part.zRot, part.yRot, part.xRot).transform(new Vector3f(local));
    }

    private static Vector3f rotate(Quaternionf rotation, Vector3f local) {
        return rotation.transform(new Vector3f(local));
    }

    /** Слои одежды повторяют кости: ваниль копирует их раньше, чем штамм успевает кость изменить. */
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

    /** Форма последнего кадра этого игрока, если она уже посчитана. */
    @Nullable
    public static Frame frame(Player player) {
        return FRAME.owner == player.getId() ? FRAME : null;
    }

    /**
     * Итог превращения за кадр: для каждой кости — фаза, точка поворота до сдвига коробки и коробка,
     * которую кость сейчас занимает. Часть существа вписывается в ту же коробку, и поверхности совпадают.
     */
    public static final class Frame {
        private int owner = Integer.MIN_VALUE;
        private final float[] phase = new float[Strain.Bone.values().length];
        private final float[][] pivot = new float[Strain.Bone.values().length][3];
        private final Box[] shape = new Box[Strain.Bone.values().length];
        private final boolean[] leg = new boolean[Strain.Bone.values().length];
        /** Поза части существа в этом кадре — куда тянется кость. В блоках, в координатах модели игрока. */
        private final Matrix4f[] goal = new Matrix4f[Strain.Bone.values().length];
        /** Позы выросших частей в этом кадре, по порядку {@link MorphPlan#grown()}. */
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

        /** Рука уже больше нога, чем рука: предмет в ней не удержать и не нарисовать. */
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
