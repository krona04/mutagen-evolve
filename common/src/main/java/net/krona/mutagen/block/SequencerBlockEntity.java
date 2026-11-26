package net.krona.mutagen.block;

import net.krona.mutagen.MutagenConfig;
import net.krona.mutagen.data.GeneData;
import net.krona.mutagen.item.GeneItem;
import net.krona.mutagen.knowledge.MutagenKnowledge;
import net.krona.mutagen.knowledge.Research;
import net.krona.mutagen.menu.SequencerMenu;
import net.krona.mutagen.registry.MutagenBlocks;
import net.krona.mutagen.registry.MutagenItems;
import net.krona.mutagen.strain.Strain;
import net.krona.mutagen.strain.Strains;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Секвенатор: читает геном вида. Каждый прогон тратит сырой образец и щепотку редстоуна
 * и добавляет процент изученности — чем чище образец и проще вид, тем больше.
 * <p>
 * Сыворотку без изученности не синтезировать, поэтому каждый образец — выбор: изучать вид
 * или колоть себе то, что уже умеешь делать.
 */
public class SequencerBlockEntity extends MachineBlockEntity {
    public static final int SLOT_SAMPLE = 0;
    public static final int SLOT_REAGENT = 1;
    private static final int WORK_TIME = 400;

    public SequencerBlockEntity(BlockPos pos, BlockState state) {
        super(MutagenBlocks.SEQUENCER_ENTITY.get(), pos, state, 2, WORK_TIME);
    }

    public static boolean isReagent(ItemStack stack) {
        return stack.is(Items.REDSTONE);
    }

    @Nullable
    private static Strain strainOf(ItemStack sample) {
        if (!sample.is(MutagenItems.SAMPLE.get())) {
            return null;
        }
        GeneData gene = GeneItem.gene(sample);
        return gene == null ? null : Strains.get(gene.strain());
    }

    @Override
    protected void serverTick(Level level, BlockPos pos, BlockState state) {
        MinecraftServer server = level.getServer();
        if (server == null) {
            return;
        }
        ItemStack sample = items.get(SLOT_SAMPLE);
        ItemStack reagent = items.get(SLOT_REAGENT);
        Strain strain = strainOf(sample);

        MutagenKnowledge knowledge = MutagenKnowledge.get(server);
        boolean personal = MutagenConfig.get().knowledgeScope.equals(MutagenConfig.SCOPE_PLAYER);
        boolean canWork = strain != null
                && isReagent(reagent)
                && (owner != null || !personal)
                && !Research.mastered(knowledge.research(owner, strain.id()));

        boolean changed = false;
        if (canWork) {
            progress++;
            if (progress >= maxProgress) {
                progress = 0;
                finishRun(server, knowledge, strain, sample);
                sample.shrink(1);
                reagent.shrink(1);
                level.playSound(null, pos, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.5F, 1.6F);
            }
            changed = true;
        } else if (progress != 0) {
            progress = 0;
            changed = true;
        }

        if (state.getValue(MachineBlock.LIT) != canWork) {
            level.setBlock(pos, state.setValue(MachineBlock.LIT, canWork), 3);
            changed = true;
        }
        if (changed) {
            setChanged();
        }
    }

    private void finishRun(MinecraftServer server, MutagenKnowledge knowledge, Strain strain, ItemStack sample) {
        GeneData gene = GeneItem.gene(sample);
        float quality = gene == null ? 0.0F : gene.quality();
        float before = knowledge.research(owner, strain.id());
        knowledge.discover(owner, strain.id());
        float after = knowledge.addResearch(owner, strain.id(), Research.gain(strain, quality));
        knowledge.broadcast(server, owner, strain.id());

        float threshold = Research.crossed(before, after);
        ServerPlayer player = owner == null ? null : server.getPlayerList().getPlayer(owner);
        if (threshold > 0.0F && player != null) {
            player.sendSystemMessage(Component.translatable("mutagen.message.research_threshold",
                            strain.displayName(), (int) threshold, Research.unlockText(threshold))
                    .withStyle(ChatFormatting.AQUA));
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.mutagen.sequencer");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        rememberUser(player);
        return new SequencerMenu(id, inventory, this, data);
    }
}
