package net.salutaryfish.versionunbound.neoforge.patches;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.TriState;
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
     * Determines if this block either force allow or force disallow a plant from being placed on it. (Or pass and let the plant's decision win)
     * This will be called in plant's canSurvive method and/or mayPlace method.
     *
     * @param level        The current level
     * @param soilPosition The current position of the block that will sustain the plant
     * @param facing       The direction relative to the given position the plant wants to be, typically its UP
     * @param plant        The plant that is checking survivability
     * @return {@link TriState#TRUE} to allow the plant to be planted/stay. {@link TriState#FALSE} to disallow the plant from placing. {@link TriState#DEFAULT} to allow the plant to make the decision to stay or not.
     */
    default TriState canSustainPlant(BlockGetter level, BlockPos soilPosition, Direction facing, BlockState plant) {
        BlockState self = (BlockState) this;
        return self.getBlock().canSustainPlant(self, level, soilPosition, facing, plant);
    }

    /**
     * Checks if this soil is fertile, typically this means that growth rates
     * of plants on this soil will be slightly sped up.
     * Only vanilla case is tilledField when it is within range of water.
     *
     * @param level The current level
     * @param pos   Block position in level
     * @return True if the soil should be considered fertile.
     */
    default boolean isFertile(BlockGetter level, BlockPos pos) {
        BlockState self = (BlockState) this;
        return self.getBlock().isFertile(self, level, pos);
    }

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
