package net.krona.mutagen.mixin;

import net.krona.mutagen.mutation.Restrictions;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Block placing and taking items in locked player slots in any inventory menu.
 */
@Mixin(Slot.class)
public abstract class SlotMixin {
    @Shadow
    @Final
    public net.minecraft.world.Container container;

    @Shadow
    public abstract int getContainerSlot();

    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void mutagen$lockedPlace(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (container instanceof Inventory inventory && Restrictions.slotLocked(inventory.player, getContainerSlot())) {
            cir.setReturnValue(false);
            return;
        }
        // Handless players cannot place items in the inventory's 2×2 crafting grid.
        if (container instanceof TransientCraftingContainer grid
                && ((TransientCraftingContainerAccessor) grid).mutagen$getMenu() instanceof InventoryMenu menu) {
            Player owner = ((InventoryMenuAccessor) menu).mutagen$getOwner();
            if (Restrictions.of(owner).noCrafting()) {
                Restrictions.tell(owner, "mutagen.restriction.crafting");
                cir.setReturnValue(false);
            }
        }
    }

    @Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
    private void mutagen$lockedPickup(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (container instanceof Inventory inventory && Restrictions.slotLocked(inventory.player, getContainerSlot())) {
            Restrictions.tell(player, "mutagen.restriction.inventory");
            cir.setReturnValue(false);
        }
    }
}
