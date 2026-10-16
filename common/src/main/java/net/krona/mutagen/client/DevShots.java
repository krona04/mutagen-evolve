package net.krona.mutagen.client;

import com.mojang.logging.LogUtils;
import dev.architectury.event.CompoundEventResult;
import dev.architectury.event.events.client.ClientChatEvent;
import dev.architectury.event.events.client.ClientSystemMessageEvent;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.platform.Platform;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.slf4j.Logger;

import java.nio.file.Files;

/**
 * Development tool: screenshots on a chat command, to check stage visuals hands-free.
 * <p>
 * Enabled only by the {@code mutagen_devshots.flag} file in the game folder — players don't have it, and the hook
 * isn't registered at all. A chat line like {@code [devshot] name view} (view — {@code front}, {@code back}
 * {@code first}, {@code inventory} or {@code turntable} — four angles at once) positions the camera, hides the HUD and saves {@code screenshots/name.png} a second later.
 * Convenient to send from the server via {@code say} over RCON.
 */
@Environment(EnvType.CLIENT)
public final class DevShots {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String MARKER = "[devshot]";
    private static final int DELAY_TICKS = 20;

    private static String pendingName;
    private static int countdown;
    private static CameraType previousCamera;
    private static boolean previousHideGui;
    private static boolean openedScreen;

    private DevShots() {
    }

    public static void init() {
        if (!Files.exists(Platform.getGameFolder().resolve("mutagen_devshots.flag"))) {
            return;
        }
        LOGGER.warn("[Mutagen] Dev screenshots are enabled (mutagen_devshots.flag)");
        // The say command arrives sometimes as player chat, sometimes as a system message — listen to both.
        ClientChatEvent.RECEIVED.register((type, message) -> {
            onMessage(message.getString());
            return CompoundEventResult.pass();
        });
        ClientSystemMessageEvent.RECEIVED.register(message -> {
            onMessage(message.getString());
            return CompoundEventResult.pass();
        });
        ClientTickEvent.CLIENT_POST.register(DevShots::tick);
    }

    private static void onMessage(String text) {
        int at = text.indexOf(MARKER);
        if (at >= 0) {
            request(text.substring(at + MARKER.length()).trim().split("\\s+"));
        }
    }

    private static void request(String[] args) {
        Minecraft minecraft = Minecraft.getInstance();
        if (args.length == 0 || args[0].isEmpty()) {
            return;
        }
        previousCamera = minecraft.options.getCameraType();
        previousHideGui = minecraft.options.hideGui;
        String view = args.length > 1 ? args[1] : "front";
        minecraft.options.setCameraType(switch (view) {
            case "back" -> CameraType.THIRD_PERSON_BACK;
            case "first" -> CameraType.FIRST_PERSON;
            default -> CameraType.THIRD_PERSON_FRONT;
        });
        // The HUD is hidden together with the hand, so for the first-person view we leave it alone.
        minecraft.options.hideGui = !"first".equals(view) && !"inventory".equals(view) && !"turntable".equals(view);
        if ("inventory".equals(view) && minecraft.player != null) {
            minecraft.setScreen(new InventoryScreen(minecraft.player));
            openedScreen = true;
        }
        if ("turntable".equals(view) && minecraft.player != null) {
            minecraft.setScreen(new DevTurntableScreen(minecraft.player));
            openedScreen = true;
        }
        pendingName = args[0].replaceAll("[^A-Za-z0-9_\\-]", "_");
        countdown = DELAY_TICKS;
    }

    private static void tick(Minecraft minecraft) {
        if (pendingName == null || --countdown > 0) {
            return;
        }
        String name = pendingName;
        pendingName = null;
        Screenshot.grab(minecraft.gameDirectory, name + ".png", minecraft.getMainRenderTarget(),
                message -> LOGGER.info("[Mutagen] Dev screenshot {}: {}", name, message.getString()));
        minecraft.options.setCameraType(previousCamera);
        minecraft.options.hideGui = previousHideGui;
        if (openedScreen) {
            minecraft.setScreen(null);
            openedScreen = false;
        }
    }
}
