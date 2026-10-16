package net.krona.mutagen.knowledge;

import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Client-side copy of research: what the player themselves knows. Filled only by packets from the server,
 * so the class lives in common code — item tooltips and screens don't need to reach into client classes.
 */
public final class ClientKnowledge {
    private static volatile Map<ResourceLocation, Float> known = Map.of();

    private ClientKnowledge() {
    }

    public static synchronized void apply(boolean replace, Map<ResourceLocation, Float> entries) {
        Map<ResourceLocation, Float> next = replace ? new LinkedHashMap<>() : new LinkedHashMap<>(known);
        next.putAll(entries);
        known = Collections.unmodifiableMap(next);
    }

    /** Species research, or -1 if the species hasn't been encountered yet. */
    public static float research(ResourceLocation strain) {
        Float value = known.get(strain);
        return value == null ? -1.0F : value;
    }

    public static Map<ResourceLocation, Float> all() {
        return known;
    }
}
