package net.krona.mutagen.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.krona.mutagen.client.FullFormRenderer;
import net.krona.mutagen.client.MutationLayer;
import net.krona.mutagen.client.body.BodyMorph;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Две правки рендера игрока: слой постепенной мутации и полная подмена модели на пятой стадии.
 */
@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererMixin
        extends LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    private PlayerRendererMixin(EntityRendererProvider.Context context,
                                PlayerModel<AbstractClientPlayer> model, float shadowRadius) {
        super(context, model, shadowRadius);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void mutagen$addMutationLayer(EntityRendererProvider.Context context, boolean slim, CallbackInfo ci) {
        addLayer(new MutationLayer((PlayerRenderer) (Object) this));
    }

    @SuppressWarnings("unchecked")
    @Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"), cancellable = true)
    private void mutagen$renderFullForm(AbstractClientPlayer player, float entityYaw, float partialTicks,
                                        PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                                        CallbackInfo ci) {
        LivingEntity dummy = FullFormRenderer.dummyFor(player);
        if (dummy == null) {
            return;
        }

        FullFormRenderer.copyState(player, dummy);
        EntityRenderer<LivingEntity> renderer = (EntityRenderer<LivingEntity>)
                Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(dummy);
        renderer.render(dummy, entityYaw, partialTicks, poseStack, buffer, packedLight);

        // Ник рисуем сами: у манекена его нет, а игрока на сервере надо узнавать.
        if (shouldShowName(player)) {
            renderNameTag(player, player.getDisplayName(), poseStack, buffer, packedLight, partialTicks);
        }
        ci.cancel();
    }

    @Inject(method = "renderRightHand", at = @At("HEAD"), cancellable = true)
    private void mutagen$rightHandFullForm(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                                           AbstractClientPlayer player, CallbackInfo ci) {
        if (FullFormRenderer.renderFullFormHand(player, poseStack, buffer, packedLight, true)) {
            ci.cancel();
            return;
        }
        BodyMorph.setFirstPerson(true);
    }

    @Inject(method = "renderLeftHand", at = @At("HEAD"), cancellable = true)
    private void mutagen$leftHandFullForm(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                                          AbstractClientPlayer player, CallbackInfo ci) {
        if (FullFormRenderer.renderFullFormHand(player, poseStack, buffer, packedLight, false)) {
            ci.cancel();
            return;
        }
        BodyMorph.setFirstPerson(true);
    }

    @Inject(method = "renderRightHand", at = @At("RETURN"))
    private void mutagen$tintRightHand(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                                       AbstractClientPlayer player, CallbackInfo ci) {
        FullFormRenderer.tintHand(player, poseStack, buffer, packedLight,
                getModel().rightArm, getModel().rightSleeve, true);
        BodyMorph.setFirstPerson(false);
    }

    @Inject(method = "renderLeftHand", at = @At("RETURN"))
    private void mutagen$tintLeftHand(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                                      AbstractClientPlayer player, CallbackInfo ci) {
        FullFormRenderer.tintHand(player, poseStack, buffer, packedLight,
                getModel().leftArm, getModel().leftSleeve, false);
        BodyMorph.setFirstPerson(false);
    }
}
