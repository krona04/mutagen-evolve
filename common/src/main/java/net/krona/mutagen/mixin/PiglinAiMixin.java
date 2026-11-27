package net.krona.mutagen.mixin;

import net.krona.mutagen.mutation.Mutation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Piglins use a brain, so {@link MobMixin}'s target check does not cover them. The existing gold rule
 * treats recognized players as wearing gold. Taking gold still angers piglins.
 */
@Mixin(PiglinAi.class)
public abstract class PiglinAiMixin {
    @Inject(method = "isWearingGold", at = @At("HEAD"), cancellable = true)
    private static void mutagen$kin(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof Player player && !player.level().isClientSide
                && Mutation.pacified(player, EntityType.PIGLIN)) {
            cir.setReturnValue(true);
        }
    }
}
