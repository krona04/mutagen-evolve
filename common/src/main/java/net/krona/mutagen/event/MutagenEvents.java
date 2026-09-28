package net.krona.mutagen.event;

import dev.architectury.event.CompoundEventResult;
import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.InteractionEvent;
import dev.architectury.event.events.common.EntityEvent;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.event.events.common.TickEvent;
import net.krona.mutagen.data.MutagenData;
import net.krona.mutagen.data.MutagenPlayer;
import net.krona.mutagen.mutation.Mutation;
import net.krona.mutagen.mutation.Restrictions;
import net.krona.mutagen.network.MutagenNetwork;
import net.krona.mutagen.strain.StrainLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;

/**
 * Подключение логики мутации к жизни сервера.
 */
public final class MutagenEvents {
    private static final int MAX_REPORTED_ERRORS = 5;

    private MutagenEvents() {
    }

    public static void init() {
        TickEvent.PLAYER_POST.register(player -> {
            if (player instanceof ServerPlayer serverPlayer) {
                Mutation.tick(serverPlayer);
                Restrictions.tick(serverPlayer);
            }
        });

        // Тело без рук: станки, инструменты и оружие. События приходят на обе стороны, поэтому отказ
        // не успевает даже качнуть руку на клиенте.
        InteractionEvent.RIGHT_CLICK_BLOCK.register((player, hand, pos, face) -> {
            if (Restrictions.blocksSleep(player, player.level().getBlockState(pos))) {
                Restrictions.tell(player, "mutagen.restriction.sleep");
                return EventResult.interruptFalse();
            }
            if (Restrictions.blocksStation(player, player.level().getBlockState(pos))) {
                Restrictions.tell(player, "mutagen.restriction.crafting");
                return EventResult.interruptFalse();
            }
            if (Restrictions.blocksTool(player, player.getItemInHand(hand))) {
                Restrictions.tell(player, "mutagen.restriction.tools");
                return EventResult.interruptFalse();
            }
            return EventResult.pass();
        });
        InteractionEvent.LEFT_CLICK_BLOCK.register((player, hand, pos, face) -> {
            if (Restrictions.blocksTool(player, player.getItemInHand(hand))) {
                Restrictions.tell(player, "mutagen.restriction.tools");
                return EventResult.interruptFalse();
            }
            return EventResult.pass();
        });
        InteractionEvent.RIGHT_CLICK_ITEM.register((player, hand) -> {
            ItemStack stack = player.getItemInHand(hand);
            if (Restrictions.blocksTool(player, stack)) {
                Restrictions.tell(player, "mutagen.restriction.tools");
                return CompoundEventResult.interruptFalse(stack);
            }
            String food = Restrictions.refusesFood(player, stack);
            if (food != null) {
                Restrictions.tell(player, food);
                return CompoundEventResult.interruptFalse(stack);
            }
            return CompoundEventResult.pass();
        });
        PlayerEvent.ATTACK_ENTITY.register((player, level, target, hand, hit) -> {
            if (Restrictions.blocksTool(player, player.getItemInHand(hand))) {
                Restrictions.tell(player, "mutagen.restriction.tools");
                return EventResult.interruptFalse();
            }
            return EventResult.pass();
        });

        // Перезагрузка датапака применяется в тике сервера, а не посреди загрузки ресурсов:
        // так рассылка идёт с того же потока, что и вся остальная игра.
        TickEvent.SERVER_POST.register(server -> {
            StrainLoader.Result reload = StrainLoader.consumePending();
            if (reload != null) {
                onStrainsReloaded(server, reload);
            }
        });

        PlayerEvent.PLAYER_JOIN.register(player -> {
            // Порядок важен: клиенту нужны штаммы раньше, чем состояние, которое на них ссылается.
            MutagenNetwork.sendStrains(player);
            MutagenNetwork.sendFullKnowledge(player);
            Mutation.refreshAttributes(player, MutagenPlayer.of(player));
            MutagenNetwork.syncPlayer(player);
        });

        PlayerEvent.PLAYER_QUIT.register(MutagenNetwork::forget);

        // Смерть не снимает штамм: тело умирает, геном остаётся.
        PlayerEvent.PLAYER_CLONE.register((oldPlayer, newPlayer, wonGame) -> {
            MutagenData from = MutagenPlayer.of(oldPlayer);
            MutagenData to = MutagenPlayer.of(newPlayer);
            to.copyFrom(from);
        });

        PlayerEvent.PLAYER_RESPAWN.register((player, conqueredEnd, removalReason) -> {
            Mutation.refreshAttributes(player, MutagenPlayer.of(player));
            MutagenNetwork.syncPlayer(player);
        });

        // При смене измерения клиент создаёт игрока заново, и его копия состояния теряется.
        PlayerEvent.CHANGE_DIMENSION.register((player, from, to) -> MutagenNetwork.syncPlayer(player));

        // Стрелы скелетного штамма бьют сильнее.
        EntityEvent.ADD.register((entity, level) -> {
            if (entity instanceof AbstractArrow arrow && arrow.getOwner() instanceof Player owner
                    && !level.isClientSide) {
                double factor = Mutation.arrowFactor(owner);
                if (factor != 1.0D) {
                    arrow.setBaseDamage(arrow.getBaseDamage() * factor);
                }
            }
            return EventResult.pass();
        });
    }

    /**
     * {@code /reload} на лету: новые штаммы уходят всем клиентам, черты пересобираются сразу,
     * а операторы видят, сколько штаммов загрузилось и какие файлы сломаны.
     * Гены штаммов, которых больше нет, остаются в сохранении и просто молчат — пока файл не вернут.
     */
    private static void onStrainsReloaded(MinecraftServer server, StrainLoader.Result reload) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            MutagenNetwork.sendStrains(player);
            Mutation.refreshAttributes(player, MutagenPlayer.of(player));
            MutagenNetwork.syncPlayer(player);
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.hasPermissions(2)) {
                continue;
            }
            player.sendSystemMessage(Component.translatable("mutagen.message.reloaded", reload.loaded())
                    .withStyle(reload.errors().isEmpty() ? ChatFormatting.GRAY : ChatFormatting.GOLD));
            int shown = 0;
            for (String error : reload.errors()) {
                if (shown++ >= MAX_REPORTED_ERRORS) {
                    player.sendSystemMessage(Component.translatable("mutagen.message.reload_more",
                            reload.errors().size() - MAX_REPORTED_ERRORS).withStyle(ChatFormatting.RED));
                    break;
                }
                player.sendSystemMessage(Component.literal(error).withStyle(ChatFormatting.RED));
            }
        }
    }
}
