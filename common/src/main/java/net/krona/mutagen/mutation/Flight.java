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
 * Evaluate flight each tick from the active {@link Traits.Flight}, hunger, and wetness. Eligible players
 * get creative-style double-jump flight at a strain-specific speed and spend hunger while airborne.
 * Otherwise remove the strain's flight.
 * <p>
 * Mark strain-granted flight with a persistent player tag so reconnecting preserves ownership. Leave flight
 * granted by creative mode or other mods alone.
 */
public final class Flight {
    /** Persistent entity tag for flight granted by a strain. */
    public static final String TAG = "mutagen_flight";
    /** Default player flight speed. */
    private static final float DEFAULT_SPEED = 0.05F;

    private Flight() {
    }

    public static void tick(ServerPlayer player) {
        Abilities abilities = player.getAbilities();
        if (player.isCreative() || player.isSpectator()) {
            // The game mode controls flight now, so clear our ownership tag.
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

    /** Reason flight is unavailable, or null when the wings can support the player. */
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

    /** Active flight trait for the primary strain. */
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
