package net.salutaryfish.versionunbound.neoforge.mixin.patches;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * NeoForge's patch to {@code Level}, the block extension parts: {@code setBlock} tells the new state
 * {@code onBlockStateChange} after the point of interest update (LevelMixin's {@code markAndNotifyBlock} does the same),
 * and {@code updateNeighbourForOutputSignal} tells each neighbour {@code onNeighborChange} (comparators answer it,
 * ComparatorBlockMixin) and the block past a conductor its weak change when it wants it ({@code getWeakChanges}), in
 * all six directions (horizontal first; vertical comparators excluded). An {@code @Overwrite} with NeoForge's body.
 */
@Mixin(Level.class)
abstract class LevelNeighborChangesMixin {
    @Unique
    private static final Direction[] NEIGHBOR_UPDATE_LIST = Stream.concat(Direction.Plane.HORIZONTAL.stream(), Direction.Plane.VERTICAL.stream()).toArray(Direction[]::new);

    @WrapOperation(
            method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;updatePOIOnBlockStateChange(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;)V"))
    private void versionunbound$blockStateChange(Level level, BlockPos pos, BlockState oldState, BlockState newState, Operation<Void> original,
            @Local(argsOnly = true) BlockState blockState) {
        original.call(level, pos, oldState, newState);
        blockState.onBlockStateChange(level, pos, oldState);
    }

    /**
     * @author NeoForge (ported by VersionUnbound)
     * @reason NeoForge's patched body
     */
    @Overwrite
    public void updateNeighbourForOutputSignal(final BlockPos pos, final Block changedBlock) {
        Level level = (Level) (Object) this;
        // Neo: send update to vertical directions as well, after horizontal ones
        for (Direction direction : NEIGHBOR_UPDATE_LIST) {
            BlockPos relativePos = pos.relative(direction);
            if (level.hasChunkAt(relativePos)) {
                BlockState state = level.getBlockState(relativePos);
                state.onNeighborChange(level, relativePos, pos);
                if (state.isRedstoneConductor(level, relativePos)) {
                    relativePos = relativePos.relative(direction);
                    state = level.getBlockState(relativePos);
                    // Neo: send to any blockstate that wants weak changes, but exclude vanilla comparators from vertical updates
                    if (state.getWeakChanges(level, relativePos) && (direction.getAxis().isHorizontal() || !state.is(Blocks.COMPARATOR))) {
                        level.neighborChanged(state, relativePos, changedBlock, null, false);
                    }
                }
            }
        }
    }
}
