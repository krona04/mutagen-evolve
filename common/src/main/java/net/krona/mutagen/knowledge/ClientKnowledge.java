package net.krona.mutagen.knowledge;

import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Копия изученности на клиенте: то, что знает сам игрок. Заполняется только пакетами с сервера,
 * поэтому класс живёт в общем коде — подсказкам предметов и экранам не нужно лезть в клиентские классы.
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

    /** Изученность вида или -1, если вид ещё не встречен. */
    public static float research(ResourceLocation strain) {
        Float value = known.get(strain);
        return value == null ? -1.0F : value;
    }

    public static Map<ResourceLocation, Float> all() {
        return known;
    }
}
