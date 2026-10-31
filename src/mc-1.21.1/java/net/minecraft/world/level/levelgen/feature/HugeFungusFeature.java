package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;

public class HugeFungusFeature extends Feature<HugeFungusConfiguration> {
    private static final float HUGE_PROBABILITY = 0.06F;

    public HugeFungusFeature(Codec<HugeFungusConfiguration> codec) {
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
    public boolean place(FeaturePlaceContext<HugeFungusConfiguration> context) {
        WorldGenLevel worldGenLevel = context.level();
        BlockPos blockPos = context.origin();
        RandomSource randomSource = context.random();
        ChunkGenerator chunkGenerator = context.chunkGenerator();
        HugeFungusConfiguration hugeFungusConfiguration = context.config();
        Block block = hugeFungusConfiguration.validBaseState.getBlock();
        BlockPos blockPos2 = null;
        BlockState blockState = worldGenLevel.getBlockState(blockPos.below());
        if (blockState.is(block)) {
            blockPos2 = blockPos;
        }

        if (blockPos2 == null) {
            return false;
        }

        int i = Mth.nextInt(randomSource, 4, 13);
        if (randomSource.nextInt(12) == 0) {
            i *= 2;
        }

        if (!hugeFungusConfiguration.planted) {
            int j = chunkGenerator.getGenDepth();
            if (blockPos2.getY() + i + 1 >= j) {
                return false;
            }
        }

        boolean bl = !hugeFungusConfiguration.planted && randomSource.nextFloat() < 0.06F;
        worldGenLevel.setBlock(blockPos, Blocks.AIR.defaultBlockState(), 4);
        this.placeStem(worldGenLevel, randomSource, hugeFungusConfiguration, blockPos2, i, bl);
        this.placeHat(worldGenLevel, randomSource, hugeFungusConfiguration, blockPos2, i, bl);
        return true;
    }

    private static boolean isReplaceable(WorldGenLevel level, BlockPos pos, HugeFungusConfiguration config, boolean checkConfig) {
        if (level.isStateAtPosition(pos, BlockBehaviour.BlockStateBase::canBeReplaced)) {
            return true;
        } else {
            return checkConfig ? config.replaceableBlocks.test(level, pos) : false;
        }
    }

    private void placeStem(WorldGenLevel level, RandomSource random, HugeFungusConfiguration config, BlockPos pos, int height, boolean huge) {
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
        BlockState blockState = config.stemState;
        int i = huge ? 1 : 0;

        for (int j = -i; j <= i; j++) {
            for (int k = -i; k <= i; k++) {
                boolean bl = huge && Mth.abs(j) == i && Mth.abs(k) == i;

                for (int l = 0; l < height; l++) {
                    mutableBlockPos.setWithOffset(pos, j, l, k);
                    if (isReplaceable(level, mutableBlockPos, config, true)) {
                        if (config.planted) {
                            if (!level.getBlockState(mutableBlockPos.below()).isAir()) {
                                level.destroyBlock(mutableBlockPos, true);
                            }

                            level.setBlock(mutableBlockPos, blockState, 3);
                        } else if (bl) {
                            if (random.nextFloat() < 0.1F) {
                                this.setBlock(level, mutableBlockPos, blockState);
                            }
                        } else {
                            this.setBlock(level, mutableBlockPos, blockState);
                        }
                    }
                }
            }
        }
    }

    private void placeHat(WorldGenLevel level, RandomSource random, HugeFungusConfiguration config, BlockPos pos, int height, boolean huge) {
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
        boolean bl = config.hatState.is(Blocks.NETHER_WART_BLOCK);
        int i = Math.min(random.nextInt(1 + height / 3) + 5, height);
        int j = height - i;

        for (int k = j; k <= height; k++) {
            int l = k < height - random.nextInt(3) ? 2 : 1;
            if (i > 8 && k < j + 4) {
                l = 3;
            }

            if (huge) {
                l++;
            }

            for (int m = -l; m <= l; m++) {
                for (int n = -l; n <= l; n++) {
                    boolean bl2 = m == -l || m == l;
                    boolean bl3 = n == -l || n == l;
                    boolean bl4 = !bl2 && !bl3 && k != height;
                    boolean bl5 = bl2 && bl3;
                    boolean bl6 = k < j + 3;
                    mutableBlockPos.setWithOffset(pos, m, k, n);
                    if (isReplaceable(level, mutableBlockPos, config, false)) {
                        if (config.planted && !level.getBlockState(mutableBlockPos.below()).isAir()) {
                            level.destroyBlock(mutableBlockPos, true);
                        }

                        if (bl6) {
                            if (!bl4) {
                                this.placeHatDropBlock(level, random, mutableBlockPos, config.hatState, bl);
                            }
                        } else if (bl4) {
                            this.placeHatBlock(level, random, config, mutableBlockPos, 0.1F, 0.2F, bl ? 0.1F : 0.0F);
                        } else if (bl5) {
                            this.placeHatBlock(level, random, config, mutableBlockPos, 0.01F, 0.7F, bl ? 0.083F : 0.0F);
                        } else {
                            this.placeHatBlock(level, random, config, mutableBlockPos, 5.0E-4F, 0.98F, bl ? 0.07F : 0.0F);
                        }
                    }
                }
            }
        }
    }

    private void placeHatBlock(
        LevelAccessor level,
        RandomSource random,
        HugeFungusConfiguration config,
        BlockPos.MutableBlockPos pos,
        float decorationChance,
        float hatChance,
        float weepingVineChance
    ) {
        if (random.nextFloat() < decorationChance) {
            this.setBlock(level, pos, config.decorState);
        } else if (random.nextFloat() < hatChance) {
            this.setBlock(level, pos, config.hatState);
            if (random.nextFloat() < weepingVineChance) {
                tryPlaceWeepingVines(pos, level, random);
            }
        }
    }

    private void placeHatDropBlock(LevelAccessor level, RandomSource random, BlockPos pos, BlockState state, boolean weepingVines) {
        if (level.getBlockState(pos.below()).is(state.getBlock())) {
            this.setBlock(level, pos, state);
        } else if (random.nextFloat() < 0.15) {
            this.setBlock(level, pos, state);
            if (weepingVines && random.nextInt(11) == 0) {
                tryPlaceWeepingVines(pos, level, random);
            }
        }
    }

    private static void tryPlaceWeepingVines(BlockPos pos, LevelAccessor level, RandomSource random) {
        BlockPos.MutableBlockPos mutableBlockPos = pos.mutable().move(Direction.DOWN);
        if (level.isEmptyBlock(mutableBlockPos)) {
            int i = Mth.nextInt(random, 1, 5);
            if (random.nextInt(7) == 0) {
                i *= 2;
            }

            int j = 23;
            int k = 25;
            WeepingVinesFeature.placeWeepingVinesColumn(level, random, mutableBlockPos, i, 23, 25);
        }
    }
}
