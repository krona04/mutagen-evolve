package net.krona.mutagen.strain;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import net.krona.mutagen.Mutagen;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Trait type: the name in the {@code "type"} field and the format of the other fields.
 * <p>
 * Its own registry rather than a vanilla one: traits live only inside strains, and there's no point syncing
 * a separate registry with the client. Addons register their types via {@link #register}.
 */
public record TraitType<T extends Trait>(ResourceLocation id, MapCodec<T> codec) {
    private static final Map<ResourceLocation, TraitType<?>> TYPES = new LinkedHashMap<>();

    public static final Codec<TraitType<?>> CODEC = ResourceLocation.CODEC.comapFlatMap(TraitType::lookup,
            TraitType::id);

    public static synchronized <T extends Trait> TraitType<T> register(ResourceLocation id, MapCodec<T> codec) {
        TraitType<T> type = new TraitType<>(id, codec);
        if (TYPES.putIfAbsent(id, type) != null) {
            throw new IllegalStateException("Duplicate trait type " + id);
        }
        return type;
    }

    static <T extends Trait> TraitType<T> register(String name, MapCodec<T> codec) {
        return register(Mutagen.id(name), codec);
    }

    public static Collection<TraitType<?>> all() {
        return Collections.unmodifiableCollection(TYPES.values());
    }

    private static DataResult<TraitType<?>> lookup(ResourceLocation id) {
        TraitType<?> type = TYPES.get(id);
        // Short form: "type": "sun_burn" instead of "mutagen:sun_burn".
        if (type == null && id.getNamespace().equals(ResourceLocation.DEFAULT_NAMESPACE)) {
            type = TYPES.get(Mutagen.id(id.getPath()));
        }
        if (type == null) {
            return DataResult.error(() -> "Unknown trait type: " + id);
        }
        return DataResult.success(type);
    }
}
