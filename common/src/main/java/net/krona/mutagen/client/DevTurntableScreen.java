package net.krona.mutagen.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Инструмент разработки: игрок с четырёх сторон на одном кадре — спереди, вполоборота, сбоку и сзади.
 * Открывается только из {@link DevShots} (вид {@code turntable}), у игроков его нет.
 */
@Environment(EnvType.CLIENT)
public class DevTurntableScreen extends Screen {
    private static final float[] ANGLES = {0.0F, 45.0F, 90.0F, 180.0F};

    private final AbstractClientPlayer player;

    public DevTurntableScreen(AbstractClientPlayer player) {
        super(Component.literal("turntable"));
        this.player = player;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xFF8FA7B8);
        graphics.fill(0, height * 3 / 4, width, height, 0xFF5E7A4A);
        float bodyRot = player.yBodyRot;
        float bodyRotO = player.yBodyRotO;
        float yRot = player.getYRot();
        float yRotO = player.yRotO;
        float xRot = player.getXRot();
        float xRotO = player.xRotO;
        float headRot = player.yHeadRot;
        float headRotO = player.yHeadRotO;

        int cell = width / ANGLES.length;
        int scale = Math.min(cell, height) / 3;
        for (int i = 0; i < ANGLES.length; i++) {
            float angle = 180.0F + ANGLES[i];
            player.yBodyRot = angle;
            player.yBodyRotO = angle;
            player.setYRot(angle);
            player.yRotO = angle;
            player.setXRot(0.0F);
            player.xRotO = 0.0F;
            player.yHeadRot = angle;
            player.yHeadRotO = angle;
            float x = cell * i + cell / 2.0F;
            float y = height * 3 / 4.0F;
            InventoryScreen.renderEntityInInventory(graphics, x, y, scale,
                    new Vector3f(0.0F, 0.0F, 0.0F), new Quaternionf().rotateZ((float) Math.PI), null, player);
        }

        player.yBodyRot = bodyRot;
        player.yBodyRotO = bodyRotO;
        player.setYRot(yRot);
        player.yRotO = yRotO;
        player.setXRot(xRot);
        player.xRotO = xRotO;
        player.yHeadRot = headRot;
        player.yHeadRotO = headRotO;

        graphics.drawString(font, "model: " + player.getSkin().model().id(), 4, 4, 0xFFFFFFFF, true);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
