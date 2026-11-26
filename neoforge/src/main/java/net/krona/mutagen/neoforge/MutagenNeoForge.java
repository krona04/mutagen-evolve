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

        // Клиентские слушатели вешаются при конструировании мода: событие регистрации
        // экранов проходит раньше, чем отрабатывает отложенная работа FMLClientSetupEvent.
        EnvExecutor.runInEnv(Env.CLIENT,
                () -> () -> net.krona.mutagen.neoforge.client.MutagenNeoForgeClient.init(modBus));
    }
}
