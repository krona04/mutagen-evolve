package net.krona.mutagen.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.krona.mutagen.Mutagen;
import net.krona.mutagen.block.SynthesizerBlockEntity;
import net.krona.mutagen.data.GeneData;
import net.krona.mutagen.item.GeneItem;
import net.krona.mutagen.knowledge.ClientKnowledge;
import net.krona.mutagen.knowledge.Research;
import net.krona.mutagen.menu.SynthesizerMenu;
import net.krona.mutagen.registry.MutagenItems;
import net.krona.mutagen.strain.Strain;
import net.krona.mutagen.strain.Strains;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class SynthesizerScreen extends MachineScreen<SynthesizerMenu> {
    private static final ResourceLocation TEXTURE = Mutagen.id("textures/gui/container/synthesizer.png");
    private static final int STATUS_X = 64;
    private static final int STATUS_Y = 60;

    public SynthesizerScreen(SynthesizerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, TEXTURE);
    }

    @Override
    protected boolean usesFuel() {
        return false;
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        Status status = status();
        if (status != null) {
            String text = font.plainSubstrByWidth(status.text().getString(), imageWidth - STATUS_X - 6);
            graphics.drawString(font, text, STATUS_X, STATUS_Y, status.color(), false);
        }
    }

    /**
     * Почему синтез стоит или чем он кончится. Всё считается из того, что клиент и так видит:
     * содержимого слотов, штаммов и своей изученности.
     */
    @Nullable
    private Status status() {
        ItemStack genome = menu.machineItem(SynthesizerBlockEntity.SLOT_GENOME);
        GeneData gene = genome.is(MutagenItems.GENOME.get()) ? GeneItem.gene(genome) : null;
        if (gene == null) {
            return null;
        }
        Strain strain = Strains.get(gene.strain());
        if (strain == null) {
            return new Status(Component.translatable("mutagen.synthesizer.unknown"), 0xFFB03030);
        }
        int limit = Research.serumLimit(Math.max(0.0F, ClientKnowledge.research(strain.id())));
        if (limit <= 0) {
            return new Status(Component.translatable("mutagen.synthesizer.needs_research", (int) Research.GENES),
                    0xFFB03030);
        }
        Strain.Synthesis synthesis = strain.synthesis();
        ItemStack catalyst = menu.machineItem(SynthesizerBlockEntity.SLOT_CATALYST);
        if (!catalyst.is(synthesis.catalyst()) || catalyst.getCount() < synthesis.catalystCount()) {
            return new Status(Component.translatable("mutagen.synthesizer.catalyst",
                    new ItemStack(synthesis.catalyst()).getHoverName(), synthesis.catalystCount()), 0xFFB03030);
        }
        int ampoules = SynthesizerBlockEntity.serumYield(gene.quality());
        if (limit < 100) {
            return new Status(Component.translatable("mutagen.synthesizer.limit", limit, ampoules), 0xFF9A6A00);
        }
        return new Status(Component.translatable("mutagen.synthesizer.ready", ampoules), 0xFF2F6F2F);
    }

    private record Status(Component text, int color) {
    }
}
