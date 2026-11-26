package net.krona.mutagen.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.krona.mutagen.MutagenConfig;
import net.krona.mutagen.client.body.BodyMorph;
import net.krona.mutagen.client.body.Box;
import net.krona.mutagen.client.body.InfectionTextures;
import net.krona.mutagen.client.body.MorphPlan;
import net.krona.mutagen.client.body.Poses;
import net.krona.mutagen.client.body.SpiderMorph;
import net.krona.mutagen.client.body.MorphSequence;
import net.krona.mutagen.client.body.MorphSurface;
import net.krona.mutagen.client.body.SpiderLegRenderer;
import net.krona.mutagen.client.body.ContinuousMorph;
import net.krona.mutagen.client.body.ContinuousMorphRenderer;
import net.krona.mutagen.mixin.client.ModelPartAccessor;
import net.krona.mutagen.data.MutagenData;
import net.krona.mutagen.data.MutagenPlayer;
import net.krona.mutagen.strain.Infection;
import net.krona.mutagen.strain.Stage;
import net.krona.mutagen.strain.Strain;
import net.krona.mutagen.strain.Strains;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;
import java.util.Map;

/**
 * Поверхность существа на превращающемся теле.
 * <p>
 * Форму тела меняет {@link BodyMorph}: кость игрока сама становится частью существа. Здесь на ту же
 * форму ложится шкура существа — его настоящая часть модели, вписанная ровно в коробку, которую сейчас
 * занимает кость, и лишь на волос снаружи. Шкура проступает островками по мере заражения кости
 * ({@link InfectionTextures#part}), а к концу превращения закрывает кость целиком. Части, которых
 * у человека нет, вырастают из тела на своём месте.
 */
@Environment(EnvType.CLIENT)
public final class HybridRenderer {
    /** Шкура чуть снаружи кости, чтобы не мерцать в одной плоскости со скином. */

    /** Пока заражение кости совсем свежее, шкуры на ней ещё нет. */
    private static final float MIN_PHASE = 0.02F;

    private HybridRenderer() {
    }

    private static boolean active(MutagenData.Gene gene) {
        Stage stage = gene.stage();
        return MutagenConfig.get().hybridRender && stage.atLeast(Stage.MANIFESTATION) && !stage.atLeast(Stage.FULL);
    }

    /** Третье лицо: шкура на всех перестроенных костях и выросшие части. */
    public static void renderBody(PlayerModel<?> model, AbstractClientPlayer player, MutagenData.Gene gene,
                                  Strain strain, PoseStack pose, MultiBufferSource buffer, int light) {
        BodyMorph.Frame frame = BodyMorph.frame(player);
        if (!active(gene) || frame == null) {
            return;
        }
        MorphPlan plan = MorphPlan.of(strain);
        if (MutagenConfig.get().bodyMorph && ContinuousMorph.supports(strain)) {
            ContinuousMorphRenderer.render(model, player, strain, gene.progress(), frame, pose, buffer, light);
            return;
        }
        for (Strain.Bone bone : Strain.Bone.values()) {
            MorphPlan.Target target = plan.target(bone);
            if (target != null) {
                drawOn(player, strain, target, BodyMorph.bone(model, bone), frame, bone, pose, buffer, light);
            }
        }
        List<MorphPlan.Target> grownParts = plan.grown();
        for (int i = 0; i < grownParts.size(); i++) {
            MorphPlan.Target grown = grownParts.get(i);
            Matrix4f own = frame.grown(i);
            Strain.Bone follow = grown.part().bone();
            // Растёт вместе с тем, как кость покрывается шкурой существа, а не только с её заражением.
            float t = frame.shape(follow) != null ? frame.phase(follow)
                    : Infection.bonePhase(strain, follow, gene.progress());
            if (MutagenConfig.get().bodyMorph) {
                t = MorphSequence.growth(strain, grown, gene.progress());
            }
            if (own == null || t < MIN_PHASE) {
                continue;
            }
            Poses.place(grown.model(), attach(model, plan, frame, follow, own, t, MutagenConfig.get().bodyMorph), t);
            if (SpiderMorph.supports(strain) && MutagenConfig.get().bodyMorph) {
                SpiderMorph.unfold(grown.model(), grown, t);
            }
            if (SpiderMorph.supports(strain) && MutagenConfig.get().bodyMorph && grown.part().part().contains("leg")) {
                SpiderLegRenderer.render(grown.model(), grown, t, gene.progress(), player.tickCount,
                        pose, buffer, light);
                float handoff = SpiderLegRenderer.handoff(gene.progress());
                if (handoff > 0) render(player, strain, grown, t, grown.model(), pose, buffer, light);
            } else {
                render(player, strain, grown, t, grown.model(), pose, buffer, light);
            }
        }
    }

