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
 * Species research: what percentage of each species' genome has already been read.
 * <p>
 * Stored in the world save. By default knowledge is shared across the world — the team researches together;
 * with {@code knowledgeScope = "player"} each player has their own lab in their head.
 * The species record appears as soon as its first sample is taken: that's how a species gets into the gene tree.
 */
public final class MutagenKnowledge extends SavedData {
    private static final String NAME = Mutagen.MOD_ID + "_knowledge";
    private static final int FORMAT_VERSION = 1;
    /**
     * The datafix type is required: vanilla storage doesn't null-check it. This type has no
     * fixes of its own for our fields, so the data passes through it untouched.
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

    /** Species research for the player (or for the world, if knowledge is shared). */
    public float research(@Nullable UUID player, ResourceLocation strain) {
        Map<ResourceLocation, Float> scope = scope(player, false);
        return scope == null ? 0.0F : scope.getOrDefault(strain, 0.0F);
    }

    public boolean known(@Nullable UUID player, ResourceLocation strain) {
        Map<ResourceLocation, Float> scope = scope(player, false);
        return scope != null && scope.containsKey(strain);
    }

    /** Adds the species to the gene tree. true if the species is encountered for the first time. */
    public boolean discover(@Nullable UUID player, ResourceLocation strain) {
        Map<ResourceLocation, Float> scope = scope(player, true);
        if (scope == null || scope.containsKey(strain)) {
            return false;
        }
        scope.put(strain, 0.0F);
        setDirty();
        return true;
    }

    /** Adds research and returns the new value. */
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

    /** Everything the player knows: for the gene tree and the initial sync. */
    public Map<ResourceLocation, Float> view(@Nullable UUID player) {
        Map<ResourceLocation, Float> scope = scope(player, false);
        return scope == null ? Map.of() : Map.copyOf(scope);
    }

    /** Broadcasts a change of one species to those it concerns: everyone with shared knowledge, otherwise one player. */
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

    // --- Saving ---

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
