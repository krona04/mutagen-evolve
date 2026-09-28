package net.krona.mutagen.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.krona.mutagen.client.MutagenClient;
import net.krona.mutagen.knowledge.ClientKnowledge;
import net.krona.mutagen.knowledge.Research;
import net.krona.mutagen.registry.MutagenItems;
import net.krona.mutagen.strain.Strain;
import net.krona.mutagen.strain.Strains;
import net.krona.mutagen.strain.Trait;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Гено-древо: каталог встреченных видов и их генов.
 * <p>
 * Вид появляется здесь с первым образцом. Пока геном прочитан меньше чем на четверть, гены скрыты:
 * игрок видит, что у существа что-то есть, но не знает что — это и есть повод идти к секвенатору.
 */
@Environment(EnvType.CLIENT)
public class GeneTreeScreen extends Screen {
    private static final int LIST_WIDTH = 126;
    private static final int ROW = 22;
    private static final int PAD = 6;
    private static final int TEXT = 0xFFE6E6E6;
    private static final int MUTED = 0xFF8A8F99;

    private final Map<EntityType<?>, LivingEntity> models = new HashMap<>();
    private List<Strain> entries = List.of();
    @Nullable
    private ResourceLocation selected;
    private int listScroll;
    private int detailScroll;
    private int detailHeight;

    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;

    public GeneTreeScreen() {
        super(Component.translatable("mutagen.gene_tree.title"));
    }

    @Override
    protected void init() {
        panelWidth = Math.min(width - 20, 400);
        panelHeight = Math.min(height - 20, 240);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        refresh();
    }

    /** Список пересобирается каждый кадр: изученность может вырасти, пока экран открыт. */
    private void refresh() {
        List<Strain> known = new ArrayList<>();
        for (ResourceLocation id : ClientKnowledge.all().keySet()) {
            Strain strain = Strains.get(id);
            if (strain != null) {
                known.add(strain);
            }
        }
        known.sort(Comparator.comparing(strain -> strain.displayName().getString()));
        entries = known;
        if (selected == null || entries.stream().noneMatch(strain -> strain.id().equals(selected))) {
            selected = entries.isEmpty() ? null : entries.get(0).id();
            detailScroll = 0;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        refresh();

        graphics.fill(left, top, left + panelWidth, top + panelHeight, 0xE0101418);
        graphics.renderOutline(left, top, panelWidth, panelHeight, 0xFF3A4A52);
        graphics.drawString(font, title, left + PAD + 2, top + 6, 0xFFFFFFFF, true);

        int bodyTop = top + 20;
        int bodyBottom = top + panelHeight - 18;
        Component footer = Component.translatable("mutagen.gene_tree.footer", entries.size(), Strains.count());
        graphics.drawString(font, footer, left + PAD + 2, bodyBottom + 5, MUTED, false);

        if (entries.isEmpty()) {
            List<FormattedCharSequence> lines = font.split(
                    Component.translatable("mutagen.gene_tree.empty"), panelWidth - 40);
            int y = (bodyTop + bodyBottom) / 2 - lines.size() * 5;
            for (FormattedCharSequence line : lines) {
                graphics.drawString(font, line, left + (panelWidth - font.width(line)) / 2, y, MUTED, false);
                y += 10;
            }
            return;
        }

        renderList(graphics, mouseX, mouseY, bodyTop, bodyBottom);
        Strain strain = selectedStrain();
        if (strain != null) {
            renderDetails(graphics, mouseX, mouseY, strain, bodyTop, bodyBottom);
        }
    }

    private void renderList(GuiGraphics graphics, int mouseX, int mouseY, int bodyTop, int bodyBottom) {
        int x = left + PAD;
        int visible = (bodyBottom - bodyTop) / ROW;
        listScroll = Mth.clamp(listScroll, 0, Math.max(0, entries.size() - visible));

        graphics.fill(x, bodyTop, x + LIST_WIDTH, bodyBottom, 0x60000000);
        for (int i = 0; i < visible && i + listScroll < entries.size(); i++) {
            Strain strain = entries.get(i + listScroll);
            int y = bodyTop + i * ROW;
            boolean hovered = mouseX >= x && mouseX < x + LIST_WIDTH && mouseY >= y && mouseY < y + ROW;
            if (strain.id().equals(selected)) {
                graphics.fill(x, y, x + LIST_WIDTH, y + ROW, 0x40FFFFFF);
            } else if (hovered) {
                graphics.fill(x, y, x + LIST_WIDTH, y + ROW, 0x20FFFFFF);
            }
            graphics.renderItem(icon(strain), x + 3, y + 3);
            String name = font.plainSubstrByWidth(strain.displayName().getString(), LIST_WIDTH - 28);
            graphics.drawString(font, name, x + 23, y + 3, 0xFF000000 | strain.color(), true);
            float research = Math.max(0.0F, ClientKnowledge.research(strain.id()));
            ResearchBar.draw(graphics, x + 23, y + 14, LIST_WIDTH - 30, 3, research,
                    ResearchBar.color(strain.color(), research));
        }
    }

    private void renderDetails(GuiGraphics graphics, int mouseX, int mouseY, Strain strain, int bodyTop,
                               int bodyBottom) {
        int x = left + PAD + LIST_WIDTH + PAD;
        int right = left + panelWidth - PAD;
        int modelWidth = 64;
        int textRight = right - modelWidth - 4;
        int textWidth = textRight - x;
        float research = Math.max(0.0F, ClientKnowledge.research(strain.id()));

        LivingEntity model = model(strain.entityType());
        if (model != null) {
            float size = Math.max(model.getBbHeight(), model.getBbWidth());
            int scale = Mth.clamp((int) (54.0F / Math.max(0.5F, size)), 8, 40);
            InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, right - modelWidth, bodyTop,
                    right, bodyTop + 88, scale, 0.0625F, mouseX, mouseY, model);
        }

        graphics.enableScissor(x, bodyTop, textRight, bodyBottom);
        int y = bodyTop + 2 - detailScroll;
        graphics.drawString(font, strain.displayName().copy().withStyle(ChatFormatting.BOLD), x, y,
                0xFF000000 | strain.color(), true);
        y += 12;
        y = wrap(graphics, Component.translatable("mutagen.gene_tree.difficulty", strain.difficulty()),
                x, y, textWidth, MUTED);
        y = wrap(graphics, Component.translatable("mutagen.gene_tree.research", String.format("%.1f", research)),
                x, y + 2, textWidth, TEXT);
        ResearchBar.draw(graphics, x, y + 1, textWidth, 5, research, ResearchBar.color(strain.color(), research));
        y += 11;

        for (float threshold : Research.THRESHOLDS) {
            boolean reached = research >= threshold;
            MutableComponent line = Component.literal((reached ? "✔ " : "• ") + (int) threshold + "% — ")
                    .append(Research.unlockText(threshold));
            y = wrap(graphics, line, x, y, textWidth, reached ? 0xFF7FD67F : MUTED);
        }

        y += 4;
        graphics.drawString(font, Component.translatable("mutagen.gene_tree.genes"), x, y, TEXT, true);
        y += 11;
        if (Research.genesVisible(research)) {
            for (Trait trait : strain.traits()) {
                MutableComponent line = Component.literal("[" + trait.minStage().numeral() + "] ")
                        .append(trait.description());
                y = wrap(graphics, line, x, y, textWidth, trait.weakness() ? 0xFFE07070 : 0xFF7FD67F);
            }
        } else {
            y = wrap(graphics, Component.translatable("mutagen.gene_tree.hidden", (int) Research.GENES),
                    x, y, textWidth, MUTED);
        }

        y += 4;
        Strain.Synthesis synthesis = strain.synthesis();
        Component catalyst = new ItemStack(synthesis.catalyst()).getHoverName();
        y = wrap(graphics, Component.translatable("mutagen.gene_tree.synthesis", catalyst,
                synthesis.catalystCount(), String.format("%.0f", synthesis.time() / 20.0F)), x, y, textWidth, MUTED);

        if (Research.genesVisible(research)) {
            y = wrap(graphics, Component.translatable("mutagen.gene_tree.body",
                    String.format("%.2f", strain.targetWidth()), String.format("%.2f", strain.targetHeight()),
                    String.format("%.2f", strain.targetEyeHeight()), String.format("%+.1f", strain.body().reach())),
                    x, y, textWidth, MUTED);
        }

        graphics.disableScissor();
        detailHeight = y + detailScroll - bodyTop;
        detailScroll = Mth.clamp(detailScroll, 0, Math.max(0, detailHeight - (bodyBottom - bodyTop)));
    }

