package net.krona.mutagen.mutation;

import net.krona.mutagen.Mutagen;
import net.krona.mutagen.MutagenConfig;
import net.krona.mutagen.data.MutagenData;
import net.krona.mutagen.data.MutagenPlayer;
import net.krona.mutagen.strain.Stage;
import net.krona.mutagen.strain.Strain;
import net.krona.mutagen.strain.Strains;
import net.krona.mutagen.strain.Trait;
import net.krona.mutagen.strain.Traits;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Что тело носителя больше не может делать. Собирается из черт штамма на текущей стадии, поэтому
 * сервер и клиент считают одно и то же: клиент знает штаммы и прогресс так же, как сервер.
 * <p>
 * Правило дизайн-документа: ограничения никогда не молчаливы. Каждый отказ объясняется строкой над
 * хотбаром, а запертые ячейки инвентаря затемнены. Вещи из запертых ячеек никуда не деваются —
 * они вернутся к носителю вместе с руками.
 */
public final class Restrictions {
    /** Станки, для которых нужны пальцы. */
    public static final TagKey<Block> STATIONS = TagKey.create(Registries.BLOCK, Mutagen.id("needs_hands"));
    /** Инструменты и оружие, которые нечем держать. */
    public static final TagKey<Item> TOOLS = TagKey.create(Registries.ITEM, Mutagen.id("needs_hands"));

    public static final Active NONE = new Active(Inventory.getSelectionSize(), false, false, false, false, false,
            List.of(), false, null, -1);

    private static final long MESSAGE_COOLDOWN_MS = 1500L;
    private static final Map<UUID, Long> LAST_MESSAGE = new HashMap<>();

    private Restrictions() {
    }

    /**
     * Действующие ограничения.
     *
     * @param hotbar     сколько первых ячеек пояса доступно
     * @param lockedMain рюкзак (ячейки 9–35) заперт
     * @param noCrafting заперта сетка крафта в инвентаре
     * @param stations   какие станки недоступны
     * @param diet       что тело ещё может есть, или null, если ограничения по еде нет
     * @param foodLevel  на каком уровне застыла сытость у тела, которое не ест; -1 — ест как обычно
     */
    public record Active(int hotbar, boolean lockedMain, boolean noOffhand, boolean noTools, boolean noCrafting,
                         boolean noBuilding, List<TagKey<Block>> stations, boolean noSleep,
                         @Nullable TagKey<Item> diet, int foodLevel) {
        public boolean any() {
            return hotbar < Inventory.getSelectionSize() || lockedMain || noOffhand || noTools || noCrafting
                    || noBuilding || !stations.isEmpty() || noSleep || diet != null || foodLevel >= 0;
        }
    }

    public static Active of(Player player) {
        if (!MutagenConfig.get().humanityPenalties) {
            return NONE;
        }
        MutagenData data = MutagenPlayer.of(player);
        if (data == null || data.isEmpty()) {
            return NONE;
        }
        int hotbar = Inventory.getSelectionSize();
        boolean lockedMain = false;
        boolean noOffhand = false;
        boolean noTools = false;
        boolean noCrafting = false;
        boolean noBuilding = false;
        boolean noSleep = false;
        TagKey<Item> diet = null;
        int foodLevel = -1;
        List<TagKey<Block>> stations = new ArrayList<>();
        for (MutagenData.Gene gene : data.genes()) {
            Strain strain = Strains.get(gene.strainId());
            if (strain == null) {
                continue;
            }
            Stage stage = gene.stage();
            for (Trait trait : strain.activeTraits(stage)) {
                if (trait instanceof Traits.InventoryLimit limit) {
                    hotbar = Math.min(hotbar, limit.hotbar());
                    lockedMain = true;
                } else if (trait instanceof Traits.NoOffhand) {
                    noOffhand = true;
                } else if (trait instanceof Traits.NoTools) {
                    noTools = true;
                } else if (trait instanceof Traits.NoCrafting crafting) {
                    noCrafting |= crafting.grid();
                    stations.add(crafting.stations());
                } else if (trait instanceof Traits.NoBuilding) {
                    noBuilding = true;
                } else if (trait instanceof Traits.NoSleep) {
                    noSleep = true;
                } else if (trait instanceof Traits.Diet food) {
                    diet = food.foods();
                } else if (trait instanceof Traits.NoEating eating) {
                    foodLevel = eating.foodLevel();
                }
            }
        }
        return new Active(hotbar, lockedMain, noOffhand, noTools, noCrafting, noBuilding, stations, noSleep, diet,
                foodLevel);
    }

