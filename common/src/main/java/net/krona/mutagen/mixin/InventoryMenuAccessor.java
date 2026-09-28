package net.krona.mutagen.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Чей это инвентарь: сетке 2×2 нужно знать владельца, чтобы спросить, есть ли у него пальцы. */
@Mixin(InventoryMenu.class)
public interface InventoryMenuAccessor {
    @Accessor("owner")
    Player mutagen$getOwner();
}
