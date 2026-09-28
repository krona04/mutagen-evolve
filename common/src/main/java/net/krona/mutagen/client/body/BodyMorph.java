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
 * Превращение формы тела: кости игрока сами становятся частями существа.
 * <p>
 * Каждая кость, у которой есть пара в модели существа ({@link MorphPlan}), по мере своего заражения
 * плавно принимает её размеры, положение в теле и исходный наклон. Всё считается в общих координатах
 * модели, где у любого моба земля на одной высоте, поэтому тело не парит и не проваливается:
 * у крипера ноги укорачиваются, а корпус и голова вместе с этим опускаются к ним. Кости, которых
 * у существа нет, втягиваются в тело. Поверх формы {@link BodyMotion} добавляет судороги и подёргивания.
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

    /** С какой фазы кости с неё спадает одежда. */
    private static final float CLOTHES_SHED = 0.5F;
    /** С какой фазы кость игрока полностью уступает место части существа. */
    private static final float FULLY_TURNED = 0.98F;

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

    /** После ванильной анимации: форма, поза и движения мутанта. */
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
            // Шаг ног до любых правок: рука, которая становится ногой, перенимает его у ноги по диагонали.
            float[] rightStep = {model.rightLeg.xRot, model.rightLeg.yRot, model.rightLeg.zRot};
            float[] leftStep = {model.leftLeg.xRot, model.leftLeg.yRot, model.leftLeg.zRot};
            // Сначала поза: сдвиг формы считается уже от нового наклона кости.
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
     * Одежда (второй слой скина) спадает с кости, когда та превратилась наполовину: дальше на ней шкура
     * существа, и два слоя кожи друг над другом выглядели бы как ошибка. Кость, превратившаяся до конца,
     * прячется целиком — остаётся часть существа, со всеми её просветами, как между рёбрами скелета.
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
     * Кость принимает форму части существа: точка поворота идёт к её месту в теле, наклон — к её
     * исходному наклону, а коробка кости растягивается и сдвигается к её коробке.
     * <p>
     * Как у живого тела, всё это идёт не разом и не по прямой. Конечность сперва выходит вперёд,
     * затем опускается и лишь под конец подтягивается вбок — так рука крипера становится передней ногой,
     * огибая грудь, а не проходя сквозь неё. Толщина кости меняется раньше длины. Пока кость
     * перестраивается, она набухает и мелко дрожит, а к концу успокаивается.
     */
    private static void reshape(Strain.Bone bone, ModelPart part, MorphPlan.Target target, float t, float time,
                                float[] step) {
        PartPose own = part.getInitialPose();
        PartPose goal = target.pose();
        boolean becomesLeg = goal.y - own.y > LEG_DROP;
        if (becomesLeg && step != null) {
            // Рука, ставшая ногой, больше не машет как рука и не держит позу хвата: она шагает,
            // в пару с ногой по диагонали, как ходят четвероногие.
            float gait = window(t, 0.15F, 0.70F);
            part.xRot = Mth.lerp(gait, part.xRot, step[0]);
            part.yRot = Mth.lerp(gait, part.yRot, step[1]);
            part.zRot = Mth.lerp(gait, part.zRot, step[2]);
        }
        float travel = firstPerson ? 0.0F : 1.0F;
        // Сперва тело оседает — кость опускается вдоль бока вместе с укорачиванием, — и лишь потом
        // уходит вперёд или назад и подтягивается под корпус. Так рука крипера не торчит вперёд, как у зомби.
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
        // Высота крепления и длина кости меняются вместе: иначе ступни на середине пути уходили бы под землю.
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

    /** Часть превращения, пришедшаяся на окно [from, to], со сглаженным началом и концом. */
    private static float window(float t, float from, float to) {
        float x = Mth.clamp((t - from) / (to - from), 0.0F, 1.0F);
        return x * x * (3.0F - 2.0F * x);
    }

    private static float time(Player player) {
        return (player.tickCount + net.minecraft.client.Minecraft.getInstance().getTimer()
                .getGameTimeDeltaPartialTick(true)) * 0.35F;
    }

    /** Кости, которых у существа нет, усыхают и уходят в тело. */
    private static void absorb(ModelPart part, float t) {
        float scale = Math.max(0.001F, 1.0F - t);
        part.xScale = scale;
        part.yScale = scale;
        part.zScale = scale;
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
