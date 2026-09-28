package net.krona.mutagen.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.krona.mutagen.menu.MachineMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * Общий экран лабораторной машины: фон, стрелка прогресса и огонь под топливом.
 */
@Environment(EnvType.CLIENT)
public abstract class MachineScreen<T extends MachineMenu> extends AbstractContainerScreen<T> {
    private final ResourceLocation texture;

    protected MachineScreen(T menu, Inventory inventory, Component title, ResourceLocation texture) {
        super(menu, inventory, title);
        this.texture = texture;
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        graphics.blit(texture, x, y, 0, 0, imageWidth, imageHeight);

        int arrow = Math.round(24.0F * menu.progress() / menu.maxProgress());
        if (arrow > 0) {
            graphics.blit(texture, x + 79, y + 34, 176, 0, arrow, 17);
        }

        if (usesFuel()) {
            int burn = menu.burnTime();
            if (burn > 0) {
                int height = Math.round(14.0F * burn / menu.burnDuration());
                graphics.blit(texture, x + 56, y + 36 + (14 - height), 176, 17 + (14 - height), 14, height);
            }
        }
    }

    protected boolean usesFuel() {
        return true;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Затемнение позади окна рисует сам AbstractContainerScreen — второй раз его звать не нужно.
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
