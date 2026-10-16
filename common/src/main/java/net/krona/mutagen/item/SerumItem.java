package net.krona.mutagen.item;

import net.krona.mutagen.data.GeneData;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Serum: an injection puts an absorption reserve into the body. Progress doesn't jump — the body processes
 * the dose slowly, and that is the mod's main difference from an instant morph.
 * <p>
 * The injection fires on a single press, without holding: that way there is no "using item" state
 * between the press and the result, which is where everything used to get stuck.
 * The ampoule is consumed only when the dose has actually gone in.
 */
public class SerumItem extends GeneItem {
    private static final int COOLDOWN_TICKS = 20;

    public SerumItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        GeneData gene = gene(stack);

        if (gene == null) {
            if (!level.isClientSide) {
                player.sendSystemMessage(Component.translatable("mutagen.message.empty_serum")
                        .withStyle(ChatFormatting.RED));
            }
            return InteractionResultHolder.fail(stack);
        }

        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.pass(stack);
        }

        // The dose is checked before injecting: a refusal must not cost the player an ampoule.
        if (!Mutation.dose(serverPlayer, gene.strain(), gene.quality(), gene.limit())) {
            return InteractionResultHolder.fail(stack);
        }

        level.playSound(null, player.blockPosition(), SoundEvents.DISPENSER_DISPENSE,
                SoundSource.PLAYERS, 0.7F, 1.5F);
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
}
