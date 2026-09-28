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
 * Инструмент разработки: снимки экрана по команде из чата, чтобы проверять визуал стадий без рук.
 * <p>
 * Включается только файлом {@code mutagen_devshots.flag} в папке игры — у игроков его нет, и хук
 * не регистрируется вовсе. Строка чата вида {@code [devshot] имя вид} (вид — {@code front}, {@code back}
 * {@code first}, {@code inventory} или {@code turntable} — четыре ракурса сразу) ставит камеру, прячет интерфейс и через секунду сохраняет {@code screenshots/имя.png}.
 * Удобно слать её с сервера через {@code say} из RCON.
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
        // Команда say приходит то чатом игрока, то системным сообщением — слушаем оба.
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
        // Интерфейс прячется вместе с рукой, поэтому для вида от первого лица его не трогаем.
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
