package net.krona.mutagen.block;

import net.krona.mutagen.MutagenConfig;
import net.krona.mutagen.data.GeneData;
import net.krona.mutagen.item.GeneItem;
import net.krona.mutagen.knowledge.MutagenKnowledge;
import net.krona.mutagen.knowledge.Research;
import net.krona.mutagen.menu.SynthesizerMenu;
import net.krona.mutagen.registry.MutagenBlocks;
import net.krona.mutagen.registry.MutagenItems;
import net.krona.mutagen.strain.Strain;
import net.krona.mutagen.strain.Strains;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Synthesizer: genome + mutagenic base + catalyst yield a serum.
 * Needs no fuel — the reagents themselves are the price.
 * <p>
 * Since 0.2 synthesis requires species research: below 25% it is impossible, and the serum's cap
 * is set by the threshold (40 / 70 / 100%). The cost of synthesis — the catalyst, its amount and the time —
 * comes from the strain data (0.2.1), and a pure genome from 85% yields two ampoules.
 */
public class SynthesizerBlockEntity extends MachineBlockEntity {
    public static final int SLOT_GENOME = 0;
    public static final int SLOT_BASE = 1;
    public static final int SLOT_CATALYST = 2;
    public static final int SLOT_OUTPUT = 3;

    public SynthesizerBlockEntity(BlockPos pos, BlockState state) {
        super(MutagenBlocks.SYNTHESIZER_ENTITY.get(), pos, state, 4, Strain.Synthesis.DEFAULT.time());
    }

    /** How many ampoules a genome of this purity yields. */
    public static int serumYield(float purity) {
        return purity >= MutagenConfig.get().doubleSerumPurity ? 2 : 1;
    }

    private ItemStack result(MinecraftServer server, @Nullable Strain strain, GeneData gene) {
        ItemStack base = items.get(SLOT_BASE);
        ItemStack catalyst = items.get(SLOT_CATALYST);
        if (strain == null || !base.is(MutagenItems.MUTAGEN_BASE.get())) {
            return ItemStack.EMPTY;
        }
        Strain.Synthesis synthesis = strain.synthesis();
        if (!catalyst.is(synthesis.catalyst()) || catalyst.getCount() < synthesis.catalystCount()) {
            return ItemStack.EMPTY;
        }
        int limit = Research.serumLimit(MutagenKnowledge.get(server).research(owner, strain.id()));
        if (limit <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack serum = new ItemStack(MutagenItems.SERUM.get(), serumYield(gene.quality()));
        return GeneItem.withGene(serum, gene.withLimit(limit));
    }

    @Override
    protected void serverTick(Level level, BlockPos pos, BlockState state) {
        MinecraftServer server = level.getServer();
        if (server == null) {
            return;
        }
        ItemStack genome = items.get(SLOT_GENOME);
        ItemStack output = items.get(SLOT_OUTPUT);
        GeneData gene = genome.is(MutagenItems.GENOME.get()) ? GeneItem.gene(genome) : null;
        Strain strain = gene == null ? null : Strains.get(gene.strain());

        ItemStack result = gene == null ? ItemStack.EMPTY : result(server, strain, gene);
        boolean canWork = !result.isEmpty() && CentrifugeBlockEntity.canAccept(output, result);
        boolean changed = false;

        int time = strain == null ? Strain.Synthesis.DEFAULT.time() : strain.synthesis().time();
        if (maxProgress != time) {
            maxProgress = time;
            changed = true;
        }

        if (canWork) {
            progress++;
            burnTime = 1;
            burnDuration = 1;
            if (progress >= maxProgress) {
                progress = 0;
                genome.shrink(1);
                items.get(SLOT_BASE).shrink(1);
                items.get(SLOT_CATALYST).shrink(strain.synthesis().catalystCount());
                if (output.isEmpty()) {
                    items.set(SLOT_OUTPUT, result);
                } else {
                    output.grow(result.getCount());
                }
            }
            changed = true;
        } else if (progress != 0 || burnTime != 0) {
            progress = 0;
            burnTime = 0;
            changed = true;
        }

        if (state.getValue(SynthesizerBlock.LIT) != canWork) {
            level.setBlock(pos, state.setValue(SynthesizerBlock.LIT, canWork), 3);
            changed = true;
        }
        if (changed) {
            setChanged();
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        // Synthesis time depends on the strain in the slot and is recalculated on the very first tick.
        if (tag.contains("MaxProgress")) {
            maxProgress = Math.max(1, tag.getInt("MaxProgress"));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("MaxProgress", maxProgress);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.mutagen.synthesizer");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        rememberUser(player);
        return new SynthesizerMenu(id, inventory, this, data);
    }
}
