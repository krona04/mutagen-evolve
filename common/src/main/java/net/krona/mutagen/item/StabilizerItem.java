package net.krona.mutagen.item;

import net.krona.mutagen.data.MutagenData;
import net.krona.mutagen.data.MutagenPlayer;
import net.krona.mutagen.strain.Strain;
import net.krona.mutagen.strain.Strains;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Стабилизатор: закрепляет текущий уровень штамма, и прогресс перестаёт откатываться сам.
 * Повторное применение снимает фиксацию.
 */
public class StabilizerItem extends Item {
    public StabilizerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }

        MutagenData data = MutagenPlayer.of(player);
        MutagenData.Gene gene = data.primary();
        if (gene == null) {
            player.displayClientMessage(Component.translatable("mutagen.message.nothing_to_stabilize")
                    .withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.fail(stack);
        }

        gene.setLocked(!gene.locked());
        data.markDirty();

        Strain strain = Strains.get(gene.strainId());
        Component name = strain != null ? strain.displayName() : Component.literal(gene.strainId().toString());
        player.displayClientMessage(Component.translatable(
                gene.locked() ? "mutagen.message.locked" : "mutagen.message.unlocked", name), true);
        level.playSound(null, player.blockPosition(), SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS, 0.7F, 1.2F);

        if (!player.hasInfiniteMaterials()) {
            stack.shrink(1);
        }
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("mutagen.tooltip.stabilizer").withStyle(ChatFormatting.GRAY));
    }
}
