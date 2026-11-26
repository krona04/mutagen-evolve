package net.krona.mutagen.menu;

import net.krona.mutagen.block.MachineBlockEntity;
import net.krona.mutagen.block.SynthesizerBlockEntity;
import net.krona.mutagen.registry.MutagenBlocks;
import net.krona.mutagen.registry.MutagenItems;
import net.krona.mutagen.strain.Strains;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class SynthesizerMenu extends MachineMenu {
    private static final int SLOTS = 4;

    public SynthesizerMenu(int id, Inventory inventory) {
        this(id, inventory, new SimpleContainer(SLOTS), new SimpleContainerData(MachineBlockEntity.DATA_COUNT));
    }

    public SynthesizerMenu(int id, Inventory inventory, Container container, ContainerData data) {
        super(MutagenBlocks.SYNTHESIZER_MENU.get(), id, inventory, container, data, SLOTS);

        addSlot(new Slot(container, SynthesizerBlockEntity.SLOT_GENOME, 44, 17) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(MutagenItems.GENOME.get());
            }
        });
        addSlot(new Slot(container, SynthesizerBlockEntity.SLOT_BASE, 44, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(MutagenItems.MUTAGEN_BASE.get());
            }
        });
        addSlot(new Slot(container, SynthesizerBlockEntity.SLOT_CATALYST, 44, 53) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return Strains.isCatalyst(stack);
            }
        });
        addSlot(new Slot(container, SynthesizerBlockEntity.SLOT_OUTPUT, 116, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });

        addPlayerInventory(inventory);
    }

    /** Содержимое слота машины: экран по нему объясняет, почему синтез стоит. */
    public ItemStack machineItem(int slot) {
        return container.getItem(slot);
    }
}
