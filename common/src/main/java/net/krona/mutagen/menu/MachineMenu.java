package net.krona.mutagen.menu;

import net.krona.mutagen.block.MachineBlockEntity;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Общая часть экранов лабораторных машин: слоты игрока, прогресс и перенос предметов шифтом.
 */
public abstract class MachineMenu extends AbstractContainerMenu {
    protected final Container container;
    protected final ContainerData data;
    protected final int machineSlots;

    protected MachineMenu(MenuType<?> type, int id, Inventory inventory, Container container,
                          ContainerData data, int machineSlots) {
        super(type, id);
        checkContainerSize(container, machineSlots);
        this.container = container;
        this.data = data;
        this.machineSlots = machineSlots;
        addDataSlots(data);
    }

    protected void addPlayerInventory(Inventory inventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, 142));
        }
    }

    public int progress() {
        return data.get(MachineBlockEntity.DATA_PROGRESS);
    }

    public int maxProgress() {
        int max = data.get(MachineBlockEntity.DATA_MAX_PROGRESS);
        return max == 0 ? 1 : max;
    }

    public int burnTime() {
        return data.get(MachineBlockEntity.DATA_BURN);
    }

    public int burnDuration() {
        int duration = data.get(MachineBlockEntity.DATA_BURN_DURATION);
        return duration == 0 ? 1 : duration;
    }

    public boolean isWorking() {
        return progress() > 0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return result;
        }

        ItemStack stack = slot.getItem();
        result = stack.copy();
        int playerStart = machineSlots;
        int playerEnd = machineSlots + 36;

        if (index < machineSlots) {
            if (!moveItemStackTo(stack, playerStart, playerEnd, true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, machineSlots, false)) {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player);
    }
}
