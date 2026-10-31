package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;

public class ScatteredOreFeature extends Feature<OreConfiguration> {
    private static final int MAX_DIST_FROM_ORIGIN = 7;

    ScatteredOreFeature(Codec<OreConfiguration> codec) {
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
    public boolean place(FeaturePlaceContext<OreConfiguration> context) {
        WorldGenLevel worldGenLevel = context.level();
        RandomSource randomSource = context.random();
        OreConfiguration oreConfiguration = context.config();
        BlockPos blockPos = context.origin();
        int i = randomSource.nextInt(oreConfiguration.size + 1);
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

        for (int j = 0; j < i; j++) {
            this.offsetTargetPos(mutableBlockPos, randomSource, blockPos, Math.min(j, 7));
            BlockState blockState = worldGenLevel.getBlockState(mutableBlockPos);

            for (OreConfiguration.TargetBlockState targetBlockState : oreConfiguration.targetStates) {
                if (OreFeature.canPlaceOre(blockState, worldGenLevel::getBlockState, randomSource, oreConfiguration, targetBlockState, mutableBlockPos)) {
                    worldGenLevel.setBlock(mutableBlockPos, targetBlockState.state, 2);
                    break;
                }
            }
        }

        return true;
    }

    private void offsetTargetPos(BlockPos.MutableBlockPos mutablePos, RandomSource random, BlockPos pos, int magnitude) {
        int i = this.getRandomPlacementInOneAxisRelativeToOrigin(random, magnitude);
        int j = this.getRandomPlacementInOneAxisRelativeToOrigin(random, magnitude);
        int k = this.getRandomPlacementInOneAxisRelativeToOrigin(random, magnitude);
        mutablePos.setWithOffset(pos, i, j, k);
    }

    private int getRandomPlacementInOneAxisRelativeToOrigin(RandomSource random, int magnitude) {
        return Math.round((random.nextFloat() - random.nextFloat()) * magnitude);
    }
}
