package net.krona.mutagen.client.compat;

import com.mojang.logging.LogUtils;
import dev.architectury.platform.Platform;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.slf4j.Logger;

import java.lang.reflect.Method;

/**
 * Iris without depending on Iris. Our layers over the skin are translucent: in the shaders' shadow pass
 * they contribute nothing but extra work and dark halos, so they are skipped there.
 * <p>
 * The API is obtained via reflection ({@code net.irisshaders.iris.api.v0.IrisApi}): Iris is available on both Fabric
 * and NeoForge, and the mod builds without it.
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

    /** Whether the shader pack's shadow pass is running right now. Without Iris — always false. */
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
