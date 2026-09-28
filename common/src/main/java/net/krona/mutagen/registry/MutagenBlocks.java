package net.krona.mutagen.registry;

import dev.architectury.registry.menu.MenuRegistry;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.krona.mutagen.Mutagen;
import net.krona.mutagen.block.BioWorkbenchBlock;
import net.krona.mutagen.block.CentrifugeBlock;
import net.krona.mutagen.block.CentrifugeBlockEntity;
import net.krona.mutagen.block.SequencerBlock;
import net.krona.mutagen.block.SequencerBlockEntity;
import net.krona.mutagen.block.SynthesizerBlock;
import net.krona.mutagen.block.SynthesizerBlockEntity;
import net.krona.mutagen.menu.BioWorkbenchMenu;
import net.krona.mutagen.menu.CentrifugeMenu;
import net.krona.mutagen.menu.SequencerMenu;
import net.krona.mutagen.menu.SynthesizerMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public final class MutagenBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Mutagen.MOD_ID, Registries.BLOCK);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Mutagen.MOD_ID, Registries.BLOCK_ENTITY_TYPE);
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Mutagen.MOD_ID, Registries.MENU);

    private static BlockBehaviour.Properties labProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(3.0F, 6.0F)
                .sound(SoundType.METAL)
                .requiresCorrectToolForDrops();
    }

    public static final RegistrySupplier<Block> BIO_WORKBENCH = BLOCKS.register("bio_workbench",
            () -> new BioWorkbenchBlock(labProperties()));
    public static final RegistrySupplier<Block> CENTRIFUGE = BLOCKS.register("centrifuge",
            () -> new CentrifugeBlock(labProperties()));
    public static final RegistrySupplier<Block> SYNTHESIZER = BLOCKS.register("synthesizer",
            () -> new SynthesizerBlock(labProperties()));
    public static final RegistrySupplier<Block> SEQUENCER = BLOCKS.register("sequencer",
            () -> new SequencerBlock(labProperties().lightLevel(state -> state.getValue(SequencerBlock.LIT) ? 7 : 0)));

    public static final RegistrySupplier<Item> BIO_WORKBENCH_ITEM = MutagenItems.ITEMS.register("bio_workbench",
            () -> new BlockItem(BIO_WORKBENCH.get(), new Item.Properties()));
    public static final RegistrySupplier<Item> CENTRIFUGE_ITEM = MutagenItems.ITEMS.register("centrifuge",
            () -> new BlockItem(CENTRIFUGE.get(), new Item.Properties()));
    public static final RegistrySupplier<Item> SYNTHESIZER_ITEM = MutagenItems.ITEMS.register("synthesizer",
            () -> new BlockItem(SYNTHESIZER.get(), new Item.Properties()));
    public static final RegistrySupplier<Item> SEQUENCER_ITEM = MutagenItems.ITEMS.register("sequencer",
            () -> new BlockItem(SEQUENCER.get(), new Item.Properties()));

    public static final RegistrySupplier<BlockEntityType<CentrifugeBlockEntity>> CENTRIFUGE_ENTITY =
            BLOCK_ENTITIES.register("centrifuge", () -> BlockEntityType.Builder
                    .of(CentrifugeBlockEntity::new, CENTRIFUGE.get())
                    .build(null));
    public static final RegistrySupplier<BlockEntityType<SynthesizerBlockEntity>> SYNTHESIZER_ENTITY =
            BLOCK_ENTITIES.register("synthesizer", () -> BlockEntityType.Builder
                    .of(SynthesizerBlockEntity::new, SYNTHESIZER.get())
                    .build(null));
    public static final RegistrySupplier<BlockEntityType<SequencerBlockEntity>> SEQUENCER_ENTITY =
            BLOCK_ENTITIES.register("sequencer", () -> BlockEntityType.Builder
                    .of(SequencerBlockEntity::new, SEQUENCER.get())
                    .build(null));

    public static final RegistrySupplier<MenuType<CentrifugeMenu>> CENTRIFUGE_MENU =
            MENUS.register("centrifuge", () -> MenuRegistry.of(CentrifugeMenu::new));
    public static final RegistrySupplier<MenuType<SynthesizerMenu>> SYNTHESIZER_MENU =
            MENUS.register("synthesizer", () -> MenuRegistry.of(SynthesizerMenu::new));
    public static final RegistrySupplier<MenuType<SequencerMenu>> SEQUENCER_MENU =
            MENUS.register("sequencer", () -> MenuRegistry.of(SequencerMenu::new));
    public static final RegistrySupplier<MenuType<BioWorkbenchMenu>> BIO_WORKBENCH_MENU =
            MENUS.register("bio_workbench", () -> MenuRegistry.of(BioWorkbenchMenu::new));

    private MutagenBlocks() {
    }

    public static void register() {
        BLOCKS.register();
        BLOCK_ENTITIES.register();
        MENUS.register();
    }
}
