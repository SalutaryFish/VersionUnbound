package net.minecraft;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntStack;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class BlockUtil {
    /**
     * Finds the rectangle with the largest area containing centerPos within the blocks specified by the predicate
     */
    public static BlockUtil.FoundRectangle getLargestRectangleAround(
        BlockPos centerPos, Direction.Axis axis1, int max1, Direction.Axis axis2, int max2, Predicate<BlockPos> posPredicate
    ) {
        BlockPos.MutableBlockPos mutableBlockPos = centerPos.mutable();
        Direction direction = Direction.get(Direction.AxisDirection.NEGATIVE, axis1);
        Direction direction2 = direction.getOpposite();
        Direction direction3 = Direction.get(Direction.AxisDirection.NEGATIVE, axis2);
        Direction direction4 = direction3.getOpposite();
        int i = getLimit(posPredicate, mutableBlockPos.set(centerPos), direction, max1);
        int j = getLimit(posPredicate, mutableBlockPos.set(centerPos), direction2, max1);
        int k = i;
        BlockUtil.IntBounds[] intBoundss = new BlockUtil.IntBounds[k + 1 + j];
        intBoundss[k] = new BlockUtil.IntBounds(
            getLimit(posPredicate, mutableBlockPos.set(centerPos), direction3, max2), getLimit(posPredicate, mutableBlockPos.set(centerPos), direction4, max2)
        );
        int l = intBoundss[k].min;

        for (int m = 1; m <= i; m++) {
            BlockUtil.IntBounds intBounds = intBoundss[k - (m - 1)];
            intBoundss[k - m] = new BlockUtil.IntBounds(
                getLimit(posPredicate, mutableBlockPos.set(centerPos).move(direction, m), direction3, intBounds.min),
                getLimit(posPredicate, mutableBlockPos.set(centerPos).move(direction, m), direction4, intBounds.max)
            );
        }

        for (int m = 1; m <= j; m++) {
            BlockUtil.IntBounds intBounds = intBoundss[k + m - 1];
            intBoundss[k + m] = new BlockUtil.IntBounds(
                getLimit(posPredicate, mutableBlockPos.set(centerPos).move(direction2, m), direction3, intBounds.min),
                getLimit(posPredicate, mutableBlockPos.set(centerPos).move(direction2, m), direction4, intBounds.max)
            );
        }

        int m = 0;
        int n = 0;
        int o = 0;
        int p = 0;
        int[] is = new int[intBoundss.length];

        for (int q = l; q >= 0; q--) {
            for (int r = 0; r < intBoundss.length; r++) {
                BlockUtil.IntBounds intBounds2 = intBoundss[r];
                int s = l - intBounds2.min;
                int t = l + intBounds2.max;
                is[r] = q >= s && q <= t ? t + 1 - q : 0;
            }

            Pair<BlockUtil.IntBounds, Integer> pair = getMaxRectangleLocation(is);
            BlockUtil.IntBounds intBounds2 = pair.getFirst();
            int s = 1 + intBounds2.max - intBounds2.min;
            int t = pair.getSecond();
            if (s * t > o * p) {
                m = intBounds2.min;
                n = q;
                o = s;
                p = t;
            }
        }

        return new BlockUtil.FoundRectangle(centerPos.relative(axis1, m - k).relative(axis2, n - l), o, p);
    }

    /**
     * Finds the distance we can travel in the given direction while the predicate returns true
     */
    private static int getLimit(Predicate<BlockPos> posPredicate, BlockPos.MutableBlockPos centerPos, Direction direction, int max) {
        int i = 0;

        while (i < max && posPredicate.test(centerPos.move(direction))) {
            i++;
        }

        return i;
    }

    /**
     * Finds the largest rectangle within the array of heights
     */
    @VisibleForTesting
    static Pair<BlockUtil.IntBounds, Integer> getMaxRectangleLocation(int[] heights) {
        int i = 0;
        int j = 0;
        int k = 0;
        IntStack intStack = new IntArrayList();
        intStack.push(0);

        for (int l = 1; l <= heights.length; l++) {
            int m = l == heights.length ? 0 : heights[l];

            while (!intStack.isEmpty()) {
                int n = heights[intStack.topInt()];
                if (m >= n) {
                    intStack.push(l);
                    break;
                }

                intStack.popInt();
                int o = intStack.isEmpty() ? 0 : intStack.topInt() + 1;
                if (n * (l - o) > k * (j - i)) {
                    j = l;
                    i = o;
                    k = n;
                }
            }

            if (intStack.isEmpty()) {
                intStack.push(l);
            }
        }

        return new Pair<>(new BlockUtil.IntBounds(i, j - 1), k);
    }

    public static Optional<BlockPos> getTopConnectedBlock(BlockGetter getter, BlockPos pos, Block baseBlock, Direction direction, Block endBlock) {
        BlockPos.MutableBlockPos mutableBlockPos = pos.mutable();

        BlockState blockState;
        do {
            mutableBlockPos.move(direction);
            blockState = getter.getBlockState(mutableBlockPos);
        } while (blockState.is(baseBlock));

        return blockState.is(endBlock) ? Optional.of(mutableBlockPos) : Optional.empty();
    }

    public static class FoundRectangle {
        /**
         * Starting position of the rectangle represented by this result
         */
        public final BlockPos minCorner;
        /**
         * Distance between minimum and maximum values on the first axis argument
         */
        public final int axis1Size;
        /**
         * Distance between minimum and maximum values on the second axis argument
         */
        public final int axis2Size;

        public FoundRectangle(BlockPos minCorner, int axis1Size, int axis2Size) {
            this.minCorner = minCorner;
            this.axis1Size = axis1Size;
            this.axis2Size = axis2Size;
        }
    }

    public static class IntBounds {
        /**
         * The minimum bound
         */
        public final int min;
        /**
         * The maximum bound
         */
        public final int max;

        public IntBounds(int min, int max) {
            this.min = min;
            this.max = max;
        }

        @Override
        public String toString() {
            return "IntBounds{min=" + this.min + ", max=" + this.max + "}";
        }
    }
}
