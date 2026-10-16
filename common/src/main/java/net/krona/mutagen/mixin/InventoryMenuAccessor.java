package net.krona.mutagen.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Whose inventory this is: the 2×2 grid needs to know the owner to ask whether they have fingers. */
@Mixin(InventoryMenu.class)
public interface InventoryMenuAccessor {
    @Accessor("owner")
    Player mutagen$getOwner();
}
