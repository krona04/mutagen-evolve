package net.krona.mutagen.body;

import com.mojang.logging.LogUtils;
import dev.architectury.platform.Platform;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Сводка о модах, с которыми Mutagen умеет или обязан уживаться (0.3.1). Ничего не меняет — только
 * пишет в лог, что найдено и как мод себя поведёт, чтобы в отчёте об ошибке это было видно сразу.
 */
public final class Compat {
    private static final Logger LOGGER = LogUtils.getLogger();

    private Compat() {
    }

    public static void report() {
        List<String> found = new ArrayList<>();
        check(found, "pehkui", "Pehkui — body size, eyes, reach and inertia go through it");
        check(found, "sodium", "Sodium — vanilla model rendering, nothing to adapt");
        check(found, "embeddium", "Embeddium — vanilla model rendering, nothing to adapt");
        check(found, "iris", "Iris — mutation overlays skip the shadow pass");
        check(found, "oculus", "Oculus — mutation overlays skip the shadow pass");
        check(found, "skinlayers3d", "3D Skin Layers — hidden skin layers stay hidden under the tint");
        check(found, "waveycapes", "Wavey Capes — the cape disappears with the rest of the player in full form");
        check(found, "figura", "Figura — an avatar may replace the hybrid and full form; turn off "
                + "hybridRender/fullFormRender if they fight");
        if (found.isEmpty()) {
            LOGGER.info("[Mutagen] Compat: no known render or size mods found");
            return;
        }
        for (String line : found) {
            LOGGER.info("[Mutagen] Compat: {}", line);
        }
    }

    private static void check(List<String> found, String id, String note) {
        if (Platform.isModLoaded(id)) {
            found.add(note);
        }
    }
}