    /** Заперта ли ячейка инвентаря игрока (номер — как в {@link Inventory}). */
    public static boolean slotLocked(Player player, int index) {
        Active active = of(player);
        if (!active.any()) {
            return false;
        }
        return locked(active, index);
    }

    public static boolean locked(Active active, int index) {
        if (index < Inventory.getSelectionSize()) {
            return index >= active.hotbar();
        }
        if (index < Inventory.INVENTORY_SIZE) {
            return active.lockedMain();
        }
        return index == Inventory.SLOT_OFFHAND && active.noOffhand();
    }

    public static boolean blocksStation(Player player, BlockState state) {
        for (TagKey<Block> tag : of(player).stations()) {
            if (state.is(tag)) {
                return true;
            }
        }
        return false;
    }

    public static boolean blocksSleep(Player player, BlockState state) {
        return state.getBlock() instanceof BedBlock && of(player).noSleep();
    }

    /** Ключ сообщения, если тело не станет это есть, иначе null. */
    @Nullable
    public static String refusesFood(Player player, ItemStack stack) {
        if (!stack.has(DataComponents.FOOD)) {
            return null;
        }
        Active active = of(player);
        if (active.foodLevel() >= 0) {
            return "mutagen.restriction.eating";
        }
        if (active.diet() != null && !stack.is(active.diet())) {
            return "mutagen.restriction.diet";
        }
        return null;
    }

    public static boolean blocksTool(Player player, ItemStack stack) {
        return stack.is(TOOLS) && of(player).noTools();
    }

    public static boolean blocksBuilding(Player player) {
        return of(player).noBuilding();
    }

    /**
     * Каждый тик на сервере: выбранная ячейка пояса не может быть запертой, а вторая рука — занятой.
     * Предмет из второй руки кладётся в свободную доступную ячейку, а если её нет — падает под ноги.
     */
    public static void tick(ServerPlayer player) {
        Active active = of(player);
        if (!active.any()) {
            return;
        }
        if (active.foodLevel() >= 0) {
            // Тело, которое не ест, и не голодает: сытость застыла там, где её оставило превращение.
            player.getFoodData().setFoodLevel(active.foodLevel());
            player.getFoodData().setSaturation(0.0F);
        }
        Inventory inventory = player.getInventory();
        if (inventory.selected >= active.hotbar()) {
            inventory.selected = active.hotbar() - 1;
            player.connection.send(new ClientboundSetCarriedItemPacket(inventory.selected));
        }
        if (active.noOffhand() && player.tickCount % 20 == 0) {
            ItemStack offhand = inventory.offhand.get(0);
            if (!offhand.isEmpty()) {
                inventory.offhand.set(0, ItemStack.EMPTY);
                if (!inventory.add(offhand)) {
                    player.drop(offhand, false);
                }
                tell(player, "mutagen.restriction.offhand");
            }
        }
    }

    /** Объяснить отказ строкой над хотбаром — не чаще раза в полторы секунды. */
    public static void tell(Player player, String key) {
        if (player.level().isClientSide) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = LAST_MESSAGE.get(player.getUUID());
        if (last != null && now - last < MESSAGE_COOLDOWN_MS) {
            return;
        }
        LAST_MESSAGE.put(player.getUUID(), now);
        player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.RED), true);
    }
}
