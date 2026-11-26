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
 * Клиентская часть под NeoForge.
 * <p>
 * Экраны здесь регистрируются напрямую в {@link RegisterMenuScreensEvent}. Через Architectury это
 * делать нельзя: её реализация лишь вешает слушателя на то же событие, а из {@code FMLClientSetupEvent}
 * оно уже прошло — экран не регистрируется, и меню молча не открывается.
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
