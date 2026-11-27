package net.krona.mutagen.ability;

import net.krona.mutagen.data.MutagenData;
import net.krona.mutagen.data.MutagenPlayer;
import net.krona.mutagen.knowledge.MutagenKnowledge;
import net.krona.mutagen.knowledge.Research;
import net.krona.mutagen.network.MutagenNetwork;
import net.krona.mutagen.network.MutagenNetwork.AbilityUsedPayload;
import net.krona.mutagen.strain.Stage;
import net.krona.mutagen.strain.Strain;
import net.krona.mutagen.strain.Strains;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Validate, charge for, and apply cooldowns to ability uses.
 * <p>
 * Validate and activate first, then charge the player. A miss costs neither hunger nor cooldown.
 */
public final class AbilityUse {
    /**
     * Store timers by player, strain, and ability index. The same index can refer to different abilities on
     * different strains.
     */
    private static final Map<MinecraftServer, Map<UUID, Map<String, AbilityUsedPayload>>> COOLDOWNS =
            new WeakHashMap<>();

    private AbilityUse() {
    }

    /** Abilities for the player's strain in the order used by the client. */
    public static List<Ability> available(ServerPlayer player) {
        MutagenData.Gene gene = MutagenPlayer.of(player).primary();
        if (gene == null) {
            return List.of();
        }
        Strain strain = Strains.get(gene.strainId());
        return strain == null ? List.of() : strain.abilities();
    }

    public static void use(ServerPlayer player, int index) {
        MutagenData.Gene gene = MutagenPlayer.of(player).primary();
        if (gene == null) {
            return;
        }
        Strain strain = Strains.get(gene.strainId());
        if (strain == null) {
            return;
        }
        List<Ability> abilities = strain.abilities();
        if (index < 0 || index >= abilities.size()) {
            return;
        }

        Ability ability = abilities.get(index);
        Stage stage = gene.stage();

        String refusal = refuse(player, strain, ability, stage, index);
        if (refusal != null) {
            player.displayClientMessage(Component.translatable(refusal).withStyle(ChatFormatting.RED), true);
            return;
        }

        if (!ability.activate(player, stage)) {
            return;
        }

        pay(player, ability.cost());
        long ready = player.level().getGameTime() + ability.cooldown();
        AbilityUsedPayload cooldown = new AbilityUsedPayload(strain.id(), index, ready, ability.cooldown());
        COOLDOWNS.computeIfAbsent(player.server, server -> new HashMap<>())
                .computeIfAbsent(player.getUUID(), key -> new HashMap<>()).put(key(strain, index), cooldown);
        MutagenNetwork.sendAbilityUsed(player, cooldown);
    }

    /** Restore active cooldowns on reconnect, including those for the player's other strains. */
    public static void syncCooldowns(ServerPlayer player) {
        long now = player.level().getGameTime();
        for (AbilityUsedPayload cooldown : cooldowns(player).values()) {
            if (cooldown.remaining(now) > 0) {
                MutagenNetwork.sendAbilityUsed(player, cooldown);
            }
        }
    }

    /** Translation key for the refusal reason, or null if the ability is allowed. */
    @Nullable
    private static String refuse(ServerPlayer player, Strain strain, Ability ability, Stage stage, int index) {
        if (!player.isAlive() || player.isSpectator()) return "mutagen.ability.unavailable";
        if (!ability.requirement().test(player)) return ability.requirement().key();
        if (!stage.atLeast(ability.minStage())) {
            return "mutagen.ability.too_early";
        }
        if (ability.apex()) {
            float research = MutagenKnowledge.get(player.server).research(player.getUUID(), strain.id());
            if (research < Research.MASTERED) {
                return "mutagen.ability.needs_mastery";
            }
        }
        if (remaining(player, strain, index) > 0) {
            return "mutagen.ability.cooling_down";
        }
        if (!canPay(player, ability.cost())) {
            return "mutagen.ability.too_weak";
        }
        return null;
    }

    /** Remaining cooldown in ticks for an ability of the current strain. */
    public static long remaining(ServerPlayer player, int index) {
        MutagenData.Gene gene = MutagenPlayer.of(player).primary();
        Strain strain = gene == null ? null : Strains.get(gene.strainId());
        return strain == null ? 0L : remaining(player, strain, index);
    }

    private static long remaining(ServerPlayer player, Strain strain, int index) {
        AbilityUsedPayload cooldown = cooldowns(player).get(key(strain, index));
        return cooldown == null ? 0L : cooldown.remaining(player.level().getGameTime());
    }

    private static Map<String, AbilityUsedPayload> cooldowns(ServerPlayer player) {
        Map<UUID, Map<String, AbilityUsedPayload>> byPlayer = COOLDOWNS.get(player.server);
        return byPlayer == null ? Map.of() : byPlayer.getOrDefault(player.getUUID(), Map.of());
    }

    private static String key(Strain strain, int index) {
        return strain.id() + "#" + index;
    }

    private static boolean canPay(ServerPlayer player, Ability.Cost cost) {
        if (cost.isFree() || player.isCreative()) {
            return true;
        }
        FoodData food = player.getFoodData();
        boolean enoughFood = cost.hunger() <= 0.0F
                || AbilityBudget.food(food.getFoodLevel(), food.getSaturationLevel(), cost.hunger()) != null;
        // Keep the player alive when charging a health cost.
        boolean enoughHealth = cost.health() <= 0.0F || AbilityBudget.healthAvailable(player.getHealth(), cost.health());
        return enoughFood && enoughHealth;
    }

    private static void pay(ServerPlayer player, Ability.Cost cost) {
        if (cost.isFree() || player.isCreative()) {
            return;
        }
        if (cost.hunger() > 0.0F) {
            FoodData food = player.getFoodData();
            AbilityBudget.FoodPayment payment = AbilityBudget.food(food.getFoodLevel(), food.getSaturationLevel(), cost.hunger());
            if (payment != null) {
                food.setSaturation(payment.saturation());
                food.setFoodLevel(payment.food());
            }
        }
        if (cost.health() > 0.0F) {
            player.setHealth(AbilityBudget.healthAfter(player.getHealth(), cost.health()));
        }
    }
}
