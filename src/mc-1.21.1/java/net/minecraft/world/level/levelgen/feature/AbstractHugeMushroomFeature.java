package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.HugeMushroomFeatureConfiguration;

public abstract class AbstractHugeMushroomFeature extends Feature<HugeMushroomFeatureConfiguration> {
    public AbstractHugeMushroomFeature(Codec<HugeMushroomFeatureConfiguration> codec) {
        super(codec);
    }

    protected void placeTrunk(
        LevelAccessor level, RandomSource random, BlockPos pos, HugeMushroomFeatureConfiguration config, int maxHeight, BlockPos.MutableBlockPos mutablePos
    ) {
        for (int i = 0; i < maxHeight; i++) {
            mutablePos.set(pos).move(Direction.UP, i);
            if (!level.getBlockState(mutablePos).isSolidRender(level, mutablePos)) {
                this.setBlock(level, mutablePos, config.stemProvider.getState(random, pos));
            }
        }
    }

    protected int getTreeHeight(RandomSource random) {
        int i = random.nextInt(3) + 4;
        if (random.nextInt(12) == 0) {
            i *= 2;
        }

        return i;
    }

    protected boolean isValidPosition(
        LevelAccessor level, BlockPos pos, int maxHeight, BlockPos.MutableBlockPos mutablePos, HugeMushroomFeatureConfiguration config
    ) {
        int i = pos.getY();
        if (i >= level.getMinBuildHeight() + 1 && i + maxHeight + 1 < level.getMaxBuildHeight()) {
            BlockState blockState = level.getBlockState(pos.below());
            if (!isDirt(blockState) && !blockState.is(BlockTags.MUSHROOM_GROW_BLOCK)) {
                return false;
            }

            for (int j = 0; j <= maxHeight; j++) {
                int k = this.getTreeRadiusForHeight(-1, -1, config.foliageRadius, j);

                for (int l = -k; l <= k; l++) {
                    for (int m = -k; m <= k; m++) {
                        BlockState blockState2 = level.getBlockState(mutablePos.setWithOffset(pos, l, j, m));
                        if (!blockState2.isAir() && !blockState2.is(BlockTags.LEAVES)) {
                            return false;
                        }
                    }
                }
            }

            return true;
        } else {
            return false;
        }
    }

    /**
     * Places the given feature at the given location.
     * During world generation, features are provided with a 3x3 region of chunks, centered on the chunk being generated, that they can safely generate into.
     *
     * @param context A context object with a reference to the level and the position
     *                the feature is being placed at
     */
    @Override
    public boolean place(FeaturePlaceContext<HugeMushroomFeatureConfiguration> context) {
        WorldGenLevel worldGenLevel = context.level();
        BlockPos blockPos = context.origin();
        RandomSource randomSource = context.random();
        HugeMushroomFeatureConfiguration hugeMushroomFeatureConfiguration = context.config();
        int i = this.getTreeHeight(randomSource);
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
        if (!this.isValidPosition(worldGenLevel, blockPos, i, mutableBlockPos, hugeMushroomFeatureConfiguration)) {
            return false;
        }

        this.makeCap(worldGenLevel, randomSource, blockPos, i, mutableBlockPos, hugeMushroomFeatureConfiguration);
        this.placeTrunk(worldGenLevel, randomSource, blockPos, hugeMushroomFeatureConfiguration, i, mutableBlockPos);
        return true;
    }

    protected abstract int getTreeRadiusForHeight(int var1, int var2, int var3, int var4);

    protected abstract void makeCap(
        LevelAccessor var1, RandomSource var2, BlockPos var3, int var4, BlockPos.MutableBlockPos var5, HugeMushroomFeatureConfiguration var6
    );
}
