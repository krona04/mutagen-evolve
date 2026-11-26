package net.krona.mutagen.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.architectury.event.events.client.ClientGuiEvent;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.networking.NetworkManager;
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
import net.krona.mutagen.network.MutagenNetwork;
import net.krona.mutagen.registry.MutagenBlocks;
import net.krona.mutagen.update.UpdateChecker;
import net.krona.mutagen.update.UpdateNotifier;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public final class MutagenClient {
    /** Гено-древо: каталог встреченных видов. По умолчанию G — в ванилле эта клавиша свободна. */
    public static final KeyMapping GENE_TREE_KEY = new KeyMapping("key.mutagen.gene_tree",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.mutagen");
    /** Две обычные способности и апекс. R, V и B в ванилле свободны. */
    public static final KeyMapping[] ABILITY_KEYS = {
            new KeyMapping("key.mutagen.ability_1", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R,
                    "key.categories.mutagen"),
            new KeyMapping("key.mutagen.ability_2", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V,
                    "key.categories.mutagen")
    };
    public static final KeyMapping APEX_KEY = new KeyMapping("key.mutagen.apex",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, "key.categories.mutagen");
    public static final KeyMapping VISION_KEY = new KeyMapping("key.mutagen.mob_vision",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, "key.categories.mutagen");

    private MutagenClient() {
    }

    /** Интерфейс, клавиши и клиентский тик. Безопасно вызывать в любой момент запуска. */
    public static void init() {
        UpdateChecker.start();
        UpdateNotifier.init();
        net.krona.mutagen.update.SupportNotifier.init();
        KeyMappingRegistry.register(GENE_TREE_KEY);
        for (KeyMapping key : ABILITY_KEYS) {
            KeyMappingRegistry.register(key);
        }
        KeyMappingRegistry.register(APEX_KEY);
        KeyMappingRegistry.register(VISION_KEY);
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
        for (int slot = 0; slot < ABILITY_KEYS.length; slot++) {
            while (ABILITY_KEYS[slot].consumeClick()) {
                requestAbility(minecraft, ClientAbilities.indexForKey(slot));
            }
        }
        while (APEX_KEY.consumeClick()) {
            requestAbility(minecraft, ClientAbilities.apexIndex());
        }
        while (VISION_KEY.consumeClick()) {
            MobVision.toggle(minecraft);
        }
        MobVision.tick(minecraft);
    }

    /** Решение принимает сервер: клиент только просит и ничего не предсказывает. */
    private static void requestAbility(Minecraft minecraft, int index) {
        if (index < 0 || minecraft.player == null || minecraft.screen != null) {
            return;
        }
        NetworkManager.sendToServer(new MutagenNetwork.UseAbilityPayload(index));
    }

    /**
     * Экраны машин.
     * <p>
     * Вызывать только там, где загрузчик ещё принимает регистрацию экранов: на Fabric это
     * клиентская точка входа, на NeoForge — собственное событие {@code RegisterMenuScreensEvent},
     * поэтому там регистрация идёт мимо этого метода.
     */
    public static void registerScreens() {
        MenuRegistry.registerScreenFactory(MutagenBlocks.CENTRIFUGE_MENU.get(), CentrifugeScreen::new);
        MenuRegistry.registerScreenFactory(MutagenBlocks.SYNTHESIZER_MENU.get(), SynthesizerScreen::new);
        MenuRegistry.registerScreenFactory(MutagenBlocks.SEQUENCER_MENU.get(), SequencerScreen::new);
        MenuRegistry.registerScreenFactory(MutagenBlocks.BIO_WORKBENCH_MENU.get(), BioWorkbenchScreen::new);
    }
}
