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
 * Пиглины думают мозгом, а не целью моба, и отказ от цели в {@link MobMixin} до них не доходит.
 * Зато у них есть готовое правило «свой — тот, кто в золоте»: носитель, которого пиглины
 * признали своим, для них всегда в золоте. Тронет их золото — разозлятся, как на любого.
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
