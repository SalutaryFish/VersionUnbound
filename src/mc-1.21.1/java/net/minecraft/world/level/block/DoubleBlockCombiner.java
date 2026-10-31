package net.minecraft.world.level.block;

import java.util.function.BiPredicate;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

public class DoubleBlockCombiner {
    public static <S extends BlockEntity> DoubleBlockCombiner.NeighborCombineResult<S> combineWithNeigbour(
        BlockEntityType<S> blockEntityType,
        Function<BlockState, DoubleBlockCombiner.BlockType> doubleBlockTypeGetter,
        Function<BlockState, Direction> directionGetter,
        DirectionProperty directionProperty,
        BlockState state,
        LevelAccessor level,
        BlockPos pos,
        BiPredicate<LevelAccessor, BlockPos> blockedChestTest
    ) {
        S blockEntity = blockEntityType.getBlockEntity(level, pos);
        if (blockEntity == null) {
            return DoubleBlockCombiner.Combiner::acceptNone;
        }

        if (blockedChestTest.test(level, pos)) {
            return DoubleBlockCombiner.Combiner::acceptNone;
        }

        DoubleBlockCombiner.BlockType blockType = doubleBlockTypeGetter.apply(state);
        boolean bl = blockType == DoubleBlockCombiner.BlockType.SINGLE;
        boolean bl2 = blockType == DoubleBlockCombiner.BlockType.FIRST;
        if (bl) {
            return new DoubleBlockCombiner.NeighborCombineResult.Single<>(blockEntity);
        }

        BlockPos blockPos = pos.relative(directionGetter.apply(state));
        BlockState blockState = level.getBlockState(blockPos);
        if (blockState.is(state.getBlock())) {
            DoubleBlockCombiner.BlockType blockType2 = doubleBlockTypeGetter.apply(blockState);
            if (blockType2 != DoubleBlockCombiner.BlockType.SINGLE
                && blockType != blockType2
                && blockState.getValue(directionProperty) == state.getValue(directionProperty)) {
                if (blockedChestTest.test(level, blockPos)) {
                    return DoubleBlockCombiner.Combiner::acceptNone;
                }

                S blockEntity2 = blockEntityType.getBlockEntity(level, blockPos);
                if (blockEntity2 != null) {
                    S blockEntity3 = bl2 ? blockEntity : blockEntity2;
                    S blockEntity4 = bl2 ? blockEntity2 : blockEntity;
                    return new DoubleBlockCombiner.NeighborCombineResult.Double<>(blockEntity3, blockEntity4);
                }
            }
        }

        return new DoubleBlockCombiner.NeighborCombineResult.Single<>(blockEntity);
    }

    public enum BlockType {
        SINGLE,
        FIRST,
        SECOND;
    }

    public interface Combiner<S, T> {
        T acceptDouble(S var1, S var2);

        T acceptSingle(S var1);

        T acceptNone();
    }

    public interface NeighborCombineResult<S> {
        <T> T apply(DoubleBlockCombiner.Combiner<? super S, T> var1);

        final class Double<S> implements DoubleBlockCombiner.NeighborCombineResult<S> {
            private final S first;
            private final S second;

            public Double(S first, S second) {
                this.first = first;
                this.second = second;
            }

            @Override
            public <T> T apply(DoubleBlockCombiner.Combiner<? super S, T> combiner) {
                return combiner.acceptDouble(this.first, this.second);
            }
        }

        final class Single<S> implements DoubleBlockCombiner.NeighborCombineResult<S> {
            private final S single;

            public Single(S single) {
                this.single = single;
            }

            @Override
            public <T> T apply(DoubleBlockCombiner.Combiner<? super S, T> combiner) {
                return combiner.acceptSingle(this.single);
            }
        }
    }
}
