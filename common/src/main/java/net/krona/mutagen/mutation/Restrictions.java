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
 * What the host's body can no longer do. Built from the strain's traits at the current stage, so
 * the server and the client compute the same thing: the client knows the strains and progress just like the server.
 * <p>
 * Design document rule: restrictions are never silent. Every refusal is explained by an action bar
 * message, and locked inventory slots are dimmed. Items in locked slots don't go anywhere —
 * they come back to the host together with the hands.
 */
public final class Restrictions {
    /** Workstations that need fingers. */
    public static final TagKey<Block> STATIONS = TagKey.create(Registries.BLOCK, Mutagen.id("needs_hands"));
    /** Tools and weapons there is nothing to hold with. */
    public static final TagKey<Item> TOOLS = TagKey.create(Registries.ITEM, Mutagen.id("needs_hands"));

    public static final Active NONE = new Active(Inventory.getSelectionSize(), false, false, false, false, false,
            List.of(), false, null, -1);

    private static final long MESSAGE_COOLDOWN_MS = 1500L;
    private static final Map<UUID, Long> LAST_MESSAGE = new HashMap<>();

    private Restrictions() {
    }

    /**
     * Active restrictions.
     *
     * @param hotbar     how many of the first hotbar slots are available
     * @param lockedMain the backpack (slots 9–35) is locked
     * @param noCrafting the inventory crafting grid is locked
     * @param stations   which workstations are unavailable
     * @param diet       what the body can still eat, or null if there is no food restriction
     * @param foodLevel  the level at which hunger is frozen for a body that doesn't eat; -1 — eats as usual
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

    /** Whether a player inventory slot is locked (index as in {@link Inventory}). */
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

    /** Message key if the body won't eat this, otherwise null. */
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
     * Every tick on the server: the selected hotbar slot can't be locked, and the offhand can't be occupied.
     * The offhand item is moved to a free available slot, and if there is none, it drops at the feet.
     */
    public static void tick(ServerPlayer player) {
        Active active = of(player);
        if (!active.any()) {
            return;
        }
        if (active.foodLevel() >= 0) {
            // A body that doesn't eat doesn't starve either: hunger is frozen where the transformation left it.
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

    /** Explain a refusal with an action bar message — no more than once every one and a half seconds. */
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
