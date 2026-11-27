package net.krona.mutagen.neoforge.client;

import net.krona.mutagen.client.MutagenClient;
import net.krona.mutagen.client.screen.BioWorkbenchScreen;
import net.krona.mutagen.client.screen.CentrifugeScreen;
import net.krona.mutagen.client.screen.SequencerScreen;
import net.krona.mutagen.client.screen.SynthesizerScreen;
import net.krona.mutagen.registry.MutagenBlocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * NeoForge client initialization.
 * <p>
 * Register screens directly in {@link RegisterMenuScreensEvent}. Architectury would add a listener for the
 * same event, which has already fired by {@code FMLClientSetupEvent}, so the menu would fail to open.
 */
public final class MutagenNeoForgeClient {
    private MutagenNeoForgeClient() {
    }

    public static void init(IEventBus modBus) {
        MutagenClient.init();
        modBus.addListener(RegisterMenuScreensEvent.class, event -> {
            event.register(MutagenBlocks.CENTRIFUGE_MENU.get(), CentrifugeScreen::new);
            event.register(MutagenBlocks.SYNTHESIZER_MENU.get(), SynthesizerScreen::new);
            event.register(MutagenBlocks.SEQUENCER_MENU.get(), SequencerScreen::new);
            event.register(MutagenBlocks.BIO_WORKBENCH_MENU.get(), BioWorkbenchScreen::new);
        });
    }
}
