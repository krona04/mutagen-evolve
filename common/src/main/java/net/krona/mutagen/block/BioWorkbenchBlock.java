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
 * Верстак биолога: рабочая поверхность лаборатории, на которой собирают снаряжение и реагенты.
 * <p>
 * С 0.2 у верстака свой тип рецептов ({@code mutagen:bio_workbench}) и свой экран со списком
 * сборок. Сам верстак собирается на обычном, всё остальное снаряжение лаборатории — уже на нём.
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
