package net.krona.mutagen.mutation;

import net.krona.mutagen.data.MutagenData;
import net.krona.mutagen.data.MutagenPlayer;
import net.krona.mutagen.strain.Stage;
import net.krona.mutagen.strain.Strain;
import net.krona.mutagen.strain.Strains;
import net.krona.mutagen.strain.Trait;
import net.krona.mutagen.strain.Traits;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Abilities;
import org.jetbrains.annotations.Nullable;

/**
 * Полёт летающих штаммов. Каждый тик решается, держат ли носителя крылья: есть ли у штамма черта
 * {@link Traits.Flight} на нынешней стадии, хватает ли сытости, не мокрые ли крылья. Если держат —
 * игроку выдаётся тот же полёт, что в творческом режиме (двойной прыжок), со своей скоростью, и в
 * воздухе он платит голодом. Если нет — полёт забирается, и носитель падает.
 * <p>
 * Выданный полёт помечен тегом игрока: так он переживает перезаход и сервер, а чужой полёт — творческий
 * режим, другие моды — штамм не трогает и не отнимает.
 */
public final class Flight {
    /** Метка «этот полёт выдал штамм». Обычный тег сущности, сохраняется вместе с игроком. */
    public static final String TAG = "mutagen_flight";
    /** Скорость полёта игрока по умолчанию. */
    private static final float DEFAULT_SPEED = 0.05F;

    private Flight() {
    }

    public static void tick(ServerPlayer player) {
        Abilities abilities = player.getAbilities();
        if (player.isCreative() || player.isSpectator()) {
            // Полётом распоряжается режим игры. Наша метка больше ничего не значит.
            player.removeTag(TAG);
            return;
        }
        boolean granted = player.getTags().contains(TAG);
        Traits.Flight wings = wings(player);
        String grounded = wings == null ? null : groundedBy(player, wings);

        if (wings != null && grounded == null) {
            if (!granted || !abilities.mayfly || abilities.getFlyingSpeed() != wings.speed()) {
                abilities.mayfly = true;
                abilities.setFlyingSpeed(wings.speed());
                player.addTag(TAG);
                player.onUpdateAbilities();
            }
            if (abilities.flying) {
                player.causeFoodExhaustion(wings.exhaustion() / 20.0F);
                player.resetFallDistance();
            }
            return;
        }

        if (granted) {
            boolean wasFlying = abilities.flying;
            abilities.mayfly = false;
            abilities.flying = false;
            abilities.setFlyingSpeed(DEFAULT_SPEED);
            player.removeTag(TAG);
            player.onUpdateAbilities();
            if (wasFlying && grounded != null) {
                player.displayClientMessage(Component.translatable(grounded).withStyle(ChatFormatting.GOLD), true);
            }
        }
    }

    /** Почему крылья сейчас не держат, или null, если держат. */
    @Nullable
    private static String groundedBy(ServerPlayer player, Traits.Flight wings) {
        if (player.getFoodData().getFoodLevel() < wings.minFood()) {
            return "mutagen.message.flight_tired";
        }
        if (!wings.rain() && player.isInWaterRainOrBubble()) {
            return "mutagen.message.flight_wet";
        }
        return null;
    }

    /** Черта полёта основного штамма на нынешней стадии. */
    @Nullable
    private static Traits.Flight wings(ServerPlayer player) {
        MutagenData data = MutagenPlayer.of(player);
        if (data == null) {
            return null;
        }
        for (MutagenData.Gene gene : data.genes()) {
            Strain strain = Strains.get(gene.strainId());
            if (strain == null) {
                continue;
            }
            Stage stage = gene.stage();
            for (Trait trait : strain.activeTraits(stage)) {
                if (trait instanceof Traits.Flight flight) {
                    return flight;
                }
            }
        }
        return null;
    }
}
