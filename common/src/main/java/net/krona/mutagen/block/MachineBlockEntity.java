package net.krona.mutagen.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Общая часть лабораторных машин: контейнер, прогресс и синхронизация с экраном.
 */
public abstract class MachineBlockEntity extends BlockEntity implements Container, MenuProvider {
    public static final int DATA_PROGRESS = 0;
    public static final int DATA_MAX_PROGRESS = 1;
    public static final int DATA_BURN = 2;
    public static final int DATA_BURN_DURATION = 3;
    public static final int DATA_COUNT = 4;

    protected final NonNullList<ItemStack> items;
    protected int progress;
    protected int maxProgress;
    protected int burnTime;
    protected int burnDuration;
    /**
     * Кто последним открыл машину. При личной изученности ({@code knowledgeScope = "player"})
     * секвенатор записывает знание этому игроку, а синтезатор берёт изученность у него.
     */
    @Nullable
    protected UUID owner;

    protected final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_PROGRESS -> progress;
                case DATA_MAX_PROGRESS -> maxProgress;
                case DATA_BURN -> burnTime;
                case DATA_BURN_DURATION -> burnDuration;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case DATA_PROGRESS -> progress = value;
                case DATA_MAX_PROGRESS -> maxProgress = value;
                case DATA_BURN -> burnTime = value;
                case DATA_BURN_DURATION -> burnDuration = value;
                default -> {
                }
            }
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    protected MachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int size, int maxProgress) {
        super(type, pos, state);
        this.items = NonNullList.withSize(size, ItemStack.EMPTY);
        this.maxProgress = maxProgress;
    }

    public ContainerData containerData() {
        return data;
    }

    /** Вызывается при открытии экрана: машина запоминает, на кого работает. */
    protected void rememberUser(Player player) {
        if (!player.getUUID().equals(owner)) {
            owner = player.getUUID();
            setChanged();
        }
    }

    @Nullable
    public UUID owner() {
        return owner;
    }

    protected abstract void serverTick(Level level, BlockPos pos, BlockState state);

    public static void tick(Level level, BlockPos pos, BlockState state, MachineBlockEntity machine) {
        if (!level.isClientSide) {
            machine.serverTick(level, pos, state);
        }
    }

    // --- Container ---

    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack result = ContainerHelper.removeItem(items, slot, amount);
        if (!result.isEmpty()) {
            setChanged();
        }
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) {
            stack.setCount(getMaxStackSize());
        }
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    // --- Сохранение ---

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items.clear();
        ContainerHelper.loadAllItems(tag, items, registries);
        progress = tag.getInt("Progress");
        burnTime = tag.getInt("BurnTime");
        burnDuration = tag.getInt("BurnDuration");
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putInt("Progress", progress);
        tag.putInt("BurnTime", burnTime);
        tag.putInt("BurnDuration", burnDuration);
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
    }
}
