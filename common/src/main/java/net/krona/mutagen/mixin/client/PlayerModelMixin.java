package net.krona.mutagen.mixin.client;

import net.krona.mutagen.client.body.BodyMorph;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Перестройка скелета поверх ванильной анимации: пропорции, осанка и подёргивания мутанта.
 * Броня и части существа берут позу у костей уже после этого, поэтому идут за телом.
 */
@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin {
    /** Модель одна на всех игроков: каждый кадр начинается с исходной позы, иначе добавки копятся. */
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
