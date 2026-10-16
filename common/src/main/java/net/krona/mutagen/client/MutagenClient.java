package net.krona.mutagen.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.architectury.event.events.client.ClientGuiEvent;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.registry.ReloadListenerRegistry;
import dev.architectury.registry.client.keymappings.KeyMappingRegistry;
import dev.architectury.registry.menu.MenuRegistry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.krona.mutagen.client.body.InfectionTextures;
import net.krona.mutagen.client.screen.BioWorkbenchScreen;
import net.krona.mutagen.client.screen.CentrifugeScreen;
import net.krona.mutagen.client.screen.GeneTreeScreen;
import net.krona.mutagen.client.screen.SequencerScreen;
import net.krona.mutagen.client.screen.SynthesizerScreen;
import net.krona.mutagen.registry.MutagenBlocks;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public final class MutagenClient {
    /** Gene tree: catalog of encountered species. G by default — that key is free in vanilla. */
    public static final KeyMapping GENE_TREE_KEY = new KeyMapping("key.mutagen.gene_tree",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.mutagen");

    private MutagenClient() {
    }

    /** HUD, keys and client tick. Safe to call at any point during startup. */
    public static void init() {
        KeyMappingRegistry.register(GENE_TREE_KEY);
        ClientGuiEvent.RENDER_HUD.register(MutagenHud::render);
        ClientGuiEvent.RENDER_CONTAINER_FOREGROUND.register(LockedSlots::renderContainer);
        ClientTickEvent.CLIENT_POST.register(MutagenClient::tick);
        DevShots.init();
        ReloadListenerRegistry.register(PackType.CLIENT_RESOURCES,
                (ResourceManagerReloadListener) manager -> InfectionTextures.reset());
    }

    private static void tick(Minecraft minecraft) {
        MutagenClientState.tick();
        InfectionTextures.tick();
        LockedSlots.tick(minecraft);
        while (GENE_TREE_KEY.consumeClick()) {
            if (minecraft.screen == null && minecraft.player != null) {
                minecraft.setScreen(new GeneTreeScreen());
            }
        }
    }

    /**
     * Machine screens.
     * <p>
     * Call only where the loader still accepts screen registration: on Fabric this is
     * the client entrypoint, on NeoForge its own {@code RegisterMenuScreensEvent},
     * so registration there bypasses this method.
     */
    public static void registerScreens() {
        MenuRegistry.registerScreenFactory(MutagenBlocks.CENTRIFUGE_MENU.get(), CentrifugeScreen::new);
        MenuRegistry.registerScreenFactory(MutagenBlocks.SYNTHESIZER_MENU.get(), SynthesizerScreen::new);
        MenuRegistry.registerScreenFactory(MutagenBlocks.SEQUENCER_MENU.get(), SequencerScreen::new);
        MenuRegistry.registerScreenFactory(MutagenBlocks.BIO_WORKBENCH_MENU.get(), BioWorkbenchScreen::new);
    }
}
