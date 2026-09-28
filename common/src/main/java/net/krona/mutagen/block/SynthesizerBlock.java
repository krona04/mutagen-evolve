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

public class SynthesizerBlock extends MachineBlock {
    public static final MapCodec<SynthesizerBlock> CODEC = simpleCodec(SynthesizerBlock::new);

    public SynthesizerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends SynthesizerBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SynthesizerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                 BlockEntityType<T> type) {
        return createTickerHelper(type, MutagenBlocks.SYNTHESIZER_ENTITY.get(), MachineBlockEntity::tick);
    }
}
