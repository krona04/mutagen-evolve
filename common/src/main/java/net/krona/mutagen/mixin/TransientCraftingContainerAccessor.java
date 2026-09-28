package net.krona.mutagen.mixin;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Сетка крафта знает своё меню: по нему видно, что это сетка 2×2 в инвентаре игрока. */
@Mixin(TransientCraftingContainer.class)
public interface TransientCraftingContainerAccessor {
    @Accessor("menu")
    AbstractContainerMenu mutagen$getMenu();
}
