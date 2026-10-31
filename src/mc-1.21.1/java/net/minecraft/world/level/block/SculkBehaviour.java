package net.minecraft.world.level.block;

import java.util.Collection;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

public interface SculkBehaviour {
    SculkBehaviour DEFAULT = new SculkBehaviour() {
        @Override
        public boolean attemptSpreadVein(
            LevelAccessor levelAccessor, BlockPos blockPos, BlockState blockState, @Nullable Collection<Direction> collection, boolean bl
        ) {
            if (collection == null) {
                return ((SculkVeinBlock)Blocks.SCULK_VEIN).getSameSpaceSpreader().spreadAll(levelAccessor.getBlockState(blockPos), levelAccessor, blockPos, bl)
                    > 0L;
            } else if (!collection.isEmpty()) {
                return !blockState.isAir() && !blockState.getFluidState().is(Fluids.WATER)
                    ? false
                    : SculkVeinBlock.regrow(levelAccessor, blockPos, blockState, collection);
            } else {
                return SculkBehaviour.super.attemptSpreadVein(levelAccessor, blockPos, blockState, collection, bl);
            }
        }

        @Override
        public int attemptUseCharge(
            SculkSpreader.ChargeCursor chargeCursor,
            LevelAccessor levelAccessor,
            BlockPos blockPos,
            RandomSource randomSource,
            SculkSpreader sculkSpreader,
            boolean bl
        ) {
            return chargeCursor.getDecayDelay() > 0 ? chargeCursor.getCharge() : 0;
        }

        @Override
        public int updateDecayDelay(int i) {
            return Math.max(i - 1, 0);
        }
    };

    default byte getSculkSpreadDelay() {
        return 1;
    }

    default void onDischarged(LevelAccessor level, BlockState state, BlockPos pos, RandomSource random) {
    }

    default boolean depositCharge(LevelAccessor level, BlockPos pos, RandomSource random) {
        return false;
    }

    default boolean attemptSpreadVein(
        LevelAccessor level, BlockPos pos, BlockState state, @Nullable Collection<Direction> directions, boolean markForPostprocessing
    ) {
        return ((MultifaceBlock)Blocks.SCULK_VEIN).getSpreader().spreadAll(state, level, pos, markForPostprocessing) > 0L;
    }

    default boolean canChangeBlockStateOnSpread() {
        return true;
    }

    default int updateDecayDelay(int currentDecayDelay) {
        return 1;
    }

    int attemptUseCharge(SculkSpreader.ChargeCursor var1, LevelAccessor var2, BlockPos var3, RandomSource var4, SculkSpreader var5, boolean var6);
}
