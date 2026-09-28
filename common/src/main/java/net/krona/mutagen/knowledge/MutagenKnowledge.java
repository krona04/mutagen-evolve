package net.krona.mutagen.knowledge;

import net.krona.mutagen.Mutagen;
import net.krona.mutagen.MutagenConfig;
import net.krona.mutagen.network.MutagenNetwork;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Изученность видов: сколько процентов генома каждого вида уже прочитано.
 * <p>
 * Хранится в сохранении мира. По умолчанию знание общее на мир — команда исследует вместе;
 * с {@code knowledgeScope = "player"} у каждого игрока своя лаборатория в голове.
 * Запись о виде появляется, как только у него взят первый образец: так вид попадает в гено-древо.
 */
public final class MutagenKnowledge extends SavedData {
    private static final String NAME = Mutagen.MOD_ID + "_knowledge";
    private static final int FORMAT_VERSION = 1;
    /**
     * Тип датафикса обязателен: ванильное хранилище не проверяет его на null. Своих правок у этого
     * типа для наших полей нет, так что данные проходят через него нетронутыми.
     */
    private static final Factory<MutagenKnowledge> FACTORY =
            new Factory<>(MutagenKnowledge::new, MutagenKnowledge::load, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private final Map<ResourceLocation, Float> world = new LinkedHashMap<>();
    private final Map<UUID, Map<ResourceLocation, Float>> players = new HashMap<>();

    public static MutagenKnowledge get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    private static boolean perPlayer() {
        return MutagenConfig.get().knowledgeScope.equals(MutagenConfig.SCOPE_PLAYER);
    }

    @Nullable
    private Map<ResourceLocation, Float> scope(@Nullable UUID player, boolean create) {
        if (!perPlayer()) {
            return world;
        }
        if (player == null) {
            return null;
        }
        return create ? players.computeIfAbsent(player, key -> new LinkedHashMap<>()) : players.get(player);
    }

    /** Изученность вида для игрока (или для мира, если знание общее). */
    public float research(@Nullable UUID player, ResourceLocation strain) {
        Map<ResourceLocation, Float> scope = scope(player, false);
        return scope == null ? 0.0F : scope.getOrDefault(strain, 0.0F);
    }

    public boolean known(@Nullable UUID player, ResourceLocation strain) {
        Map<ResourceLocation, Float> scope = scope(player, false);
        return scope != null && scope.containsKey(strain);
    }

    /** Вносит вид в гено-древо. true, если вид встречен впервые. */
    public boolean discover(@Nullable UUID player, ResourceLocation strain) {
        Map<ResourceLocation, Float> scope = scope(player, true);
        if (scope == null || scope.containsKey(strain)) {
            return false;
        }
        scope.put(strain, 0.0F);
        setDirty();
        return true;
    }

    /** Добавляет изученность и возвращает новое значение. */
    public float addResearch(@Nullable UUID player, ResourceLocation strain, float amount) {
        return setResearch(player, strain, research(player, strain) + amount);
    }

    public float setResearch(@Nullable UUID player, ResourceLocation strain, float value) {
        Map<ResourceLocation, Float> scope = scope(player, true);
        if (scope == null) {
            return 0.0F;
        }
        float clamped = Math.max(0.0F, Math.min(100.0F, value));
        scope.put(strain, clamped);
        setDirty();
        return clamped;
    }

    /** Всё, что знает игрок: для гено-древа и первой синхронизации. */
    public Map<ResourceLocation, Float> view(@Nullable UUID player) {
        Map<ResourceLocation, Float> scope = scope(player, false);
        return scope == null ? Map.of() : Map.copyOf(scope);
    }

    /** Рассылает изменение одного вида тем, кого оно касается: всем при общем знании, иначе одному игроку. */
    public void broadcast(MinecraftServer server, @Nullable UUID player, ResourceLocation strain) {
        Map<ResourceLocation, Float> delta = Map.of(strain, research(player, strain));
        for (ServerPlayer target : audience(server, player)) {
            MutagenNetwork.sendKnowledge(target, false, delta);
        }
    }

    private List<ServerPlayer> audience(MinecraftServer server, @Nullable UUID player) {
        if (!perPlayer()) {
            return server.getPlayerList().getPlayers();
        }
        ServerPlayer target = player == null ? null : server.getPlayerList().getPlayer(player);
        return target == null ? List.of() : List.of(target);
    }

    // --- Сохранение ---

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("Version", FORMAT_VERSION);
        tag.put("World", saveScope(world));
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Map<ResourceLocation, Float>> entry : players.entrySet()) {
            CompoundTag player = new CompoundTag();
            player.putUUID("Player", entry.getKey());
            player.put("Species", saveScope(entry.getValue()));
            list.add(player);
        }
        tag.put("Players", list);
        return tag;
    }

    private static MutagenKnowledge load(CompoundTag tag, HolderLookup.Provider registries) {
        MutagenKnowledge knowledge = new MutagenKnowledge();
        loadScope(tag.getList("World", Tag.TAG_COMPOUND), knowledge.world);
        ListTag list = tag.getList("Players", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag player = list.getCompound(i);
            if (!player.hasUUID("Player")) {
                continue;
            }
            Map<ResourceLocation, Float> scope = new LinkedHashMap<>();
            loadScope(player.getList("Species", Tag.TAG_COMPOUND), scope);
            knowledge.players.put(player.getUUID("Player"), scope);
        }
        return knowledge;
    }

    private static ListTag saveScope(Map<ResourceLocation, Float> scope) {
        ListTag list = new ListTag();
        for (Map.Entry<ResourceLocation, Float> entry : scope.entrySet()) {
            CompoundTag species = new CompoundTag();
            species.putString("Id", entry.getKey().toString());
            species.putFloat("Research", entry.getValue());
            list.add(species);
        }
        return list;
    }

    private static void loadScope(ListTag list, Map<ResourceLocation, Float> into) {
        for (int i = 0; i < list.size(); i++) {
            CompoundTag species = list.getCompound(i);
            ResourceLocation id = ResourceLocation.tryParse(species.getString("Id"));
            if (id != null) {
                into.put(id, species.getFloat("Research"));
            }
        }
    }
}
