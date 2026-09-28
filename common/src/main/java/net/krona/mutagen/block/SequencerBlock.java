package net.krona.mutagen.block;

import com.mojang.serialization.MapCodec;
import net.krona.mutagen.registry.MutagenBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class SequencerBlock extends MachineBlock {
    public static final MapCodec<SequencerBlock> CODEC = simpleCodec(SequencerBlock::new);

    public SequencerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends SequencerBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SequencerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                 BlockEntityType<T> type) {
        return createTickerHelper(type, MutagenBlocks.SEQUENCER_ENTITY.get(), MachineBlockEntity::tick);
    }
}
