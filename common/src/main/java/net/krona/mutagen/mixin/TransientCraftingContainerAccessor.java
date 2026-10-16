package net.krona.mutagen.mixin;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The crafting grid knows its menu: from it we can tell it's the 2×2 grid in the player inventory. */
@Mixin(TransientCraftingContainer.class)
public interface TransientCraftingContainerAccessor {
    @Accessor("menu")
    AbstractContainerMenu mutagen$getMenu();
}
