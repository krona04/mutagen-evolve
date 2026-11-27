package net.krona.mutagen.mixin;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Expose the owning menu to identify the player inventory's 2×2 crafting grid. */
@Mixin(TransientCraftingContainer.class)
public interface TransientCraftingContainerAccessor {
    @Accessor("menu")
    AbstractContainerMenu mutagen$getMenu();
}