    /**
     * Где сейчас выросшая часть. Она держится за кость, из которой растёт: пока корпус человека ещё
     * только ложится горизонтально, брюшко паука и средние лапы уже сидят на нём, а не висят там,
     * где будут у готового паука. Сдвиг и поворот части относительно родительской части существа
     * переносятся на кость в её нынешней позе. Кость без пары в существе — часть на своём месте.
     */
    private static Matrix4f attach(PlayerModel<?> model, MorphPlan plan, BodyMorph.Frame frame, Strain.Bone follow,
                                   Matrix4f own, float t, boolean anatomical) {
        MorphPlan.Target parent = plan.target(follow);
        Matrix4f parentGoal = frame.goal(follow);
        if (parent == null || parentGoal == null || frame.shape(follow) == null) {
            return own;
        }
        float[] pivot = frame.pivot(follow);
        ModelPart bone = BodyMorph.bone(model, follow);
        if (anatomical) {
            float scale = Poses.scale(parentGoal);
            Box.Fit fit = parent.aligned().times(scale).fit(frame.shape(follow));
            Matrix4f relative = parentGoal.invert(new Matrix4f()).mul(own);
            Matrix4f attached = new Matrix4f().translate(pivot[0] / 16.0F, pivot[1] / 16.0F, pivot[2] / 16.0F)
                    .rotate(Poses.rotation(bone))
                    .translate(fit.offset().x / 16.0F, fit.offset().y / 16.0F, fit.offset().z / 16.0F)
                    .scale(fit.scale())
                    .rotate(new Quaternionf(parent.align()).conjugate()).scale(scale).mul(relative);
            return new Matrix4f().translation(attached.getTranslation(new Vector3f()))
                    .rotate(Poses.rotation(bone).mul(new Quaternionf(parent.align()).conjugate())
                            .mul(Poses.rotation(relative))).scale(scale * Poses.scale(relative));
        }
        Matrix4f current = new Matrix4f()
                .translate(pivot[0] / 16.0F, pivot[1] / 16.0F, pivot[2] / 16.0F)
                .rotate(Poses.rotation(bone).mul(new Quaternionf(parent.align()).conjugate()))
                .scale(Poses.scale(parentGoal));
        Matrix4f relative = parentGoal.invert(new Matrix4f()).mul(own);
        // Часть выходит из кости: пока она мала, она и сидит ближе к месту, откуда растёт.
        Vector3f offset = relative.getTranslation(new Vector3f());
        relative.setTranslation(offset.mul(0.35F + 0.65F * t));
        return current.mul(relative);
    }

    /** Первое лицо: шкура существа на руке, которая сейчас превращается. */
    public static void renderArm(AbstractClientPlayer player, ModelPart arm, boolean right, PoseStack pose,
                                 MultiBufferSource buffer, int light) {
        MutagenData.Gene gene = MutagenPlayer.of(player).primary();
        BodyMorph.Frame frame = BodyMorph.frame(player);
        if (gene == null || frame == null || !active(gene)) {
            return;
        }
        Strain strain = Strains.get(gene.strainId());
        if (strain == null) {
            return;
        }
        Strain.Bone bone = right ? Strain.Bone.RIGHT_ARM : Strain.Bone.LEFT_ARM;
        MorphPlan.Target target = MorphPlan.of(strain).target(bone);
        if (target != null) {
            drawOn(player, strain, target, arm, frame, bone, pose, buffer, light);
        }
    }

