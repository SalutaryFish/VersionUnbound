package net.salutaryfish.versionunbound.neoforge.mixin.patches;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndLightGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

/**
 * As {@code BlockAppearanceMixin}, for {@code FabricBlockState} and {@code IBlockStateExtension}, whose defaults both
 * ask the block.
 */
@Mixin(BlockState.class)
abstract class BlockStateAppearanceMixin {
    public BlockState getAppearance(BlockAndLightGetter level, BlockPos pos, Direction side, @Nullable BlockState queryState, @Nullable BlockPos queryPos) {
        BlockState self = (BlockState) (Object) this;
        return self.getBlock().getAppearance(self, level, pos, side, queryState, queryPos);
    }
}
