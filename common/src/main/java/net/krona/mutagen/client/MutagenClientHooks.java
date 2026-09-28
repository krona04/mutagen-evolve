package net.krona.mutagen.client;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import dev.architectury.networking.NetworkManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.krona.mutagen.client.body.BodyMotion;
import net.krona.mutagen.data.MutagenData;
import net.krona.mutagen.data.MutagenPlayer;
import net.krona.mutagen.knowledge.ClientKnowledge;
import net.krona.mutagen.mutation.Mutation;
import net.krona.mutagen.network.MutagenNetwork;
import net.krona.mutagen.strain.Stage;
import net.krona.mutagen.strain.Strain;
import net.krona.mutagen.strain.Strains;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Приём серверных пакетов на клиенте.
 */
@Environment(EnvType.CLIENT)
public final class MutagenClientHooks {
    private static final Logger LOGGER = LogUtils.getLogger();

    private MutagenClientHooks() {
    }

    public static void handleSync(MutagenNetwork.SyncPayload payload, NetworkManager.PacketContext context) {
        context.queue(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level == null) {
                return;
            }
            Entity entity = minecraft.level.getEntity(payload.entityId());
            if (entity instanceof Player player) {
                MutagenData data = MutagenPlayer.of(player);
                MutagenData.Gene before = data.primary();
                Stage stageBefore = before == null ? Stage.NONE : before.stage();
                data.applySnapshot(payload.genes());
                MutagenData.Gene after = data.primary();
                // Стадия выросла — тело выкручивает. Видят все, кто смотрит, а не только сам носитель.
                if (after != null && after.stage().ordinal() > stageBefore.ordinal() && stageBefore != Stage.NONE) {
                    BodyMotion.startConvulsion(player);
                }
                // Хитбокс и глаза считаются из прогресса: клиент пересчитывает их сам, как и сервер.
                Mutation.refreshDimensions(player);
            }
        });
    }

    public static void handleStage(MutagenNetwork.StagePayload payload, NetworkManager.PacketContext context) {
        context.queue(() -> {
            Stage[] stages = Stage.values();
            Stage stage = payload.stage() >= 0 && payload.stage() < stages.length
                    ? stages[payload.stage()]
                    : Stage.NONE;
            MutagenClientState.onStageChanged(stage, payload.up(), payload.color());
        });
    }

    public static void handleStrains(MutagenNetwork.StrainsPayload payload, NetworkManager.PacketContext context) {
        context.queue(() -> {
            // В одиночной игре сервер живёт в том же процессе и уже держит этот набор:
            // подменять его копией незачем.
            if (Minecraft.getInstance().hasSingleplayerServer()) {
                return;
            }
            List<Strain> strains = new ArrayList<>();
            for (CompoundTag tag : payload.strains()) {
                DataResult<Strain> result = Strain.CODEC.parse(NbtOps.INSTANCE, tag);
                result.resultOrPartial(error -> LOGGER.warn("[Mutagen] Bad strain from server: {}", error))
                        .ifPresent(strains::add);
            }
            Strains.replace(strains);
        });
    }

    public static void handleKnowledge(MutagenNetwork.KnowledgePayload payload, NetworkManager.PacketContext context) {
        context.queue(() -> ClientKnowledge.apply(payload.replace(), payload.entries()));
    }
}
