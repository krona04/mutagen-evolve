package net.krona.mutagen.menu;

import net.krona.mutagen.block.MachineBlockEntity;
import net.krona.mutagen.block.SequencerBlockEntity;
import net.krona.mutagen.registry.MutagenBlocks;
import net.krona.mutagen.registry.MutagenItems;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class SequencerMenu extends MachineMenu {
    private static final int SLOTS = 2;

    public SequencerMenu(int id, Inventory inventory) {
        this(id, inventory, new SimpleContainer(SLOTS), new SimpleContainerData(MachineBlockEntity.DATA_COUNT));
    }

    public SequencerMenu(int id, Inventory inventory, Container container, ContainerData data) {
        super(MutagenBlocks.SEQUENCER_MENU.get(), id, inventory, container, data, SLOTS);

        addSlot(new Slot(container, SequencerBlockEntity.SLOT_SAMPLE, 35, 17) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(MutagenItems.SAMPLE.get());
            }
        });
        addSlot(new Slot(container, SequencerBlockEntity.SLOT_REAGENT, 35, 53) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return SequencerBlockEntity.isReagent(stack);
            }
        });

        addPlayerInventory(inventory);
    }

    /** Образец в слоте: экран по нему показывает, какой вид сейчас читается. */
    public ItemStack sample() {
        return container.getItem(SequencerBlockEntity.SLOT_SAMPLE);
    }
}
