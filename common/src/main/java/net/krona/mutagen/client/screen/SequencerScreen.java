package net.krona.mutagen.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.krona.mutagen.Mutagen;
import net.krona.mutagen.data.GeneData;
import net.krona.mutagen.item.GeneItem;
import net.krona.mutagen.knowledge.ClientKnowledge;
import net.krona.mutagen.knowledge.Research;
import net.krona.mutagen.menu.SequencerMenu;
import net.krona.mutagen.strain.Strain;
import net.krona.mutagen.strain.Strains;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Экран секвенатора: слева образец и редстоун, справа — что сейчас читается и сколько уже прочитано.
 */
@Environment(EnvType.CLIENT)
public class SequencerScreen extends MachineScreen<SequencerMenu> {
    private static final ResourceLocation TEXTURE = Mutagen.id("textures/gui/container/sequencer.png");
    private static final int PANEL_X = 88;
    private static final int PANEL_Y = 19;
    private static final int PANEL_WIDTH = 78;
    private static final int PANEL_HEIGHT = 48;

    public SequencerScreen(SequencerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, TEXTURE);
    }

    @Nullable
    private Strain strain() {
        GeneData gene = GeneItem.gene(menu.sample());
        return gene == null ? null : Strains.get(gene.strain());
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        graphics.blit(TEXTURE, x, y, 0, 0, imageWidth, imageHeight);

        int arrow = Math.round(24.0F * menu.progress() / menu.maxProgress());
        if (arrow > 0) {
            graphics.blit(TEXTURE, x + 58, y + 34, 176, 0, arrow, 17);
        }

        Strain strain = strain();
        int px = x + PANEL_X;
        int py = y + PANEL_Y;
        if (strain == null) {
            List<FormattedCharSequence> lines = font.split(
                    Component.translatable("mutagen.sequencer.insert"), PANEL_WIDTH);
            int ly = py + (PANEL_HEIGHT - lines.size() * 10) / 2;
            for (FormattedCharSequence line : lines) {
                graphics.drawString(font, line, px, ly, 0xFF8A8F99, false);
                ly += 10;
            }
            return;
        }

        float research = Math.max(0.0F, ClientKnowledge.research(strain.id()));
        graphics.drawString(font, font.plainSubstrByWidth(strain.displayName().getString(), PANEL_WIDTH),
                px, py, 0xFF000000 | strain.color(), true);
        graphics.drawString(font, String.format("%.1f%%", research), px, py + 11, 0xFFFFFFFF, true);
        ResearchBar.draw(graphics, px, py + 22, PANEL_WIDTH, 5, research, ResearchBar.color(strain.color(), research));

        float next = Research.nextThreshold(research);
        Component hint = next < 0.0F
                ? Component.translatable("mutagen.sequencer.mastered")
                : Component.translatable("mutagen.sequencer.next", (int) next);
        graphics.drawString(font, font.plainSubstrByWidth(hint.getString(), PANEL_WIDTH), px, py + 32,
                0xFFB8C0CC, false);
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderTooltip(graphics, mouseX, mouseY);
        Strain strain = strain();
        if (strain == null || !isHovering(PANEL_X - 2, PANEL_Y - 2, PANEL_WIDTH + 4, PANEL_HEIGHT, mouseX, mouseY)) {
            return;
        }
        float research = Math.max(0.0F, ClientKnowledge.research(strain.id()));
        List<Component> lines = new ArrayList<>();
        lines.add(strain.displayName().copy().withStyle(ChatFormatting.WHITE));
        for (float threshold : Research.THRESHOLDS) {
            boolean reached = research >= threshold;
            lines.add(Component.literal((reached ? "✔ " : "• ") + (int) threshold + "% — ")
                    .append(Research.unlockText(threshold))
                    .withStyle(reached ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        }
        lines.add(Component.translatable("mutagen.sequencer.gain",
                String.format("%.1f", Research.gain(strain, qualityOfSample()))).withStyle(ChatFormatting.DARK_AQUA));
        graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
    }

    private float qualityOfSample() {
        GeneData gene = GeneItem.gene(menu.sample());
        return gene == null ? 0.0F : gene.quality();
    }
}
