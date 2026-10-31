package net.minecraft.world.level.levelgen.feature;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.DeltaFeatureConfiguration;

public class DeltaFeature extends Feature<DeltaFeatureConfiguration> {
    private static final ImmutableList<Block> CANNOT_REPLACE = ImmutableList.of(
        Blocks.BEDROCK, Blocks.NETHER_BRICKS, Blocks.NETHER_BRICK_FENCE, Blocks.NETHER_BRICK_STAIRS, Blocks.NETHER_WART, Blocks.CHEST, Blocks.SPAWNER
    );
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final double RIM_SPAWN_CHANCE = 0.9;

    public DeltaFeature(Codec<DeltaFeatureConfiguration> codec) {
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
    public boolean place(FeaturePlaceContext<DeltaFeatureConfiguration> context) {
        boolean bl = false;
        RandomSource randomSource = context.random();
        WorldGenLevel worldGenLevel = context.level();
        DeltaFeatureConfiguration deltaFeatureConfiguration = context.config();
        BlockPos blockPos = context.origin();
        boolean bl2 = randomSource.nextDouble() < 0.9;
        int i = bl2 ? deltaFeatureConfiguration.rimSize().sample(randomSource) : 0;
        int j = bl2 ? deltaFeatureConfiguration.rimSize().sample(randomSource) : 0;
        boolean bl3 = bl2 && i != 0 && j != 0;
        int k = deltaFeatureConfiguration.size().sample(randomSource);
        int l = deltaFeatureConfiguration.size().sample(randomSource);
        int m = Math.max(k, l);

        for (BlockPos blockPos2 : BlockPos.withinManhattan(blockPos, k, 0, l)) {
            if (blockPos2.distManhattan(blockPos) > m) {
                break;
            }

            if (isClear(worldGenLevel, blockPos2, deltaFeatureConfiguration)) {
                if (bl3) {
                    bl = true;
                    this.setBlock(worldGenLevel, blockPos2, deltaFeatureConfiguration.rim());
                }

                BlockPos blockPos3 = blockPos2.offset(i, 0, j);
                if (isClear(worldGenLevel, blockPos3, deltaFeatureConfiguration)) {
                    bl = true;
                    this.setBlock(worldGenLevel, blockPos3, deltaFeatureConfiguration.contents());
                }
            }
        }

        return bl;
    }

    private static boolean isClear(LevelAccessor level, BlockPos pos, DeltaFeatureConfiguration config) {
        BlockState blockState = level.getBlockState(pos);
        if (blockState.is(config.contents().getBlock())) {
            return false;
        }

        if (CANNOT_REPLACE.contains(blockState.getBlock())) {
            return false;
        }

        for (Direction direction : DIRECTIONS) {
            boolean bl = level.getBlockState(pos.relative(direction)).isAir();
            if (bl && direction != Direction.UP || !bl && direction == Direction.UP) {
                return false;
            }
        }

        return true;
    }
}
