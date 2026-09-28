package net.krona.mutagen;

import dev.architectury.registry.ReloadListenerRegistry;
import net.krona.mutagen.body.Compat;
import net.krona.mutagen.command.MutagenCommand;
import net.krona.mutagen.event.MutagenEvents;
import net.krona.mutagen.network.MutagenNetwork;
import net.krona.mutagen.registry.MutagenBlocks;
import net.krona.mutagen.registry.MutagenComponents;
import net.krona.mutagen.registry.MutagenItems;
import net.krona.mutagen.registry.MutagenRecipes;
import net.krona.mutagen.strain.StrainLoader;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;

public final class Mutagen {
    public static final String MOD_ID = "mutagen";

    private Mutagen() {
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public static void init() {
        MutagenConfig.load();
        Compat.report();

        MutagenComponents.register();
        // Блоки идут первыми: предметы блоков ссылаются на них при создании.
        MutagenBlocks.register();
        MutagenItems.register();
        MutagenRecipes.register();

        // Штаммы приходят из датапаков: при запуске сервера и при каждом /reload.
        ReloadListenerRegistry.register(PackType.SERVER_DATA, new StrainLoader(), id("strains"));

        MutagenNetwork.init();
        MutagenEvents.init();
        MutagenCommand.init();
    }
}
