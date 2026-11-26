package net.krona.mutagen.item;

import net.krona.mutagen.mutation.Mutation;
import net.krona.mutagen.registry.MutagenItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
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
 * Антимутаген: откатывает прогресс на 15%. Болезненно — и так и задумано.
 * Молоко трансформацию не снимает, иначе вся система обесценивается.
 */
public class ReversalSerumItem extends Item {
    private static final float STEP = 15.0F;
    private static final int COOLDOWN_TICKS = 20;

    public ReversalSerumItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.pass(stack);
        }

        if (!Mutation.reverse(serverPlayer, STEP)) {
            player.sendSystemMessage(Component.translatable("mutagen.message.nothing_to_reverse")
                    .withStyle(ChatFormatting.GRAY));
            return InteractionResultHolder.fail(stack);
        }

        level.playSound(null, player.blockPosition(), SoundEvents.DISPENSER_DISPENSE,
                SoundSource.PLAYERS, 0.7F, 0.9F);
        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);

        if (!player.hasInfiniteMaterials()) {
            stack.shrink(1);
            ItemStack emptySyringe = new ItemStack(MutagenItems.SYRINGE.get());
            if (!player.getInventory().add(emptySyringe)) {
                player.drop(emptySyringe, false);
            }
        }
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("mutagen.tooltip.reversal").withStyle(ChatFormatting.GRAY));
    }
}
