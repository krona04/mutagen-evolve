package net.krona.mutagen.mixin;

import net.krona.mutagen.mutation.Restrictions;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Picked-up items don't go into locked slots: item pickup and return look for space only where
 * the host can still reach.
 */
@Mixin(Inventory.class)
public abstract class InventoryMixin {
    @Shadow
    @Final
    public Player player;

    @Shadow
    @Final
    public NonNullList<ItemStack> items;

    @Shadow
    public int selected;

    @Inject(method = "getFreeSlot", at = @At("HEAD"), cancellable = true)
    private void mutagen$freeSlot(CallbackInfoReturnable<Integer> cir) {
        Restrictions.Active active = Restrictions.of(player);
        if (!active.any()) {
            return;
        }
        for (int i = 0; i < items.size(); i++) {
            if (!Restrictions.locked(active, i) && items.get(i).isEmpty()) {
                cir.setReturnValue(i);
                return;
            }
        }
        cir.setReturnValue(-1);
    }

    @Inject(method = "getSlotWithRemainingSpace", at = @At("HEAD"), cancellable = true)
    private void mutagen$slotWithSpace(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        Restrictions.Active active = Restrictions.of(player);
        if (!active.any()) {
            return;
        }
        if (!Restrictions.locked(active, selected) && fits(items.get(selected), stack)) {
            cir.setReturnValue(selected);
            return;
        }
        for (int i = 0; i < items.size(); i++) {
            if (!Restrictions.locked(active, i) && fits(items.get(i), stack)) {
                cir.setReturnValue(i);
                return;
            }
        }
        cir.setReturnValue(-1);
    }

    private boolean fits(ItemStack destination, ItemStack stack) {
        return !destination.isEmpty() && ItemStack.isSameItemSameComponents(destination, stack)
                && destination.isStackable() && destination.getCount() < destination.getMaxStackSize()
                && destination.getCount() < ((Inventory) (Object) this).getMaxStackSize();
    }
}
