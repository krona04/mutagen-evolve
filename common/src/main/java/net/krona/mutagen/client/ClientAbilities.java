package net.krona.mutagen.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.krona.mutagen.ability.Ability;
import net.krona.mutagen.ability.AbilityBudget;
import net.krona.mutagen.data.MutagenData;
import net.krona.mutagen.data.MutagenPlayer;
import net.krona.mutagen.knowledge.ClientKnowledge;
import net.krona.mutagen.knowledge.Research;
import net.krona.mutagen.network.MutagenNetwork.AbilityUsedPayload;
import net.krona.mutagen.strain.Stage;
import net.krona.mutagen.strain.Strain;
import net.krona.mutagen.strain.Strains;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client-side ability availability and cooldowns.
 * <p>
 * The UI reads abilities from the strain synchronized by the server. The server sends cooldown end times on
 * join and when abilities activate.
 */
@Environment(EnvType.CLIENT)
public final class ClientAbilities {
    /** Key cooldowns by strain and ability index. */
    private record CooldownKey(ResourceLocation strain, int index) {}
    private static final Map<CooldownKey, AbilityUsedPayload> COOLDOWNS = new HashMap<>();
    private static Object connection;

    private ClientAbilities() {
    }

    /** Indexed abilities for the current strain, or an empty list when the player has no strain. */
    public static List<Slot> slots() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return List.of();
        }
        MutagenData.Gene gene = MutagenPlayer.of(player).primary();
        if (gene == null) {
            return List.of();
        }
        Strain strain = Strains.get(gene.strainId());
        if (strain == null) {
            return List.of();
        }

        Stage stage = gene.stage();
        boolean mastered = ClientKnowledge.research(strain.id()) >= Research.MASTERED;
        List<Slot> slots = new ArrayList<>();
        List<Ability> abilities = strain.abilities();
        for (int index = 0; index < abilities.size(); index++) {
            Ability ability = abilities.get(index);
            boolean affordable = player.isCreative()
                    || (AbilityBudget.food(player.getFoodData().getFoodLevel(), player.getFoodData().getSaturationLevel(),
                            ability.cost().hunger()) != null
                        && AbilityBudget.healthAvailable(player.getHealth(), ability.cost().health()));
            boolean ready = player.isAlive() && !player.isSpectator() && affordable
                    && stage.atLeast(ability.minStage()) && (!ability.apex() || mastered)
                    && ability.requirement().test(player);
            slots.add(new Slot(index, ability, ready));
        }
        return slots;
    }

    /** Ability index for this key, or -1. */
    public static int indexForKey(int keySlot) {
        List<Slot> slots = slots();
        int seen = 0;
        for (Slot slot : slots) {
            if (slot.ability().apex()) {
                continue;
            }
            if (seen == keySlot) {
                return slot.index();
            }
            seen++;
        }
        return -1;
    }

    /** Apex ability index, or -1. */
    public static int apexIndex() {
        for (Slot slot : slots()) {
            if (slot.ability().apex()) {
                return slot.index();
            }
        }
        return -1;
    }

    public static void startCooldown(AbilityUsedPayload cooldown) {
        checkConnection(Minecraft.getInstance());
        COOLDOWNS.put(new CooldownKey(cooldown.strain(), cooldown.index()), cooldown);
    }

    /** Remaining cooldown as a fraction. Zero means ready and one means just activated. */
    public static float cooldownFraction(int index) {
        Minecraft minecraft = Minecraft.getInstance();
        AbilityUsedPayload cooldown = cooldown(minecraft, index);
        if (minecraft.level == null || cooldown == null || cooldown.duration() <= 0) {
            return 0.0F;
        }
        return cooldown.remaining(minecraft.level.getGameTime()) / (float) cooldown.duration();
    }

    /** Remaining cooldown in seconds, rounded up. Zero means ready. */
    public static int cooldownSeconds(int index) {
        Minecraft minecraft = Minecraft.getInstance();
        AbilityUsedPayload cooldown = cooldown(minecraft, index);
        if (minecraft.level == null || cooldown == null) {
            return 0;
        }
        return (int) ((cooldown.remaining(minecraft.level.getGameTime()) + 19) / 20);
    }

    @Nullable
    private static AbilityUsedPayload cooldown(Minecraft minecraft, int index) {
        checkConnection(minecraft);
        LocalPlayer player = minecraft.player;
        MutagenData.Gene gene = player == null ? null : MutagenPlayer.of(player).primary();
        return gene == null ? null : COOLDOWNS.get(new CooldownKey(gene.strainId(), index));
    }

    private static void checkConnection(Minecraft minecraft) {
        Object currentConnection = minecraft.getConnection();
        if (connection != currentConnection) {
            COOLDOWNS.clear();
            connection = currentConnection;
        }
    }

    /** An indexed ability and whether the player can currently use it. */
    public record Slot(int index, Ability ability, boolean unlocked) {
        @Nullable
        public Ability abilityOrNull() {
            return ability;
        }
    }
}
