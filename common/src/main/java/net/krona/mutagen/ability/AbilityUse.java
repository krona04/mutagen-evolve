package net.krona.mutagen.ability;

import net.krona.mutagen.data.MutagenData;
import net.krona.mutagen.data.MutagenPlayer;
import net.krona.mutagen.knowledge.MutagenKnowledge;
import net.krona.mutagen.knowledge.Research;
import net.krona.mutagen.network.MutagenNetwork;
import net.krona.mutagen.strain.Stage;
import net.krona.mutagen.strain.Strain;
import net.krona.mutagen.strain.Strains;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.MinecraftServer;

/**
 * Применение способности: проверки, цена и перезарядка.
 * <p>
 * Порядок важен: сначала всё, что может отказать, и только потом списание. Неудачная попытка
 * не стоит игроку ни голода, ни перезарядки — иначе промах по цели наказывал бы дважды.
 */
public final class AbilityUse {
    /**
     * Игрок -> «штамм#номер» -> тик готовности. Номер без штамма не годится: у другого тела под тем
     * же номером другая способность, и перезарядка волчьей стаи не должна держать разлом эндермена.
     */
    private static final Map<MinecraftServer, Map<UUID, Map<String, Long>>> COOLDOWNS = new WeakHashMap<>();

    private AbilityUse() {
    }

    /** Способности штамма игрока, в том порядке, в каком их нумерует клиент. */
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
        COOLDOWNS.computeIfAbsent(player.server, server -> new HashMap<>())
                .computeIfAbsent(player.getUUID(), key -> new HashMap<>()).put(key(strain, index), ready);
        MutagenNetwork.sendAbilityUsed(player, index, ability.cooldown());
    }

    /** Ключ перевода причины отказа или null, если применять можно. */
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

    /** Сколько тиков перезарядки осталось у способности текущего штамма. */
    public static long remaining(ServerPlayer player, int index) {
        MutagenData.Gene gene = MutagenPlayer.of(player).primary();
        Strain strain = gene == null ? null : Strains.get(gene.strainId());
        return strain == null ? 0L : remaining(player, strain, index);
    }

    private static long remaining(ServerPlayer player, Strain strain, int index) {
        Map<UUID, Map<String, Long>> byPlayer = COOLDOWNS.get(player.server);
        Map<String, Long> byKey = byPlayer == null ? null : byPlayer.get(player.getUUID());
        if (byKey == null) {
            return 0L;
        }
        Long ready = byKey.get(key(strain, index));
        return ready == null ? 0L : Math.max(0L, ready - player.level().getGameTime());
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
        // Здоровьем платят до последнего сердца, но не жизнью.
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
