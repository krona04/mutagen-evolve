package net.krona.mutagen.network;

import com.mojang.logging.LogUtils;
import dev.architectury.networking.NetworkManager;
import dev.architectury.utils.Env;
import dev.architectury.utils.EnvExecutor;
import net.krona.mutagen.Mutagen;
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
 * Сеть мода. Состояние считает сервер, клиент получает копию для интерфейса и отрисовки.
 * <p>
 * С 0.2.1 состояние игрока уходит дельтами. Сервер помнит, что уже отправил, и шлёт снова только
 * заметное: самому игроку — от 0.1% прогресса или запаса (этого хватает на плавную полосу),
 * остальным — от 1% прогресса или при смене стадии, потому что им нужен только облик.
 * Раньше состояние целиком уходило в NBT каждую секунду, пока шло усвоение.
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
        // Клиент регистрирует обработчики (а вместе с ними и сами типы пакетов),
        // выделенному серверу достаточно знать, что он имеет право такие пакеты отправлять.
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
    }

    private static void registerServerTypes() {
        NetworkManager.registerS2CPayloadType(SyncPayload.TYPE, SyncPayload.STREAM_CODEC);
        NetworkManager.registerS2CPayloadType(StagePayload.TYPE, StagePayload.STREAM_CODEC);
        NetworkManager.registerS2CPayloadType(StrainsPayload.TYPE, StrainsPayload.STREAM_CODEC);
        NetworkManager.registerS2CPayloadType(KnowledgePayload.TYPE, KnowledgePayload.STREAM_CODEC);
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
    }

    // --- Состояние игрока ---

    /** Отправить состояние сразу, не сравнивая с отправленным: вход, смерть, смена измерения, команды. */
    public static void syncPlayer(ServerPlayer player) {
        syncPlayer(player, true);
    }

    /**
     * Отправляет состояние игрока ему самому и тем, кто его видит, если с прошлой отправки
     * оно заметно изменилось. {@code force} отправляет в любом случае.
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

    /** Кто-то начал видеть игрока: ему нужен облик сразу, а не при следующем изменении. */
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

    // --- Штаммы и знание ---

    /** Все загруженные штаммы: при входе и после {@code /reload}. */
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

    // --- Пакеты ---

    /** Состояние мутации одного игрока. */
    public record SyncPayload(int entityId, List<MutagenData.Gene> genes) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<SyncPayload> TYPE =
                new CustomPacketPayload.Type<>(Mutagen.id("sync"));

        public static final StreamCodec<RegistryFriendlyByteBuf, SyncPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, SyncPayload::entityId,
                MutagenData.Gene.STREAM_CODEC.apply(ByteBufCodecs.list(16)), SyncPayload::genes,
                SyncPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Переход между стадиями: вспышка на экране и звук. */
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

    /** Штаммы из датапака сервера: клиенту они нужны для цвета, имени, облика и гено-древа. */
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

    /** Изученность видов. {@code replace} — полный список, иначе только изменившиеся виды. */
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

    /** Клиент просит разложить ингредиенты рецепта по верстаку биолога. */
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
