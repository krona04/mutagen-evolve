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
 * Render the creature's skin over the transforming body.
 * <p>
 * {@link BodyMorph} reshapes each player bone into a creature part. This renderer fits the matching
 * creature model part over the bone's current box with a slight offset. The skin appears in patches as
 * infection spreads ({@link InfectionTextures#part}) and eventually covers the whole bone. Parts with no
 * human equivalent grow from the body in their proper positions.
 */
@Environment(EnvType.CLIENT)
public final class HybridRenderer {
    /** Offset the creature skin slightly to avoid z-fighting with the player skin. */

    /** Skip the creature skin while infection in this bone is still minimal. */
    private static final float MIN_PHASE = 0.02F;

    private HybridRenderer() {
    }

    private static boolean active(MutagenData.Gene gene) {
        Stage stage = gene.stage();
        return MutagenConfig.get().hybridRender && stage.atLeast(Stage.MANIFESTATION) && !stage.atLeast(Stage.FULL);
    }

    /** Render skin on transformed bones and any grown parts in third person. */
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
            // Grow with the amount of creature skin covering the bone as well as its infection progress.
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
     * Anchor the growing part to its source bone. A spider's abdomen and middle legs follow the torso as it
     * tilts, starting from the relative pose of the corresponding creature parts. If the bone has no
     * creature counterpart, use the part's own position.
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
        // Keep the part close to its point of origin while it is small.
        Vector3f offset = relative.getTranslation(new Vector3f());
        relative.setTranslation(offset.mul(0.35F + 0.65F * t));
        return current.mul(relative);
    }

    /** Render creature skin over the transforming arm in first person. */
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
     * Render the creature part over the bone. The bone is aligned to the part's long axis ({@link
     * MorphPlan.Target#align}), so undo that rotation and transform its box into the part's coordinates.
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

    /** Copy the part's pose and its descendants' poses to the corresponding part in another model layer. */
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
        // Descendants with their own plan entries render independently in their own positions.
        for (ModelPart hidden : target.hide()) {
            hidden.visible = false;
        }
        mob.render(pose, buffer.getBuffer(RenderType.entityCutoutNoCull(texture)), light, OverlayTexture.NO_OVERLAY, colour);

        // Render extra creature layers over the same part and pose using their own textures.
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
