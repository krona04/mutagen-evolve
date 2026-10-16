package net.krona.mutagen.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.krona.mutagen.Mutagen;
import net.krona.mutagen.client.body.InfectionTextures;
import net.krona.mutagen.client.compat.IrisCompat;
import net.krona.mutagen.data.MutagenData;
import net.krona.mutagen.data.MutagenPlayer;
import net.krona.mutagen.strain.Stage;
import net.krona.mutagen.strain.Strain;
import net.krona.mutagen.strain.Strains;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * Gradual transformation visuals.
 * <p>
 * Stage I shows nothing: the changes are only heard and felt. From stage II, blotches of creature flesh
 * spread across the skin from the injection site ({@link InfectionTextures}), from stage III the eyes start glowing
 * and parts of the creature itself grow out ({@link HybridRenderer}). Proportions and posture are changed by
 * {@link net.krona.mutagen.client.body.BodyMorph}. The player's skin doesn't disappear until the full form:
 * their own skin remains between the blotches.
 */
@Environment(EnvType.CLIENT)
public class MutationLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final Map<ResourceLocation, Boolean> EYES = new HashMap<>();

    public MutationLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffer, int packedLight, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        if (player.isInvisible() || IrisCompat.isShadowPass()) {
            return;
        }

        MutagenData data = MutagenPlayer.of(player);
        MutagenData.Gene gene = data.primary();
        if (gene == null) {
            return;
        }
        Strain strain = Strains.get(gene.strainId());
        if (strain == null) {
            return;
        }

        Stage stage = gene.stage();
        if (!stage.atLeast(Stage.MANIFESTATION)) {
            return;
        }

        PlayerModel<AbstractClientPlayer> model = getParentModel();
        ResourceLocation skin = InfectionTextures.skin(player, strain, gene);
        if (skin != null) {
            // Same geometry as the skin: infected pixels land exactly on the skin and on the clothing.
            model.renderToBuffer(pose, buffer.getBuffer(RenderType.entityTranslucent(skin)), packedLight,
                    OverlayTexture.NO_OVERLAY, -1);
        }

        HybridRenderer.renderBody(model, player, gene, strain, pose, buffer, packedLight);

        ResourceLocation eyes = Mutagen.id("textures/entity/eyes/" + gene.strainId().getPath() + ".png");
        if (stage.atLeast(Stage.MUTATION) && hasTexture(eyes)) {
            VertexConsumer glow = buffer.getBuffer(RenderType.eyes(eyes));
            model.head.render(pose, glow, 15728640, OverlayTexture.NO_OVERLAY, -1);
        }
    }

    /**
     * Not every strain has glowing eyes: a strain from a third-party datapack may lack an eyes texture,
     * and the black-and-purple placeholder must not be drawn in its place.
     */
    private static boolean hasTexture(ResourceLocation texture) {
        return EYES.computeIfAbsent(texture,
                key -> Minecraft.getInstance().getResourceManager().getResource(key).isPresent());
    }
}
