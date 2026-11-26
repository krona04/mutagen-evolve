package net.krona.mutagen.body;

import com.mojang.logging.LogUtils;
import dev.architectury.platform.Platform;
import net.krona.mutagen.MutagenConfig;
import org.slf4j.Logger;

/**
 * Выбор прослойки тела. Pehkui берётся, если он установлен и не выключен в конфиге ({@code pehkuiIntegration}).
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
     * Считает ли мод хитбокс сам. Спрашивается из миксина размеров, в том числе на клиенте: Pehkui на
     * клиенте стоит, только если стоит и на сервере, так что ответ на обеих сторонах совпадает.
     */
    public static boolean ownsDimensions() {
        return scaler().ownsDimensions();
    }
}
