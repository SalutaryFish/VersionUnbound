package net.salutaryfish.versionunbound.neoforge.patches;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Methods of NeoForge's {@code IBlockStateExtension} that its patches already call, injected into {@link BlockState} by
 * the class tweaker; each asks the block ({@link BlockExtension}), as in NeoForge. When the block state extension is
 * ported (Phase 2 step 5), it declares the same methods.
 */
public interface BlockStateExtension {
    /**
     * Determines if this block is can be destroyed by the specified entities normal behavior.
     *
     * @param level The current level
     * @param pos   Block position in level
     * @return True to allow the ender dragon to destroy this block
     */
    default boolean canEntityDestroy(BlockGetter level, BlockPos pos, Entity entity) {
        BlockState self = (BlockState) this;
        return self.getBlock().canEntityDestroy(self, level, pos, entity);
    }
}
