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
 * Effect immunity, wall climbing, and attacks by mutated players against mobs.
 * <p>
 * Player overrides {@code actuallyHurt}. Incoming player damage is handled by {@link PlayerMixin}.
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
    /** Apply the attacker's traits, such as bee venom, when they hit a mob. */
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
     * Treat a wall the player presses against as climbable. Client and server both know the strain and
     * stage, so run the check on both sides.
     */
    @Inject(method = "onClimbable", at = @At("HEAD"), cancellable = true)
    private void mutagen$climbWalls(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof Player player && player.horizontalCollision && !player.isSpectator()
                && !player.getAbilities().flying && Mutation.climbs(player)) {
            cir.setReturnValue(true);
        }
    }
}
