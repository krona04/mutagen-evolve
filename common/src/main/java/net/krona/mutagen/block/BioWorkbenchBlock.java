package net.krona.mutagen.block;

import net.krona.mutagen.menu.BioWorkbenchMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Biologist's workbench: the lab's work surface, where equipment and reagents are assembled.
 * <p>
 * Since 0.2 the workbench has its own recipe type ({@code mutagen:bio_workbench}) and its own screen listing
 * the assemblies. The workbench itself is crafted on a regular table; all other lab equipment is made on it.
 */
public class BioWorkbenchBlock extends Block {
    private static final Component TITLE = Component.translatable("container.mutagen.bio_workbench");

    public BioWorkbenchBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        player.openMenu(new SimpleMenuProvider(
                (id, inventory, p) -> new BioWorkbenchMenu(id, inventory, ContainerLevelAccess.create(level, pos)),
                TITLE));
        return InteractionResult.CONSUME;
    }
}
