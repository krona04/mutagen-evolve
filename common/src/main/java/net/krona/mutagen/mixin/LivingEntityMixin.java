package net.krona.mutagen.mixin;

import net.krona.mutagen.mutation.Mutation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Иммунитеты к эффектам, лазание по стенам и удары носителя по мобам.
 * <p>
 * Урон по самому носителю здесь не трогается: игрок переопределяет {@code actuallyHurt} целиком
 * и до этой версии метода не доходит. Его урон правит {@link PlayerMixin}.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Inject(method = "canBreatheUnderwater", at = @At("HEAD"), cancellable = true)
    private void mutagen$gills(CallbackInfoReturnable<Boolean> cir) {
        if ((Object)this instanceof Player player
                && net.krona.mutagen.mutation.AquaticAdaptation.aquatic(player)) cir.setReturnValue(true);
    }

    @Inject(method = "increaseAirSupply", at = @At("HEAD"), cancellable = true)
    private void mutagen$noLandRefill(int air, CallbackInfoReturnable<Integer> cir) {
        if ((Object)this instanceof Player player && !player.isCreative() && !player.isSpectator()
                && net.krona.mutagen.mutation.AquaticAdaptation.dependent(player)
                && !net.krona.mutagen.mutation.AquaticAdaptation.wet(player)) cir.setReturnValue(air);
    }
    /** Удар по мобу: черты носителя-нападающего вроде яда пчелы. */
    @Inject(method = "actuallyHurt", at = @At("HEAD"))
    private void mutagen$melee(DamageSource source, float amount, CallbackInfo ci) {
        Mutation.onDamaged((LivingEntity) (Object) this, source);
    }

    @Inject(method = "canBeAffected", at = @At("HEAD"), cancellable = true)
    private void mutagen$blockEffects(MobEffectInstance instance, CallbackInfoReturnable<Boolean> cir) {
        if (Mutation.blocksEffect((LivingEntity) (Object) this, instance.getEffect())) {
            cir.setReturnValue(false);
        }
    }

    /**
     * Стена считается лестницей, пока носитель в неё упирается. Движение игрока считает клиент,
     * поэтому проверка идёт на обеих сторонах — обе знают штамм и стадию.
     */
    @Inject(method = "onClimbable", at = @At("HEAD"), cancellable = true)
    private void mutagen$climbWalls(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof Player player && player.horizontalCollision && !player.isSpectator()
                && !player.getAbilities().flying && Mutation.climbs(player)) {
            cir.setReturnValue(true);
        }
    }
}
