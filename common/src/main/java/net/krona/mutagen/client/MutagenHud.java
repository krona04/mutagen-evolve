package net.krona.mutagen.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.krona.mutagen.MutagenConfig;
import net.krona.mutagen.data.MutagenData;
import net.krona.mutagen.data.MutagenPlayer;
import net.krona.mutagen.strain.Stage;
import net.krona.mutagen.strain.Strain;
import net.krona.mutagen.strain.Strains;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;

/**
 * Индикатор мутации: стадия, прогресс и запас усвоения.
 * Игрок должен понимать, что с ним происходит, не открывая команд.
 */
@Environment(EnvType.CLIENT)
public final class MutagenHud {
    private static final int BAR_WIDTH = 80;
    private static final int BAR_HEIGHT = 5;

    private MutagenHud() {
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) {
            return;
        }

        renderFlash(graphics, minecraft);
        LockedSlots.renderHotbar(graphics, minecraft);

        if (!MutagenConfig.get().hud) {
            return;
        }

        MutagenData data = MutagenPlayer.of(minecraft.player);
        MutagenData.Gene gene = data.primary();
        if (gene == null) {
            return;
        }
        Strain strain = Strains.get(gene.strainId());
        if (strain == null) {
            return;
        }

        Stage stage = gene.stage();
        int x = 8;
        int y = graphics.guiHeight() - 72;

        Component title = Component.translatable("mutagen.hud.title",
                strain.displayName(), stage.numeral());
        graphics.drawString(minecraft.font, title, x, y, 0xFFFFFFFF, true);

        int barY = y + 11;
        graphics.fill(x - 1, barY - 1, x + BAR_WIDTH + 1, barY + BAR_HEIGHT + 1, 0xAA000000);

        int progressWidth = Mth.ceil(BAR_WIDTH * gene.progress() / 100.0F);
        graphics.fill(x, barY, x + progressWidth, barY + BAR_HEIGHT, 0xFF000000 | strain.color());

        // Запас усвоения: то, что тело ещё не переработало.
        int pendingWidth = Mth.ceil(BAR_WIDTH * Math.min(100.0F, gene.progress() + gene.pending()) / 100.0F);
        if (pendingWidth > progressWidth) {
            graphics.fill(x + progressWidth, barY, x + pendingWidth, barY + BAR_HEIGHT,
                    FastColor.ARGB32.color(120, (strain.color() >> 16) & 0xFF,
                            (strain.color() >> 8) & 0xFF, strain.color() & 0xFF));
        }

        if (gene.locked()) {
            graphics.drawString(minecraft.font, "■", x + BAR_WIDTH + 4, barY - 2, 0xFFFFD24A, true);
        }

        if (minecraft.getDebugOverlay().showDebugScreen()) {
            graphics.drawString(minecraft.font, String.format("%.1f%% (+%.1f) human %.0f%%",
                            gene.progress(), gene.pending(), data.humanity()),
                    x, barY + BAR_HEIGHT + 3, 0xFFAAAAAA, true);
        }
    }

    private static void renderFlash(GuiGraphics graphics, Minecraft minecraft) {
        float alpha = MutagenClientState.flashAlpha();
        if (alpha <= 0.0F) {
            return;
        }
        int color = MutagenClientState.flashColor();
        int argb = FastColor.ARGB32.color((int) (alpha * 110), (color >> 16) & 0xFF,
                (color >> 8) & 0xFF, color & 0xFF);
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), argb);

        Stage stage = MutagenClientState.lastStage();
        if (stage != Stage.NONE && alpha > 0.35F) {
            Component text = Component.translatable(
                    MutagenClientState.lastUp() ? "mutagen.hud.stage_up" : "mutagen.hud.stage_down",
                    stage.displayName());
            int width = minecraft.font.width(text);
            graphics.drawString(minecraft.font, text,
                    (graphics.guiWidth() - width) / 2, graphics.guiHeight() / 2 - 30,
                    0xFFFFFFFF, true);
        }
    }
}
