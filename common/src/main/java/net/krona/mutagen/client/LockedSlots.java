package net.krona.mutagen.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.krona.mutagen.mutation.Restrictions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;

/**
 * Запертые ячейки видно сразу: они затянуты тёмной плёнкой и на поясе, и в любом окне с инвентарём.
 * Выбор ячейки пояса колесом тоже не заходит в запертые.
 */
@Environment(EnvType.CLIENT)
public final class LockedSlots {
    private static final int SHADE = 0xC0281010;
    private static final int MARK = 0xFF7A2A2A;
    private static final float HUD_Z = 2000.0F;

    private LockedSlots() {
    }

    public static void tick(Minecraft minecraft) {
        if (minecraft.player == null) {
            return;
        }
        Restrictions.Active active = Restrictions.of(minecraft.player);
        Inventory inventory = minecraft.player.getInventory();
        if (inventory.selected >= active.hotbar()) {
            inventory.selected = active.hotbar() - 1;
        }
    }

    public static void renderHotbar(GuiGraphics graphics, Minecraft minecraft) {
        if (minecraft.player == null || minecraft.options.hideGui || minecraft.player.isSpectator()) {
            return;
        }
        Restrictions.Active active = Restrictions.of(minecraft.player);
        if (active.hotbar() >= Inventory.getSelectionSize()) {
            return;
        }
        int left = graphics.guiWidth() / 2 - 91;
        int top = graphics.guiHeight() - 22;
        graphics.pose().pushPose();
        // Интерфейс в 1.21 рисуется слоями, и каждый следующий слой поднимается по глубине; хук HUD может
        // прийти раньше слоя пояса, поэтому плёнка поднимается заведомо выше него.
        graphics.pose().translate(0.0F, 0.0F, HUD_Z);
        for (int i = active.hotbar(); i < Inventory.getSelectionSize(); i++) {
            int x = left + 3 + i * 20;
            shade(graphics, x, top + 3);
        }
        graphics.pose().popPose();
    }

    public static void renderContainer(AbstractContainerScreen<?> screen, GuiGraphics graphics, int mouseX, int mouseY,
                                       float delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        Restrictions.Active active = Restrictions.of(minecraft.player);
        if (!active.any()) {
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 300.0F);
        for (Slot slot : screen.getMenu().slots) {
            boolean locked = slot.container instanceof Inventory && Restrictions.locked(active, slot.getContainerSlot());
            boolean grid = slot.container instanceof TransientCraftingContainer && active.noCrafting();
            if (locked || grid) {
                shade(graphics, slot.x, slot.y);
            }
        }
        graphics.pose().popPose();
    }

    private static void shade(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 16, y + 16, SHADE);
        // Косая черта: запертая ячейка должна читаться, даже если в ней ничего не лежит.
        for (int i = 2; i < 14; i++) {
            graphics.fill(x + i, y + 15 - i, x + i + 1, y + 16 - i, MARK);
        }
    }
}
