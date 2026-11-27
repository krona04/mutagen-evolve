package net.krona.mutagen.mixin.client;

import net.krona.mutagen.client.body.BodyMorph;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Apply mutant proportions, posture, and twitches after vanilla animation. Armor and creature parts then
 * follow the modified bones.
 */
@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin {
    /** Restore the original pose each frame so transforms do not accumulate on the shared model. */
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("HEAD"))
    private void mutagen$resetPose(LivingEntity entity, float limbSwing, float limbSwingAmount,
                                       float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (entity instanceof Player) {
            BodyMorph.resetPose((PlayerModel<?>) (Object) this);
        }
    }

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("RETURN"))
    private void mutagen$morph(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                                   float netHeadYaw, float headPitch, CallbackInfo ci) {
        BodyMorph.apply((PlayerModel<?>) (Object) this, entity, limbSwing, limbSwingAmount, ageInTicks,
                netHeadYaw, headPitch);
    }
}
