package net.krona.mutagen.mixin.client;

import net.krona.mutagen.client.body.BodyMorph;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Skeleton restructuring on top of the vanilla animation: the mutant's proportions, posture and twitches.
 * Armor and creature parts take their pose from the bones after this, so they follow the body.
 */
@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin {
    /** One model for all players: every frame starts from the initial pose, otherwise offsets accumulate. */
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("HEAD"))
    private void mutagen$resetPose(LivingEntity entity, float limbSwing, float limbSwingAmount,
                                       float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        BodyMorph.resetPose((PlayerModel<?>) (Object) this);
    }

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("RETURN"))
    private void mutagen$morph(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                                   float netHeadYaw, float headPitch, CallbackInfo ci) {
        BodyMorph.apply((PlayerModel<?>) (Object) this, entity);
    }
}
