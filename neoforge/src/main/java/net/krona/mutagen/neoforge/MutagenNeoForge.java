package net.krona.mutagen.neoforge;

import dev.architectury.utils.Env;
import dev.architectury.utils.EnvExecutor;
import net.krona.mutagen.Mutagen;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(Mutagen.MOD_ID)
public final class MutagenNeoForge {
    public MutagenNeoForge(IEventBus modBus) {
        Mutagen.init();

        // Client listeners are attached when the mod is constructed: the screen registration event
        // fires before the deferred work of FMLClientSetupEvent runs.
        EnvExecutor.runInEnv(Env.CLIENT,
                () -> () -> net.krona.mutagen.neoforge.client.MutagenNeoForgeClient.init(modBus));
    }
}
