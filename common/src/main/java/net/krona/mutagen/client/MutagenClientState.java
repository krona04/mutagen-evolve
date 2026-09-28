package net.krona.mutagen.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.krona.mutagen.strain.Stage;

/**
 * Клиентское состояние: вспышка на экране при переходе между стадиями.
 */
@Environment(EnvType.CLIENT)
public final class MutagenClientState {
    private static final int FLASH_TICKS = 30;

    private static int flash;
    private static int flashColor = 0xFFFFFF;
    private static Stage lastStage = Stage.NONE;
    private static boolean lastUp = true;

    private MutagenClientState() {
    }

    public static void onStageChanged(Stage stage, boolean up, int color) {
        lastStage = stage;
        lastUp = up;
        flashColor = color;
        flash = FLASH_TICKS;
    }

    public static void tick() {
        if (flash > 0) {
            flash--;
        }
    }

    public static float flashAlpha() {
        return flash <= 0 ? 0.0F : (float) flash / FLASH_TICKS;
    }

    public static int flashColor() {
        return flashColor;
    }

    public static Stage lastStage() {
        return lastStage;
    }

    public static boolean lastUp() {
        return lastUp;
    }
}
