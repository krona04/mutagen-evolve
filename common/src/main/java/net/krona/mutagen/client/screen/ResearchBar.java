package net.krona.mutagen.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.krona.mutagen.knowledge.Research;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FastColor;

/** Полоса изученности с засечками порогов — общая для секвенатора и гено-древа. */
@Environment(EnvType.CLIENT)
final class ResearchBar {
    private ResearchBar() {
    }

    static void draw(GuiGraphics graphics, int x, int y, int width, int height, float research, int color) {
        graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, 0xFF000000);
        graphics.fill(x, y, x + width, y + height, 0xFF2A2E36);
        int filled = Math.round(width * Math.max(0.0F, Math.min(100.0F, research)) / 100.0F);
        if (filled > 0) {
            graphics.fill(x, y, x + filled, y + height, 0xFF000000 | color);
        }
        for (float threshold : Research.THRESHOLDS) {
            if (threshold >= 100.0F) {
                continue;
            }
            int tick = x + Math.round(width * threshold / 100.0F);
            int tickColor = research >= threshold ? 0xFFFFFFFF : 0xFF8A8F99;
            graphics.fill(tick, y - 1, tick + 1, y + height + 1, tickColor);
        }
    }

    /** Цвет, в который красится полоса: светлее для полностью изученного вида. */
    static int color(int strainColor, float research) {
        if (Research.mastered(research)) {
            return FastColor.ARGB32.color(255,
                    Math.min(255, ((strainColor >> 16) & 0xFF) + 60),
                    Math.min(255, ((strainColor >> 8) & 0xFF) + 60),
                    Math.min(255, (strainColor & 0xFF) + 60)) & 0xFFFFFF;
        }
        return strainColor;
    }
}
