package net.krona.mutagen.ability;

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
 * An ability's {@code "type"} name and the schema for its other fields.
 * <p>
 * Like {@link net.krona.mutagen.strain.TraitType}, this uses its own registry. Abilities already travel
 * with their strains, so they need no separate client sync.
 */
public record AbilityType<T extends Ability>(ResourceLocation id, MapCodec<T> codec) {
    private static final Map<ResourceLocation, AbilityType<?>> TYPES = new LinkedHashMap<>();

    public static final Codec<AbilityType<?>> CODEC = ResourceLocation.CODEC.comapFlatMap(AbilityType::lookup,
            AbilityType::id);

    public static synchronized <T extends Ability> AbilityType<T> register(ResourceLocation id, MapCodec<T> codec) {
        AbilityType<T> type = new AbilityType<>(id, codec);
        if (TYPES.putIfAbsent(id, type) != null) {
            throw new IllegalStateException("Duplicate ability type " + id);
        }
        return type;
    }

    static <T extends Ability> AbilityType<T> register(String name, MapCodec<T> codec) {
        return register(Mutagen.id(name), codec);
    }

    public static Collection<AbilityType<?>> all() {
        return Collections.unmodifiableCollection(TYPES.values());
    }

    private static DataResult<AbilityType<?>> lookup(ResourceLocation id) {
        AbilityType<?> type = TYPES.get(id);
        // Use the short form "type": "leap" in place of "mutagen:leap".
        if (type == null && id.getNamespace().equals(ResourceLocation.DEFAULT_NAMESPACE)) {
            type = TYPES.get(Mutagen.id(id.getPath()));
        }
        if (type == null) {
            return DataResult.error(() -> "Unknown ability type: " + id);
        }
        return DataResult.success(type);
    }
}
