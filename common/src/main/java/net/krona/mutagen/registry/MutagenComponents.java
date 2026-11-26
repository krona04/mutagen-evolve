package net.krona.mutagen.registry;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.krona.mutagen.Mutagen;
import net.krona.mutagen.data.GeneData;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;

public final class MutagenComponents {
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Mutagen.MOD_ID, Registries.DATA_COMPONENT_TYPE);

    /** Какой штамм и какой чистоты лежит в пробирке. */
    public static final RegistrySupplier<DataComponentType<GeneData>> GENE = COMPONENTS.register("gene",
            () -> DataComponentType.<GeneData>builder()
                    .persistent(GeneData.CODEC)
                    .networkSynchronized(GeneData.STREAM_CODEC)
                    .build());

    private MutagenComponents() {
    }

    public static void register() {
        COMPONENTS.register();
    }
}
