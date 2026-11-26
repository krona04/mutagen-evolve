package net.krona.mutagen.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.krona.mutagen.client.body.BodyMorph;
import net.krona.mutagen.strain.Strain;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Рука, которая стала ногой, ничего не держит: предмет не висит у её копыта.
 */
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMixin {
    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void mutagen$noItemOnLeg(LivingEntity entity, ItemStack stack, ItemDisplayContext context,
                                         HumanoidArm arm, PoseStack pose, MultiBufferSource buffer, int light,
                                         CallbackInfo ci) {
        if (!(entity instanceof Player player)) {
            return;
        }
        BodyMorph.Frame frame = BodyMorph.frame(player);
        Strain.Bone bone = arm == HumanoidArm.RIGHT ? Strain.Bone.RIGHT_ARM : Strain.Bone.LEFT_ARM;
        if (frame != null && frame.becameLeg(bone)) {
            ci.cancel();
        }
    }
}
