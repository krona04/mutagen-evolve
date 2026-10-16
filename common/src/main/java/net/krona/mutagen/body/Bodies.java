package net.krona.mutagen.body;

import com.mojang.logging.LogUtils;
import dev.architectury.platform.Platform;
import net.krona.mutagen.MutagenConfig;
import org.slf4j.Logger;

/**
 * Body layer selection. Pehkui is used if it is installed and not disabled in the config ({@code pehkuiIntegration}).
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
     * Whether the mod computes the hitbox itself. Queried from the dimensions mixin, including on the client: Pehkui is
     * on the client only if it is also on the server, so the answer matches on both sides.
     */
    public static boolean ownsDimensions() {
        return scaler().ownsDimensions();
    }
}
