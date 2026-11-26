package net.krona.mutagen.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.krona.mutagen.ability.Ability;
import net.krona.mutagen.ability.AbilityBudget;
import net.krona.mutagen.data.MutagenData;
import net.krona.mutagen.data.MutagenPlayer;
import net.krona.mutagen.knowledge.ClientKnowledge;
import net.krona.mutagen.knowledge.Research;
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
 * Клиентская сторона способностей: что доступно и сколько осталось до готовности.
 * <p>
 * Список берётся из того же штамма, что уже пришёл с сервера, поэтому интерфейс знает о
 * способностях всё, не спрашивая ничего дополнительно. Перезарядку клиент заводит по
 * подтверждению сервера и отсчитывает сам — она нужна только чтобы рисовать полоску.
 */
@Environment(EnvType.CLIENT)
public final class ClientAbilities {
    /** Номер способности в штамме -> тик, когда она снова готова. */
    private record CooldownKey(ResourceLocation strain, int index) {}
    private static final Map<CooldownKey, Long> COOLDOWNS = new HashMap<>();
    private static final Map<CooldownKey, Integer> LENGTHS = new HashMap<>();
    private static Object connection;
    /** Штамм, к которому относятся перезарядки: другое тело начинает с чистого листа. */
    @Nullable
    private static ResourceLocation owner;

    private ClientAbilities() {
    }

    /** Способности текущего штамма вместе с их номерами. Пустой список, если штамма нет. */
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

    /** Номер способности, привязанной к этой клавише, или -1. */
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

    /** Номер апекса или -1. */
    public static int apexIndex() {
        for (Slot slot : slots()) {
            if (slot.ability().apex()) {
                return slot.index();
            }
        }
        return -1;
    }

    public static void startCooldown(int index, int ticks) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        checkOwner();
        COOLDOWNS.put(new CooldownKey(owner, index), minecraft.level.getGameTime() + ticks);
        LENGTHS.put(new CooldownKey(owner, index), ticks);
    }

    /** Доля оставшейся перезарядки: 0 — готово, 1 — только что применили. */
    public static float cooldownFraction(int index) {
        Minecraft minecraft = Minecraft.getInstance();
        checkOwner();
        CooldownKey key = new CooldownKey(owner, index);
        Long ready = COOLDOWNS.get(key);
        Integer length = LENGTHS.get(key);
        if (minecraft.level == null || ready == null || length == null || length <= 0) {
            return 0.0F;
        }
        long left = ready - minecraft.level.getGameTime();
        if (left <= 0) {
            COOLDOWNS.remove(key);
            LENGTHS.remove(key);
            return 0.0F;
        }
        return Math.min(1.0F, left / (float) length);
    }

    /** Сколько секунд перезарядки осталось, с округлением вверх; 0 — готово. */
    public static int cooldownSeconds(int index) {
        Minecraft minecraft = Minecraft.getInstance();
        checkOwner();
        Long ready = COOLDOWNS.get(new CooldownKey(owner, index));
        if (minecraft.level == null || ready == null) {
            return 0;
        }
        long left = ready - minecraft.level.getGameTime();
        return left <= 0 ? 0 : (int) ((left + 19) / 20);
    }

    private static void checkOwner() {
        LocalPlayer player = Minecraft.getInstance().player;
        MutagenData.Gene gene = player == null ? null : MutagenPlayer.of(player).primary();
        ResourceLocation current = gene == null ? null : gene.strainId();
        Object currentConnection = Minecraft.getInstance().getConnection();
        if (connection != currentConnection) {
            clear();
            connection = currentConnection;
        }
        owner = current;
    }

    public static void clear() {
        COOLDOWNS.clear();
        LENGTHS.clear();
        owner = null;
        connection = null;
    }

    /** Способность вместе с её номером и тем, доступна ли она сейчас. */
    public record Slot(int index, Ability ability, boolean unlocked) {
        @Nullable
        public Ability abilityOrNull() {
            return ability;
        }
    }
}
