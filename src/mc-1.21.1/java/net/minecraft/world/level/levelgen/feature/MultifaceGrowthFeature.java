package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.MultifaceGrowthConfiguration;

public class MultifaceGrowthFeature extends Feature<MultifaceGrowthConfiguration> {
    public MultifaceGrowthFeature(Codec<MultifaceGrowthConfiguration> codec) {
        super(codec);
    }

    /**
     * Places the given feature at the given location.
     * During world generation, features are provided with a 3x3 region of chunks, centered on the chunk being generated, that they can safely generate into.
     *
     * @param context A context object with a reference to the level and the position
     *                the feature is being placed at
     */
    @Override
    public boolean place(FeaturePlaceContext<MultifaceGrowthConfiguration> context) {
        WorldGenLevel worldGenLevel = context.level();
        BlockPos blockPos = context.origin();
        RandomSource randomSource = context.random();
        MultifaceGrowthConfiguration multifaceGrowthConfiguration = context.config();
        if (!isAirOrWater(worldGenLevel.getBlockState(blockPos))) {
            return false;
        }

        List<Direction> list = multifaceGrowthConfiguration.getShuffledDirections(randomSource);
        if (placeGrowthIfPossible(worldGenLevel, blockPos, worldGenLevel.getBlockState(blockPos), multifaceGrowthConfiguration, randomSource, list)) {
            return true;
        }

        BlockPos.MutableBlockPos mutableBlockPos = blockPos.mutable();

        for (Direction direction : list) {
            mutableBlockPos.set(blockPos);
            List<Direction> list2 = multifaceGrowthConfiguration.getShuffledDirectionsExcept(randomSource, direction.getOpposite());

            for (int i = 0; i < multifaceGrowthConfiguration.searchRange; i++) {
                mutableBlockPos.setWithOffset(blockPos, direction);
                BlockState blockState = worldGenLevel.getBlockState(mutableBlockPos);
                if (!isAirOrWater(blockState) && !blockState.is(multifaceGrowthConfiguration.placeBlock)) {
                    break;
                }

                if (placeGrowthIfPossible(worldGenLevel, mutableBlockPos, blockState, multifaceGrowthConfiguration, randomSource, list2)) {
                    return true;
                }
            }
        }

        return false;
    }

    public static boolean placeGrowthIfPossible(
        WorldGenLevel level, BlockPos pos, BlockState state, MultifaceGrowthConfiguration config, RandomSource random, List<Direction> directions
    ) {
        BlockPos.MutableBlockPos mutableBlockPos = pos.mutable();

        for (Direction direction : directions) {
            BlockState blockState = level.getBlockState(mutableBlockPos.setWithOffset(pos, direction));
            if (blockState.is(config.canBePlacedOn)) {
                BlockState blockState2 = config.placeBlock.getStateForPlacement(state, level, pos, direction);
                if (blockState2 == null) {
                    return false;
                }

                level.setBlock(pos, blockState2, 3);
                level.getChunk(pos).markPosForPostprocessing(pos);
                if (random.nextFloat() < config.chanceOfSpreading) {
                    config.placeBlock.getSpreader().spreadFromFaceTowardRandomDirection(blockState2, level, pos, direction, random, true);
                }

                return true;
            }
        }

        return false;
    }

    private static boolean isAirOrWater(BlockState state) {
        return state.isAir() || state.is(Blocks.WATER);
    }
}
