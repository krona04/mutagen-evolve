package net.krona.mutagen.network;

import com.mojang.logging.LogUtils;
import dev.architectury.networking.NetworkManager;
import dev.architectury.utils.Env;
import dev.architectury.utils.EnvExecutor;
import net.krona.mutagen.Mutagen;
import net.krona.mutagen.ability.AbilityUse;
import net.krona.mutagen.data.MutagenData;
import net.krona.mutagen.data.MutagenPlayer;
import net.krona.mutagen.knowledge.MutagenKnowledge;
import net.krona.mutagen.menu.BioWorkbenchMenu;
import net.krona.mutagen.strain.Stage;
import net.krona.mutagen.strain.Strain;
import net.krona.mutagen.strain.Strains;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The server computes the mutation state and syncs a copy for the client UI and rendering.
 * <p>
 * Since 0.2.1, send compact snapshots only when the change is noticeable. The owner receives changes of at
 * least 0.1 percentage points in progress or pending absorption. Tracking players receive changes of at
 * least 1 percentage point in progress or a stage transition. Earlier versions sent the entire NBT state
 * every second during absorption.
 */
public final class MutagenNetwork {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final float OWNER_STEP = 0.1F;
    private static final float WATCHER_STEP = 1.0F;

    private static final Map<UUID, List<MutagenData.Gene>> SENT_TO_OWNER = new HashMap<>();
    private static final Map<UUID, List<MutagenData.Gene>> SENT_TO_WATCHERS = new HashMap<>();

    private MutagenNetwork() {
    }

    public static void init() {
        // The client registers handlers with the payload types. A dedicated server only registers the types
        // it sends.
        EnvExecutor.runInEnv(Env.CLIENT, () -> MutagenNetwork::registerClientReceivers);
        EnvExecutor.runInEnv(Env.SERVER, () -> MutagenNetwork::registerServerTypes);

        NetworkManager.registerReceiver(NetworkManager.Side.C2S, FillRecipePayload.TYPE, FillRecipePayload.STREAM_CODEC,
                (payload, context) -> context.queue(() -> {
                    if (context.getPlayer() instanceof ServerPlayer player
                            && player.containerMenu instanceof BioWorkbenchMenu menu
                            && menu.stillValid(player)) {
                        menu.fill(player, payload.recipe());
                    }
                }));

        NetworkManager.registerReceiver(NetworkManager.Side.C2S, UseAbilityPayload.TYPE,
                UseAbilityPayload.STREAM_CODEC,
                (payload, context) -> context.queue(() -> {
                    if (context.getPlayer() instanceof ServerPlayer player) {
                        AbilityUse.use(player, payload.index());
                    }
                }));
    }

    private static void registerServerTypes() {
        NetworkManager.registerS2CPayloadType(SyncPayload.TYPE, SyncPayload.STREAM_CODEC);
        NetworkManager.registerS2CPayloadType(StagePayload.TYPE, StagePayload.STREAM_CODEC);
        NetworkManager.registerS2CPayloadType(StrainsPayload.TYPE, StrainsPayload.STREAM_CODEC);
        NetworkManager.registerS2CPayloadType(KnowledgePayload.TYPE, KnowledgePayload.STREAM_CODEC);
        NetworkManager.registerS2CPayloadType(AbilityUsedPayload.TYPE, AbilityUsedPayload.STREAM_CODEC);
    }

