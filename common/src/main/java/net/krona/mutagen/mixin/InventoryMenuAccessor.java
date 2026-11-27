package net.krona.mutagen.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Inventory owner used to check whether they can use the 2×2 crafting grid. */
@Mixin(InventoryMenu.class)
public interface InventoryMenuAccessor {
    @Accessor("owner")
    Player mutagen$getOwner();
}
