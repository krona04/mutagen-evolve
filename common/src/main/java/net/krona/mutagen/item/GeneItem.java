package net.krona.mutagen.item;

import net.krona.mutagen.data.GeneData;
import net.krona.mutagen.knowledge.ClientKnowledge;
import net.krona.mutagen.registry.MutagenComponents;
import net.krona.mutagen.strain.Strain;
import net.krona.mutagen.strain.Strains;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Предмет, который несёт генетический материал: образец, геном или сыворотка.
 */
public class GeneItem extends Item {
    public GeneItem(Properties properties) {
        super(properties);
    }

    @Nullable
    public static GeneData gene(ItemStack stack) {
        return stack.get(MutagenComponents.GENE.get());
    }

    public static ItemStack withGene(ItemStack stack, GeneData gene) {
        stack.set(MutagenComponents.GENE.get(), gene);
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        GeneData gene = gene(stack);
        if (gene == null) {
            tooltip.add(Component.translatable("mutagen.tooltip.empty_vial").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        Strain strain = Strains.get(gene.strain());
        Component name = strain != null
                ? strain.displayName()
                : Component.literal(gene.strain().toString());
        tooltip.add(Component.translatable("mutagen.tooltip.strain", name).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("mutagen.tooltip.quality", String.format("%.0f", gene.quality()))
                .withStyle(qualityColor(gene.quality())));
        if (gene.limit() < GeneData.NO_LIMIT) {
            tooltip.add(Component.translatable("mutagen.tooltip.limit", gene.limit())
                    .withStyle(ChatFormatting.GOLD));
        }

        // Изученность видна только тому, кто её знает: на клиенте она приходит с сервера.
        float research = ClientKnowledge.research(gene.strain());
        if (research >= 0.0F && !(this instanceof SerumItem)) {
            tooltip.add(Component.translatable("mutagen.tooltip.research", String.format("%.0f", research))
                    .withStyle(ChatFormatting.DARK_AQUA));
        }
    }

    private static ChatFormatting qualityColor(float quality) {
        if (quality >= 75.0F) {
            return ChatFormatting.GREEN;
        }
        if (quality >= 50.0F) {
            return ChatFormatting.YELLOW;
        }
        return ChatFormatting.RED;
    }
}
