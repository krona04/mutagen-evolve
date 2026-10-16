package net.krona.mutagen.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.krona.mutagen.MutagenConfig;
import net.krona.mutagen.client.body.BodyMorph;
import net.krona.mutagen.client.body.Box;
import net.krona.mutagen.client.body.InfectionTextures;
import net.krona.mutagen.client.body.MorphPlan;
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
import org.joml.Vector3f;

/**
 * The creature's surface on a transforming body.
 * <p>
 * The body shape is changed by {@link BodyMorph}: the player's bone itself becomes a part of the creature. Here the
 * creature's hide is laid onto the same shape — its actual model part, fitted exactly into the box the bone
 * currently occupies, and only a hair outside it. The hide shows through in islands as the bone becomes infected
 * ({@link InfectionTextures#part}), and by the end of the transformation covers the bone entirely. Parts that
 * humans don't have grow out of the body in their own place.
 */
@Environment(EnvType.CLIENT)
public final class HybridRenderer {
    /** The hide sits slightly outside the bone so it doesn't z-fight with the skin. */
    private static final float SURFACE = 1.03F;
    /** While the bone's infection is very fresh, there is no hide on it yet. */
    private static final float MIN_PHASE = 0.02F;

    private HybridRenderer() {
    }

    private static boolean active(MutagenData.Gene gene) {
        Stage stage = gene.stage();
        return MutagenConfig.get().hybridRender && stage.atLeast(Stage.MANIFESTATION) && !stage.atLeast(Stage.FULL);
    }

    /** Third person: hide on all restructured bones, plus grown parts. */
    public static void renderBody(PlayerModel<?> model, AbstractClientPlayer player, MutagenData.Gene gene,
                                  Strain strain, PoseStack pose, MultiBufferSource buffer, int light) {
        BodyMorph.Frame frame = BodyMorph.frame(player);
        if (!active(gene) || frame == null) {
            return;
        }
        MorphPlan plan = MorphPlan.of(strain);
        for (Strain.Bone bone : Strain.Bone.values()) {
            MorphPlan.Target target = plan.target(bone);
            if (target != null) {
                drawOn(player, strain, target, BodyMorph.bone(model, bone), frame, bone, pose, buffer, light);
            }
        }
        for (MorphPlan.Target grown : plan.grown()) {
            float t = Infection.bonePhase(strain, grown.part().bone(), gene.progress());
            if (t < MIN_PHASE) {
                continue;
            }
            ModelPart follow = BodyMorph.bone(model, grown.part().bone());
            ModelPart mob = grown.model();
            mob.x = grown.pose().x;
            mob.y = grown.pose().y;
            mob.z = grown.pose().z;
            mob.xRot = grown.pose().xRot + follow.xRot;
            mob.yRot = grown.pose().yRot + follow.yRot;
            mob.zRot = grown.pose().zRot + follow.zRot;
            mob.xScale = t;
            mob.yScale = t;
            mob.zScale = t;
            render(player, strain, grown, t, mob, pose, buffer, light);
        }
    }

    /** First person: the creature's hide on the arm that is transforming right now. */
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

    private static void drawOn(AbstractClientPlayer player, Strain strain, MorphPlan.Target target, ModelPart bone,
                               BodyMorph.Frame frame, Strain.Bone which, PoseStack pose, MultiBufferSource buffer,
                               int light) {
        float t = frame.phase(which);
        Box shape = frame.shape(which);
        if (t < MIN_PHASE || shape == null) {
            return;
        }
        ModelPart mob = target.model();
        mob.xRot = bone.xRot;
        mob.yRot = bone.yRot;
        mob.zRot = bone.zRot;
        Box.Fit fit = target.box().fit(shape.scaled(SURFACE, SURFACE, SURFACE));
        mob.xScale = fit.scale().x();
        mob.yScale = fit.scale().y();
        mob.zScale = fit.scale().z();
        Vector3f shift = BodyMorph.rotate(mob, fit.offset());
        float[] pivot = frame.pivot(which);
        mob.x = pivot[0] + shift.x;
        mob.y = pivot[1] + shift.y;
        mob.z = pivot[2] + shift.z;
        render(player, strain, target, t, mob, pose, buffer, light);
    }

    private static void render(AbstractClientPlayer player, Strain strain, MorphPlan.Target target, float t,
                               ModelPart mob, PoseStack pose, MultiBufferSource buffer, int light) {
        ResourceLocation texture = InfectionTextures.part(player, strain, target.index(), t);
        if (texture == null) {
            return;
        }
        mob.visible = true;
        mob.render(pose, buffer.getBuffer(RenderType.entityTranslucent(texture)), light, OverlayTexture.NO_OVERLAY);
    }
}
