package net.krona.mutagen.body;

import com.mojang.logging.LogUtils;
import dev.architectury.platform.Platform;
import net.krona.mutagen.MutagenConfig;
import org.slf4j.Logger;

/**
 * Select the body scaler. Use Pehkui if it is installed and enabled by {@code pehkuiIntegration}.
 */
public final class Bodies {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile BodyScaler scaler;

    private Bodies() {
    }

    public static BodyScaler scaler() {
        BodyScaler current = scaler;
        if (current == null) {
            synchronized (Bodies.class) {
                if (scaler == null) {
                    scaler = choose();
                    LOGGER.info("[Mutagen] Body scaling: {}", scaler.name());
                }
                current = scaler;
            }
        }
        return current;
    }

    private static BodyScaler choose() {
        if (MutagenConfig.get().pehkuiIntegration && Platform.isModLoaded("pehkui")) {
            PehkuiBodyScaler pehkui = PehkuiBodyScaler.create();
            if (pehkui != null) {
                return pehkui;
            }
        }
        return new VanillaBodyScaler();
    }

    /**
     * Whether Mutagen calculates the hitbox. The player dimensions mixin checks on both sides. If the
     * server uses Pehkui, the client also has it, so the result matches.
     */
    public static boolean ownsDimensions() {
        return scaler().ownsDimensions();
    }
}
