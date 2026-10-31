package net.minecraft.world.level.block;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

public interface ChangeOverTimeBlock<T extends Enum<T>> {
    int SCAN_DISTANCE = 4;

    Optional<BlockState> getNext(BlockState var1);

    float getChanceModifier();

    default void changeOverTime(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        float f = 0.05688889F;
        if (random.nextFloat() < 0.05688889F) {
            this.getNextState(state, level, pos, random).ifPresent(blockState -> level.setBlockAndUpdate(pos, blockState));
        }
    }

    T getAge();

    default Optional<BlockState> getNextState(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int i = this.getAge().ordinal();
        int j = 0;
        int k = 0;

        for (BlockPos blockPos : BlockPos.withinManhattan(pos, 4, 4, 4)) {
            int l = blockPos.distManhattan(pos);
            if (l > 4) {
                break;
            }

            if (!blockPos.equals(pos) && level.getBlockState(blockPos).getBlock() instanceof ChangeOverTimeBlock<?> changeOverTimeBlock) {
                Enum<?> enum_ = changeOverTimeBlock.getAge();
                if (this.getAge().getClass() == enum_.getClass()) {
                    int m = enum_.ordinal();
                    if (m < i) {
                        return Optional.empty();
                    }

                    if (m > i) {
                        k++;
                    } else {
                        j++;
                    }
                }
            }
        }

        float f = (float)(k + 1) / (k + j + 1);
        float g = f * f * this.getChanceModifier();
        return random.nextFloat() < g ? this.getNext(state) : Optional.empty();
    }
}
