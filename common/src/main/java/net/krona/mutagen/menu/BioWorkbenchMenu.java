package net.krona.mutagen.menu;

import net.krona.mutagen.recipe.BioWorkbenchInput;
import net.krona.mutagen.recipe.BioWorkbenchRecipe;
import net.krona.mutagen.registry.MutagenBlocks;
import net.krona.mutagen.registry.MutagenRecipes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Верстак биолога: шесть ячеек под реагенты и результат.
 * <p>
 * Работает только на рецептах своего типа ({@code mutagen:bio_workbench}). Ванильный верстак
 * здесь больше не прячется: всё, что собирается у биолога, собирается только у биолога.
 */
public class BioWorkbenchMenu extends AbstractContainerMenu {
    public static final int INPUTS = 6;
    public static final int RESULT_SLOT = INPUTS;
    private static final int PLAYER_START = INPUTS + 1;
    private static final int PLAYER_END = PLAYER_START + 36;

    private final ContainerLevelAccess access;
    private final ResultContainer result = new ResultContainer();
    private final SimpleContainer inputs = new SimpleContainer(INPUTS) {
        @Override
        public void setChanged() {
            super.setChanged();
            slotsChanged(this);
        }
    };
    @Nullable
    private RecipeHolder<BioWorkbenchRecipe> current;

    public BioWorkbenchMenu(int id, Inventory inventory) {
        this(id, inventory, ContainerLevelAccess.NULL);
    }

    public BioWorkbenchMenu(int id, Inventory inventory, ContainerLevelAccess access) {
        super(MutagenBlocks.BIO_WORKBENCH_MENU.get(), id);
        this.access = access;

        for (int row = 0; row < 2; row++) {
            for (int column = 0; column < 3; column++) {
                addSlot(new Slot(inputs, column + row * 3, 30 + column * 18, 26 + row * 18));
            }
        }
        addSlot(new OutputSlot(result, 0, 124, 35));

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, 142));
        }
    }

    @Override
    public void slotsChanged(Container container) {
        if (container == inputs) {
            access.execute((level, pos) -> updateResult(level));
        }
    }

    private void updateResult(Level level) {
        if (level.isClientSide) {
            return;
        }
        BioWorkbenchInput input = BioWorkbenchInput.of(inputs);
        Optional<RecipeHolder<BioWorkbenchRecipe>> found = level.getRecipeManager()
                .getRecipeFor(MutagenRecipes.BIO_WORKBENCH_TYPE.get(), input, level);
        current = found.orElse(null);
        result.setItem(0, found.map(holder -> holder.value().assemble(input, level.registryAccess()))
                .orElse(ItemStack.EMPTY));
    }

    /** Забрать результат: ингредиенты списываются только сейчас, а не при раскладке. */
    private void craft(Player taker, ItemStack stack) {
        if (current == null || !current.value().consume(inputs)) {
            return;
        }
        stack.onCraftedBy(taker.level(), taker, stack.getCount());
    }

    /**
     * Раскладывает ингредиенты рецепта из инвентаря: сначала всё со стола уходит обратно игроку,
     * потом каждый ингредиент кладётся в свою ячейку. Если чего-то не хватает, кладётся сколько есть.
     */
    public void fill(ServerPlayer target, ResourceLocation recipeId) {
        Optional<RecipeHolder<?>> holder = target.serverLevel().getRecipeManager().byKey(recipeId);
        if (holder.isEmpty() || !(holder.get().value() instanceof BioWorkbenchRecipe recipe)) {
            return;
        }
        Inventory inventory = target.getInventory();
        for (int slot = 0; slot < INPUTS; slot++) {
            ItemStack stack = inputs.removeItemNoUpdate(slot);
            if (!stack.isEmpty()) {
                inventory.placeItemBackInInventory(stack);
            }
        }

        int slot = 0;
        for (BioWorkbenchRecipe.CountedIngredient required : recipe.ingredients()) {
            if (slot >= INPUTS) {
                break;
            }
            int need = required.count();
            ItemStack placed = ItemStack.EMPTY;
            for (int index = 0; index < inventory.getContainerSize() && need > 0; index++) {
                ItemStack stack = inventory.getItem(index);
                if (stack.isEmpty() || !required.ingredient().test(stack)) {
                    continue;
                }
                if (!placed.isEmpty() && !ItemStack.isSameItemSameComponents(placed, stack)) {
                    continue;
                }
                int amount = Math.min(need, stack.getCount());
                ItemStack taken = stack.split(amount);
                if (placed.isEmpty()) {
                    placed = taken;
                } else {
                    placed.grow(taken.getCount());
                }
                need -= amount;
            }
            if (!placed.isEmpty()) {
                inputs.setItem(slot++, placed);
            }
        }
        inputs.setChanged();
    }

    @Override
    public ItemStack quickMoveStack(Player taker, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();

        if (index == RESULT_SLOT) {
            if (!moveItemStackTo(stack, PLAYER_START, PLAYER_END, true)) {
                return ItemStack.EMPTY;
            }
            slot.onQuickCraft(stack, copy);
        } else if (index < INPUTS) {
            if (!moveItemStackTo(stack, PLAYER_START, PLAYER_END, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, INPUTS, false)) {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == copy.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(taker, stack);
        return copy;
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.container != result && super.canTakeItemForPickAll(stack, slot);
    }

    @Override
    public void removed(Player taker) {
        super.removed(taker);
        access.execute((level, pos) -> clearContainer(taker, inputs));
    }

    @Override
    public boolean stillValid(Player taker) {
        return stillValid(access, taker, MutagenBlocks.BIO_WORKBENCH.get());
    }

    /** Ячейки на столе — для подсветки рецептов, которые уже можно собрать. */
    public Container inputs() {
        return inputs;
    }

    private class OutputSlot extends Slot {
        OutputSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public void onTake(Player taker, ItemStack stack) {
            craft(taker, stack);
            super.onTake(taker, stack);
        }
    }
}
