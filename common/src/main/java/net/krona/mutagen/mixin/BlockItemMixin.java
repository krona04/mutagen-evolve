package net.krona.mutagen.mixin;

import net.krona.mutagen.mutation.Restrictions;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Тело, которому нечем строить, блоков не ставит. Сундуки, двери и рычаги при этом открываются:
 * запрещена только установка блока.
 */
@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void mutagen$noBuilding(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
        if (context.getPlayer() != null && Restrictions.blocksBuilding(context.getPlayer())) {
            Restrictions.tell(context.getPlayer(), "mutagen.restriction.building");
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }
}
