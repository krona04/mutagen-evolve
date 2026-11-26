package net.krona.mutagen.block;

import net.krona.mutagen.data.GeneData;
import net.krona.mutagen.item.GeneItem;
import net.krona.mutagen.registry.MutagenBlocks;
import net.krona.mutagen.registry.MutagenItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.krona.mutagen.menu.CentrifugeMenu;
import org.jetbrains.annotations.Nullable;

/**
 * Центрифуга: из сырого образца выделяет чистый геном. Работает на обычном топливе.
 */
public class CentrifugeBlockEntity extends MachineBlockEntity {
    public static final int SLOT_INPUT = 0;
    public static final int SLOT_FUEL = 1;
    public static final int SLOT_OUTPUT = 2;
    private static final int WORK_TIME = 200;

    public CentrifugeBlockEntity(BlockPos pos, BlockState state) {
        super(MutagenBlocks.CENTRIFUGE_ENTITY.get(), pos, state, 3, WORK_TIME);
    }

    /** Центрифуга очищает материал, но часть его теряет. */
    public static ItemStack result(ItemStack input) {
        if (!input.is(MutagenItems.SAMPLE.get())) {
            return ItemStack.EMPTY;
        }
        GeneData gene = GeneItem.gene(input);
        if (gene == null) {
            return ItemStack.EMPTY;
        }
        float purity = Math.min(100.0F, gene.quality() * 0.95F + 8.0F);
        return GeneItem.withGene(new ItemStack(MutagenItems.GENOME.get()), gene.withQuality(purity));
    }

    @Override
    protected void serverTick(Level level, BlockPos pos, BlockState state) {
        boolean changed = false;
        boolean wasLit = burnTime > 0;

        if (burnTime > 0) {
            burnTime--;
        }

        ItemStack input = items.get(SLOT_INPUT);
        ItemStack fuel = items.get(SLOT_FUEL);
        ItemStack output = items.get(SLOT_OUTPUT);
        ItemStack result = result(input);
        boolean canWork = !result.isEmpty() && canAccept(output, result);

        if (canWork && burnTime == 0 && !fuel.isEmpty()) {
            int fuelTime = AbstractFurnaceBlockEntity.getFuel().getOrDefault(fuel.getItem(), 0);
            if (fuelTime > 0) {
                burnTime = fuelTime;
                burnDuration = fuelTime;
                fuel.shrink(1);
                changed = true;
            }
        }

        if (canWork && burnTime > 0) {
            progress++;
            if (progress >= maxProgress) {
                progress = 0;
                input.shrink(1);
                if (output.isEmpty()) {
                    items.set(SLOT_OUTPUT, result);
                } else {
                    output.grow(result.getCount());
                }
            }
            changed = true;
        } else if (progress != 0) {
            progress = 0;
            changed = true;
        }

        boolean lit = burnTime > 0;
        if (lit != wasLit) {
            level.setBlock(pos, state.setValue(CentrifugeBlock.LIT, lit), 3);
            changed = true;
        }
        if (changed) {
            setChanged();
        }
    }

    static boolean canAccept(ItemStack output, ItemStack result) {
        if (output.isEmpty()) {
            return true;
        }
        if (!ItemStack.isSameItemSameComponents(output, result)) {
            return false;
        }
        return output.getCount() + result.getCount() <= output.getMaxStackSize();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.mutagen.centrifuge");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new CentrifugeMenu(id, inventory, this, data);
    }
}
