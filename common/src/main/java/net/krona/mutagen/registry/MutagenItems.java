package net.krona.mutagen.registry;

import dev.architectury.registry.CreativeTabRegistry;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.krona.mutagen.Mutagen;
import net.krona.mutagen.item.GeneItem;
import net.krona.mutagen.item.ReversalSerumItem;
import net.krona.mutagen.item.SerumItem;
import net.krona.mutagen.item.StabilizerItem;
import net.krona.mutagen.item.SyringeItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class MutagenItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Mutagen.MOD_ID, Registries.ITEM);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Mutagen.MOD_ID, Registries.CREATIVE_MODE_TAB);

    public static final RegistrySupplier<Item> SYRINGE = ITEMS.register("syringe",
            () -> new SyringeItem(new Item.Properties().stacksTo(16), 0.0F, false));

    /** Золотой шприц не тратится на каждый образец и берёт материал бережнее. */
    public static final RegistrySupplier<Item> GOLDEN_SYRINGE = ITEMS.register("golden_syringe",
            () -> new SyringeItem(new Item.Properties().durability(32), 10.0F, true));

    public static final RegistrySupplier<Item> NETHERITE_SYRINGE = ITEMS.register("netherite_syringe",
            () -> new SyringeItem(new Item.Properties().durability(128).fireResistant(), 25.0F, true));

    public static final RegistrySupplier<Item> SAMPLE = ITEMS.register("sample",
            () -> new GeneItem(new Item.Properties().stacksTo(16)));

    public static final RegistrySupplier<Item> GENOME = ITEMS.register("genome",
            () -> new GeneItem(new Item.Properties().stacksTo(16)));

    public static final RegistrySupplier<Item> SERUM = ITEMS.register("serum",
            () -> new SerumItem(new Item.Properties().stacksTo(8)));

    public static final RegistrySupplier<Item> REVERSAL_SERUM = ITEMS.register("reversal_serum",
            () -> new ReversalSerumItem(new Item.Properties().stacksTo(8)));

    public static final RegistrySupplier<Item> STABILIZER = ITEMS.register("stabilizer",
            () -> new StabilizerItem(new Item.Properties().stacksTo(16)));

    public static final RegistrySupplier<Item> CARRIER_FLUID = ITEMS.register("carrier_fluid",
            () -> new Item(new Item.Properties().stacksTo(16)));

    public static final RegistrySupplier<Item> MUTAGEN_BASE = ITEMS.register("mutagen_base",
            () -> new Item(new Item.Properties().stacksTo(16)));

    public static final RegistrySupplier<Item> CATALYST = ITEMS.register("catalyst",
            () -> new Item(new Item.Properties().stacksTo(64)));

    public static final RegistrySupplier<CreativeModeTab> TAB = TABS.register("general",
            () -> CreativeTabRegistry.create(builder -> builder
                    .title(Component.translatable("itemGroup.mutagen.general"))
                    .icon(() -> new ItemStack(SYRINGE.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(SYRINGE.get());
                        output.accept(GOLDEN_SYRINGE.get());
                        output.accept(NETHERITE_SYRINGE.get());
                        output.accept(SAMPLE.get());
                        output.accept(GENOME.get());
                        output.accept(SERUM.get());
                        output.accept(REVERSAL_SERUM.get());
                        output.accept(STABILIZER.get());
                        output.accept(CARRIER_FLUID.get());
                        output.accept(MUTAGEN_BASE.get());
                        output.accept(CATALYST.get());
                        output.accept(MutagenBlocks.BIO_WORKBENCH_ITEM.get());
                        output.accept(MutagenBlocks.CENTRIFUGE_ITEM.get());
                        output.accept(MutagenBlocks.SYNTHESIZER_ITEM.get());
                        output.accept(MutagenBlocks.SEQUENCER_ITEM.get());
                    })));

    private MutagenItems() {
    }

    public static void register() {
        ITEMS.register();
        TABS.register();
    }
}