    private int wrap(GuiGraphics graphics, Component text, int x, int y, int width, int color) {
        for (FormattedCharSequence line : font.split(text, width)) {
            graphics.drawString(font, line, x, y, color, false);
            y += 10;
        }
        return y;
    }

    private static ItemStack icon(Strain strain) {
        SpawnEggItem egg = SpawnEggItem.byId(strain.entityType());
        return new ItemStack(egg != null ? egg : MutagenItems.SAMPLE.get());
    }

    @Nullable
    private LivingEntity model(EntityType<?> type) {
        if (minecraft == null || minecraft.level == null) {
            return null;
        }
        if (models.containsKey(type)) {
            return models.get(type);
        }
        Entity entity = type.create(minecraft.level);
        LivingEntity living = entity instanceof LivingEntity found ? found : null;
        models.put(type, living);
        return living;
    }

    @Nullable
    private Strain selectedStrain() {
        return selected == null ? null : Strains.get(selected);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = left + PAD;
        int bodyTop = top + 20;
        if (button == 0 && mouseX >= x && mouseX < x + LIST_WIDTH && mouseY >= bodyTop) {
            int index = (int) ((mouseY - bodyTop) / ROW) + listScroll;
            if (index >= 0 && index < entries.size() && mouseY < top + panelHeight - 18) {
                selected = entries.get(index).id();
                detailScroll = 0;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX < left + PAD + LIST_WIDTH) {
            listScroll -= (int) Math.signum(scrollY);
        } else {
            detailScroll -= (int) (Math.signum(scrollY) * 12);
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (MutagenClient.GENE_TREE_KEY.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
