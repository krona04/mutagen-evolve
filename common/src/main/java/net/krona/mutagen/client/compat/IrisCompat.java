package net.krona.mutagen.client.compat;

import com.mojang.logging.LogUtils;
import dev.architectury.platform.Platform;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.slf4j.Logger;

import java.lang.reflect.Method;

/**
 * Iris без зависимости от Iris. Наши слои поверх скина полупрозрачные: в проходе теней шейдеров
 * они ничего не дают, кроме лишней работы и тёмных ореолов, поэтому там они пропускаются.
 * <p>
 * API берётся через рефлексию ({@code net.irisshaders.iris.api.v0.IrisApi}): Iris ставится и на Fabric,
 * и на NeoForge, а мод собирается без него.
 */
@Environment(EnvType.CLIENT)
public final class IrisCompat {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static Object api;
    private static Method shadowPass;
    private static boolean resolved;

    private IrisCompat() {
    }

    private static void resolve() {
        resolved = true;
        if (!Platform.isModLoaded("iris") && !Platform.isModLoaded("oculus")) {
            return;
        }
        try {
            Class<?> type = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            api = type.getMethod("getInstance").invoke(null);
            shadowPass = type.getMethod("isRenderingShadowPass");
            LOGGER.info("[Mutagen] Iris detected: mutation overlays skip the shadow pass");
        } catch (Throwable e) {
            api = null;
            LOGGER.warn("[Mutagen] Iris is installed, but its API was not recognised", e);
        }
    }

    /** Идёт ли сейчас проход теней шейдерпака. Без Iris — всегда нет. */
    public static boolean isShadowPass() {
        if (!resolved) {
            resolve();
        }
        if (api == null) {
            return false;
        }
        try {
            return (boolean) shadowPass.invoke(api);
        } catch (Throwable e) {
            api = null;
            return false;
        }
    }
}