    private static void registerClientReceivers() {
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, SyncPayload.TYPE, SyncPayload.STREAM_CODEC,
                net.krona.mutagen.client.MutagenClientHooks::handleSync);
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, StagePayload.TYPE, StagePayload.STREAM_CODEC,
                net.krona.mutagen.client.MutagenClientHooks::handleStage);
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, StrainsPayload.TYPE, StrainsPayload.STREAM_CODEC,
                net.krona.mutagen.client.MutagenClientHooks::handleStrains);
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, KnowledgePayload.TYPE, KnowledgePayload.STREAM_CODEC,
                net.krona.mutagen.client.MutagenClientHooks::handleKnowledge);
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, AbilityUsedPayload.TYPE,
                AbilityUsedPayload.STREAM_CODEC,
                net.krona.mutagen.client.MutagenClientHooks::handleAbilityUsed);
    }

    /** Send the ability's ready time on join or activation. */
    public static void sendAbilityUsed(ServerPlayer player, AbilityUsedPayload cooldown) {
        NetworkManager.sendToPlayer(player, cooldown);
    }

    // --- Player state ---

    /** Force a sync on join, death, dimension changes, and commands. */
    public static void syncPlayer(ServerPlayer player) {
        syncPlayer(player, true);
    }

    /**
     * Sync to the player and tracking players when the change is noticeable. {@code force} sends regardless
     * of the last snapshot.
     */
    public static void syncPlayer(ServerPlayer player, boolean force) {
        MutagenData data = MutagenPlayer.of(player);
        List<MutagenData.Gene> now = data.snapshot();
        UUID id = player.getUUID();

        boolean owner = force || changed(SENT_TO_OWNER.get(id), now, OWNER_STEP, true);
        boolean watchers = force || changed(SENT_TO_WATCHERS.get(id), now, WATCHER_STEP, false);
        if (!owner && !watchers) {
            return;
        }

        Packet<?> packet = NetworkManager.toPacket(NetworkManager.Side.S2C,
                new SyncPayload(player.getId(), now), player.level().registryAccess());
        if (owner) {
            player.connection.send(packet);
            SENT_TO_OWNER.put(id, now);
        }
        if (watchers) {
            player.serverLevel().getChunkSource().broadcast(player, packet);
            SENT_TO_WATCHERS.put(id, now);
        }
    }

    /** Give a newly tracking player the current appearance immediately. */
    public static void sendTo(ServerPlayer watcher, ServerPlayer target) {
        NetworkManager.sendToPlayer(watcher, new SyncPayload(target.getId(), MutagenPlayer.of(target).snapshot()));
    }

    public static void forget(ServerPlayer player) {
        SENT_TO_OWNER.remove(player.getUUID());
        SENT_TO_WATCHERS.remove(player.getUUID());
    }

    private static boolean changed(List<MutagenData.Gene> before, List<MutagenData.Gene> now, float step,
                                   boolean pending) {
        if (before == null || before.size() != now.size()) {
            return true;
        }
        for (int i = 0; i < now.size(); i++) {
            MutagenData.Gene a = before.get(i);
            MutagenData.Gene b = now.get(i);
            if (!a.strainId().equals(b.strainId()) || a.locked() != b.locked() || a.stage() != b.stage()) {
                return true;
            }
            if (Math.abs(a.progress() - b.progress()) >= step) {
                return true;
            }
            if (pending && (Math.abs(a.pending() - b.pending()) >= step
                    || (a.pending() > 0.0F) != (b.pending() > 0.0F))) {
                return true;
            }
        }
        return false;
    }

    public static void sendStageEvent(ServerPlayer player, Stage stage, boolean up, int color) {
        NetworkManager.sendToPlayer(player, new StagePayload(stage.ordinal(), up, color));
    }

    // --- Strains and research ---

    /** Send all registered strains on join and after {@code /reload}. */
    public static void sendStrains(ServerPlayer player) {
        List<CompoundTag> encoded = new ArrayList<>();
        for (Strain strain : Strains.all()) {
            Strain.CODEC.encodeStart(NbtOps.INSTANCE, strain)
                    .resultOrPartial(error -> LOGGER.error("[Mutagen] Cannot send strain {}: {}", strain.id(), error))
                    .ifPresent(tag -> {
                        if (tag instanceof CompoundTag compound) {
                            encoded.add(compound);
                        }
                    });
        }
        NetworkManager.sendToPlayer(player, new StrainsPayload(encoded));
    }

    public static void sendKnowledge(ServerPlayer player, boolean replace, Map<ResourceLocation, Float> entries) {
        NetworkManager.sendToPlayer(player, new KnowledgePayload(replace, new LinkedHashMap<>(entries)));
    }

    public static void sendFullKnowledge(ServerPlayer player) {
        sendKnowledge(player, true, MutagenKnowledge.get(player.server).view(player.getUUID()));
    }

    // --- Payloads ---

    /** Mutation state for one player. */
    public record SyncPayload(int entityId, List<MutagenData.Gene> genes) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<SyncPayload> TYPE =
                new CustomPacketPayload.Type<>(Mutagen.id("sync"));

        public static final StreamCodec<RegistryFriendlyByteBuf, SyncPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, SyncPayload::entityId,
                MutagenData.Gene.STREAM_CODEC.apply(ByteBufCodecs.list()), SyncPayload::genes,
                SyncPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Stage transition that triggers a screen flash and sound. */
    public record StagePayload(int stage, boolean up, int color) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<StagePayload> TYPE =
                new CustomPacketPayload.Type<>(Mutagen.id("stage"));

        public static final StreamCodec<RegistryFriendlyByteBuf, StagePayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, StagePayload::stage,
                ByteBufCodecs.BOOL, StagePayload::up,
                ByteBufCodecs.INT, StagePayload::color,
                StagePayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server datapack strains used by the client for names, colors, appearance, and the gene tree. */
    public record StrainsPayload(List<CompoundTag> strains) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<StrainsPayload> TYPE =
                new CustomPacketPayload.Type<>(Mutagen.id("strains"));

        public static final StreamCodec<RegistryFriendlyByteBuf, StrainsPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.COMPOUND_TAG.apply(ByteBufCodecs.list()), StrainsPayload::strains,
                StrainsPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Species research. {@code replace} sends the full list. Otherwise, send only changed entries. */
    public record KnowledgePayload(boolean replace, Map<ResourceLocation, Float> entries)
            implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<KnowledgePayload> TYPE =
                new CustomPacketPayload.Type<>(Mutagen.id("knowledge"));

        public static final StreamCodec<RegistryFriendlyByteBuf, KnowledgePayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, KnowledgePayload::replace,
                ByteBufCodecs.<RegistryFriendlyByteBuf, ResourceLocation, Float, Map<ResourceLocation, Float>>map(
                        LinkedHashMap::new, ResourceLocation.STREAM_CODEC, ByteBufCodecs.FLOAT),
                KnowledgePayload::entries,
                KnowledgePayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** The client requests ingredient placement in the bio workbench. */
    /** The client requests an ability by index. */
    public record UseAbilityPayload(int index) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<UseAbilityPayload> TYPE =
                new CustomPacketPayload.Type<>(Mutagen.id("use_ability"));

        public static final StreamCodec<RegistryFriendlyByteBuf, UseAbilityPayload> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.VAR_INT, UseAbilityPayload::index, UseAbilityPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Cooldown for one ability using the world's game time. */
    public record AbilityUsedPayload(ResourceLocation strain, int index, long ready, int duration)
            implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<AbilityUsedPayload> TYPE =
                new CustomPacketPayload.Type<>(Mutagen.id("ability_used"));

        public static final StreamCodec<RegistryFriendlyByteBuf, AbilityUsedPayload> STREAM_CODEC =
                StreamCodec.composite(
                        ResourceLocation.STREAM_CODEC, AbilityUsedPayload::strain,
                        ByteBufCodecs.VAR_INT, AbilityUsedPayload::index,
                        ByteBufCodecs.VAR_LONG, AbilityUsedPayload::ready,
                        ByteBufCodecs.VAR_INT, AbilityUsedPayload::duration,
                        AbilityUsedPayload::new);

        public long remaining(long gameTime) {
            return Math.max(0L, Math.min(duration, ready - gameTime));
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record FillRecipePayload(ResourceLocation recipe) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<FillRecipePayload> TYPE =
                new CustomPacketPayload.Type<>(Mutagen.id("fill_recipe"));

        public static final StreamCodec<RegistryFriendlyByteBuf, FillRecipePayload> STREAM_CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, FillRecipePayload::recipe,
                FillRecipePayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