    /**
     * Часть существа поверх кости. Кость повёрнута вдоль длинной оси части ({@link MorphPlan.Target#align}),
     * поэтому часть рисуется с обратным доворотом, а коробка кости переводится в её координаты.
     */
    private static void drawOn(AbstractClientPlayer player, Strain strain, MorphPlan.Target target, ModelPart bone,
                               BodyMorph.Frame frame, Strain.Bone which, PoseStack pose, MultiBufferSource buffer,
                               int light) {
        float t = frame.phase(which);
        Box shape = frame.shape(which);
        if (t < MIN_PHASE || shape == null) {
            return;
        }
        ModelPart mob = target.model();
        Quaternionf rotation = Poses.rotation(bone).mul(new Quaternionf(target.align()).conjugate());
        Poses.setRotation(mob, rotation);
        float surfaceScale = MorphSurface.clearance(t);
        Box surface = shape.scaled(surfaceScale, surfaceScale, surfaceScale).rotated(target.align());
        Box.Fit fit = target.box().fit(surface);
        mob.xScale = fit.scale().x();
        mob.yScale = fit.scale().y();
        mob.zScale = fit.scale().z();
        Vector3f shift = rotation.transform(new Vector3f(fit.offset()));
        float[] pivot = frame.pivot(which);
        mob.x = pivot[0] + shift.x;
        mob.y = pivot[1] + shift.y;
        mob.z = pivot[2] + shift.z;
        render(player, strain, target, t, mob, pose, buffer, light);
    }

    /** Поза части и всех её потомков — на такую же часть другого слоя модели. */
    private static void copyPose(ModelPart from, ModelPart to) {
        to.copyFrom(from);
        to.xScale = from.xScale;
        to.yScale = from.yScale;
        to.zScale = from.zScale;
        to.visible = from.visible;
        Map<String, ModelPart> source = ((ModelPartAccessor) (Object) from).mutagen$getChildren();
        for (Map.Entry<String, ModelPart> child : ((ModelPartAccessor) (Object) to).mutagen$getChildren().entrySet()) {
            ModelPart match = source.get(child.getKey());
            if (match != null) {
                copyPose(match, child.getValue());
            }
        }
    }

    private static void render(AbstractClientPlayer player, Strain strain, MorphPlan.Target target, float t,
                               ModelPart mob, PoseStack pose, MultiBufferSource buffer, int light) {
        int colour = -1;
        ResourceLocation texture = InfectionTextures.part(player, strain, target.index(), t);
        if (texture == null) {
            return;
        }
        mob.visible = true;
        // Потомки, которых ведёт своя запись плана, рисуются сами, на своём месте.
        for (ModelPart hidden : target.hide()) {
            hidden.visible = false;
        }
        mob.render(pose, buffer.getBuffer(RenderType.entityCutoutNoCull(texture)), light, OverlayTexture.NO_OVERLAY, colour);

        // Дополнительные слои существа — та же часть в той же позе, со своей текстурой.
        List<Strain.Overlay> overlays = strain.body().overlays();
        for (int i = 0; i < overlays.size() && i < target.overlays().size(); i++) {
            ModelPart layer = target.overlays().get(i).orElse(null);
            if (layer == null) {
                continue;
            }
            Strain.Overlay overlay = overlays.get(i);
            ResourceLocation skin = InfectionTextures.part(player, strain, target.index(), t, overlay.texture(), i + 1);
            if (skin == null) {
                continue;
            }
            copyPose(mob, layer);
            layer.render(pose, buffer.getBuffer(overlay.emissive() ? RenderType.eyes(skin)
                    : RenderType.entityTranslucent(skin)), light, OverlayTexture.NO_OVERLAY, colour);
        }
        for (ModelPart hidden : target.hide()) {
            hidden.visible = true;
        }
    }
}
