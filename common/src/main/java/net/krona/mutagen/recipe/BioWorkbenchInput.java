package net.krona.mutagen.recipe;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;

import java.util.ArrayList;
import java.util.List;

/** Содержимое ячеек верстака биолога для поиска рецепта. */
public record BioWorkbenchInput(List<ItemStack> items) implements RecipeInput {
    public static BioWorkbenchInput of(Container container) {
        List<ItemStack> items = new ArrayList<>(container.getContainerSize());
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            items.add(container.getItem(slot));
        }
        return new BioWorkbenchInput(items);
    }

    @Override
    public ItemStack getItem(int index) {
        return items.get(index);
    }

    @Override
    public int size() {
        return items.size();
    }
}
