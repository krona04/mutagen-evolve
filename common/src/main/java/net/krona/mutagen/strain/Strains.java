package net.krona.mutagen.strain;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Загруженные штаммы.
 * <p>
 * Набор заменяется целиком одним снимком: при перезагрузке датапака сервер и клиент в одиночной игре
 * видят либо старый набор, либо новый, но никогда не половину. Штаммы в Java больше не пишутся —
 * их даёт {@link StrainLoader} из датапаков, а на клиент они приезжают пакетом.
 */
public final class Strains {
    private static volatile Snapshot current = Snapshot.EMPTY;

    /**
     * Атрибуты, которые мод трогает. Набор только растёт: если перезагрузка убрала черту,
     * её модификатор всё равно нужно снять с игрока.
     */
    private static final Set<Holder<Attribute>> TRACKED_ATTRIBUTES = ConcurrentHashMap.newKeySet();

    static {
        TRACKED_ATTRIBUTES.addAll(List.of(Attributes.MAX_HEALTH, Attributes.MOVEMENT_SPEED,
                Attributes.ATTACK_DAMAGE, Attributes.ATTACK_SPEED, Attributes.ARMOR,
                Attributes.KNOCKBACK_RESISTANCE, Attributes.SCALE,
                Attributes.BLOCK_INTERACTION_RANGE, Attributes.ENTITY_INTERACTION_RANGE));
    }

    private Strains() {
    }

    /** Заменяет весь набор. Если два штамма претендуют на одно существо, побеждает первый по id. */
    public static void replace(Collection<Strain> strains) {
        Map<ResourceLocation, Strain> byId = new LinkedHashMap<>();
        Map<EntityType<?>, Strain> byEntity = new LinkedHashMap<>();
        Set<Item> catalysts = new HashSet<>();
        strains.stream()
                .sorted((a, b) -> a.id().toString().compareTo(b.id().toString()))
                .forEach(strain -> {
                    byId.put(strain.id(), strain);
                    byEntity.putIfAbsent(strain.entityType(), strain);
                    catalysts.add(strain.synthesis().catalyst());
                    for (Trait trait : strain.traits()) {
                        if (trait instanceof Traits.Attr attr) {
                            TRACKED_ATTRIBUTES.add(attr.attribute());
                        }
                    }
                });
        current = new Snapshot(Collections.unmodifiableMap(byId), Collections.unmodifiableMap(byEntity),
                Collections.unmodifiableSet(catalysts));
    }

    @Nullable
    public static Strain get(ResourceLocation id) {
        return current.byId().get(id);
    }

    @Nullable
    public static Strain byEntity(EntityType<?> entityType) {
        return current.byEntity().get(entityType);
    }

    public static Collection<Strain> all() {
        return current.byId().values();
    }

    public static Collection<ResourceLocation> ids() {
        return current.byId().keySet();
    }

    public static int count() {
        return current.byId().size();
    }

    /** Годится ли предмет в катализатор хоть одного штамма — для слота синтезатора. */
    public static boolean isCatalyst(ItemStack stack) {
        return current.catalysts().contains(stack.getItem());
    }

    public static Set<Holder<Attribute>> trackedAttributes() {
        return TRACKED_ATTRIBUTES;
    }

    /** Неизменяемый снимок набора: читается без блокировок с любого потока. */
    private record Snapshot(Map<ResourceLocation, Strain> byId, Map<EntityType<?>, Strain> byEntity,
                            Set<Item> catalysts) {
        static final Snapshot EMPTY = new Snapshot(Map.of(), Map.of(), Set.of());
    }
}
