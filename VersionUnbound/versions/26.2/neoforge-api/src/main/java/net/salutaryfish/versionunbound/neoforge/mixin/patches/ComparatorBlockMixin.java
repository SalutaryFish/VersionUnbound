package net.salutaryfish.versionunbound.neoforge.mixin.patches;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

/**
 * NeoForge's patch to {@code ComparatorBlock}: it wants weak changes, and a change next to it on its own level
 * updates it, as vanilla's {@code updateNeighbourForOutputSignal} did directly (LevelNeighborChangesMixin).
 */
@Mixin(ComparatorBlock.class)
abstract class ComparatorBlockMixin {
    public boolean getWeakChanges(BlockState state, LevelReader world, BlockPos pos) {
        return state.is(Blocks.COMPARATOR);
    }

    public void onNeighborChange(BlockState state, LevelReader levelReader, BlockPos pos, BlockPos neighbor) {
        if (pos.getY() == neighbor.getY() && levelReader instanceof Level level && !levelReader.isClientSide()) {
            // TODO porting: check this still works as expected
            state.handleNeighborChanged(level, pos, levelReader.getBlockState(neighbor).getBlock(), null, false);
        }
    }
}
