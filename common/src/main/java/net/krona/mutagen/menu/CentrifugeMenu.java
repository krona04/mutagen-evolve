package net.krona.mutagen.menu;

import net.krona.mutagen.block.MachineBlockEntity;
import net.krona.mutagen.block.CentrifugeBlockEntity;
import net.krona.mutagen.registry.MutagenBlocks;
import net.krona.mutagen.registry.MutagenItems;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

public class CentrifugeMenu extends MachineMenu {
    private static final int SLOTS = 3;

    public CentrifugeMenu(int id, Inventory inventory) {
        this(id, inventory, new SimpleContainer(SLOTS), new SimpleContainerData(MachineBlockEntity.DATA_COUNT));
    }

    public CentrifugeMenu(int id, Inventory inventory, Container container, ContainerData data) {
        super(MutagenBlocks.CENTRIFUGE_MENU.get(), id, inventory, container, data, SLOTS);

        addSlot(new Slot(container, CentrifugeBlockEntity.SLOT_INPUT, 56, 17) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(MutagenItems.SAMPLE.get());
            }
        });
        addSlot(new Slot(container, CentrifugeBlockEntity.SLOT_FUEL, 56, 53) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return AbstractFurnaceBlockEntity.isFuel(stack);
            }
        });
        addSlot(new Slot(container, CentrifugeBlockEntity.SLOT_OUTPUT, 116, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public void onTake(Player player, ItemStack stack) {
                super.onTake(player, stack);
            }
        });

        addPlayerInventory(inventory);
    }
}
