package net.krona.mutagen.mixin;

import net.krona.mutagen.mutation.Mutation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Слабости и иммунитеты штаммов: множитель входящего урона и блокировка эффектов.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @ModifyVariable(method = "actuallyHurt", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float mutagen$modifyDamage(float amount, DamageSource source) {
        return Mutation.modifyIncomingDamage((LivingEntity) (Object) this, source, amount);
    }

    @Inject(method = "canBeAffected", at = @At("HEAD"), cancellable = true)
    private void mutagen$blockEffects(MobEffectInstance instance, CallbackInfoReturnable<Boolean> cir) {
        if (Mutation.blocksEffect((LivingEntity) (Object) this, instance.getEffect())) {
            cir.setReturnValue(false);
        }
    }
}
