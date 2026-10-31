package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.ClampedNormalFloat;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Column;
import net.minecraft.world.level.levelgen.feature.configurations.DripstoneClusterConfiguration;

public class DripstoneClusterFeature extends Feature<DripstoneClusterConfiguration> {
    public DripstoneClusterFeature(Codec<DripstoneClusterConfiguration> codec) {
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
    public boolean place(FeaturePlaceContext<DripstoneClusterConfiguration> context) {
        WorldGenLevel worldGenLevel = context.level();
        BlockPos blockPos = context.origin();
        DripstoneClusterConfiguration dripstoneClusterConfiguration = context.config();
        RandomSource randomSource = context.random();
        if (!DripstoneUtils.isEmptyOrWater(worldGenLevel, blockPos)) {
            return false;
        }

        int i = dripstoneClusterConfiguration.height.sample(randomSource);
        float f = dripstoneClusterConfiguration.wetness.sample(randomSource);
        float g = dripstoneClusterConfiguration.density.sample(randomSource);
        int j = dripstoneClusterConfiguration.radius.sample(randomSource);
        int k = dripstoneClusterConfiguration.radius.sample(randomSource);

        for (int l = -j; l <= j; l++) {
            for (int m = -k; m <= k; m++) {
                double d = this.getChanceOfStalagmiteOrStalactite(j, k, l, m, dripstoneClusterConfiguration);
                BlockPos blockPos2 = blockPos.offset(l, 0, m);
                this.placeColumn(worldGenLevel, randomSource, blockPos2, l, m, f, d, i, g, dripstoneClusterConfiguration);
            }
        }

        return true;
    }

    private void placeColumn(
        WorldGenLevel level,
        RandomSource random,
        BlockPos pos,
        int x,
        int z,
        float wetness,
        double chance,
        int height,
        float density,
        DripstoneClusterConfiguration config
    ) {
        Optional<Column> optional = Column.scan(
            level, pos, config.floorToCeilingSearchRange, DripstoneUtils::isEmptyOrWater, DripstoneUtils::isNeitherEmptyNorWater
        );
        if (!optional.isEmpty()) {
            OptionalInt optionalInt = optional.get().getCeiling();
            OptionalInt optionalInt2 = optional.get().getFloor();
            if (!optionalInt.isEmpty() || !optionalInt2.isEmpty()) {
                boolean bl = random.nextFloat() < wetness;
                Column column;
                if (bl && optionalInt2.isPresent() && this.canPlacePool(level, pos.atY(optionalInt2.getAsInt()))) {
                    int i = optionalInt2.getAsInt();
                    column = optional.get().withFloor(OptionalInt.of(i - 1));
                    level.setBlock(pos.atY(i), Blocks.WATER.defaultBlockState(), 2);
                } else {
                    column = optional.get();
                }

                OptionalInt optionalInt3 = column.getFloor();
                boolean bl2 = random.nextDouble() < chance;
                int l;
                if (optionalInt.isPresent() && bl2 && !this.isLava(level, pos.atY(optionalInt.getAsInt()))) {
                    int j = config.dripstoneBlockLayerThickness.sample(random);
                    this.replaceBlocksWithDripstoneBlocks(level, pos.atY(optionalInt.getAsInt()), j, Direction.UP);
                    int k;
                    if (optionalInt3.isPresent()) {
                        k = Math.min(height, optionalInt.getAsInt() - optionalInt3.getAsInt());
                    } else {
                        k = height;
                    }

                    l = this.getDripstoneHeight(random, x, z, density, k, config);
                } else {
                    l = 0;
                }

                boolean bl3 = random.nextDouble() < chance;
                int j;
                if (optionalInt3.isPresent() && bl3 && !this.isLava(level, pos.atY(optionalInt3.getAsInt()))) {
                    int m = config.dripstoneBlockLayerThickness.sample(random);
                    this.replaceBlocksWithDripstoneBlocks(level, pos.atY(optionalInt3.getAsInt()), m, Direction.DOWN);
                    if (optionalInt.isPresent()) {
                        j = Math.max(
                            0, l + Mth.randomBetweenInclusive(random, -config.maxStalagmiteStalactiteHeightDiff, config.maxStalagmiteStalactiteHeightDiff)
                        );
                    } else {
                        j = this.getDripstoneHeight(random, x, z, density, height, config);
                    }
                } else {
                    j = 0;
                }

                int t;
                int m;
                if (optionalInt.isPresent() && optionalInt3.isPresent() && optionalInt.getAsInt() - l <= optionalInt3.getAsInt() + j) {
                    int n = optionalInt3.getAsInt();
                    int o = optionalInt.getAsInt();
                    int p = Math.max(o - l, n + 1);
                    int q = Math.min(n + j, o - 1);
                    int r = Mth.randomBetweenInclusive(random, p, q + 1);
                    int s = r - 1;
                    m = o - r;
                    t = s - n;
                } else {
                    m = l;
                    t = j;
                }

                boolean bl4 = random.nextBoolean() && m > 0 && t > 0 && column.getHeight().isPresent() && m + t == column.getHeight().getAsInt();
                if (optionalInt.isPresent()) {
                    DripstoneUtils.growPointedDripstone(level, pos.atY(optionalInt.getAsInt() - 1), Direction.DOWN, m, bl4);
                }

                if (optionalInt3.isPresent()) {
                    DripstoneUtils.growPointedDripstone(level, pos.atY(optionalInt3.getAsInt() + 1), Direction.UP, t, bl4);
                }
            }
        }
    }

    private boolean isLava(LevelReader level, BlockPos pos) {
        return level.getBlockState(pos).is(Blocks.LAVA);
    }

    private int getDripstoneHeight(RandomSource random, int x, int z, float chance, int height, DripstoneClusterConfiguration config) {
        if (random.nextFloat() > chance) {
            return 0;
        }

        int i = Math.abs(x) + Math.abs(z);
        float f = (float)Mth.clampedMap(i, 0.0, config.maxDistanceFromCenterAffectingHeightBias, height / 2.0, 0.0);
        return (int)randomBetweenBiased(random, 0.0F, height, f, config.heightDeviation);
    }

    private boolean canPlacePool(WorldGenLevel level, BlockPos pos) {
        BlockState blockState = level.getBlockState(pos);
        if (!blockState.is(Blocks.WATER) && !blockState.is(Blocks.DRIPSTONE_BLOCK) && !blockState.is(Blocks.POINTED_DRIPSTONE)) {
            if (level.getBlockState(pos.above()).getFluidState().is(FluidTags.WATER)) {
                return false;
            }

            for (Direction direction : Direction.Plane.HORIZONTAL) {
                if (!this.canBeAdjacentToWater(level, pos.relative(direction))) {
                    return false;
                }
            }

            return this.canBeAdjacentToWater(level, pos.below());
        } else {
            return false;
        }
    }

    private boolean canBeAdjacentToWater(LevelAccessor level, BlockPos pos) {
        BlockState blockState = level.getBlockState(pos);
        return blockState.is(BlockTags.BASE_STONE_OVERWORLD) || blockState.getFluidState().is(FluidTags.WATER);
    }

    private void replaceBlocksWithDripstoneBlocks(WorldGenLevel level, BlockPos pos, int thickness, Direction direction) {
        BlockPos.MutableBlockPos mutableBlockPos = pos.mutable();

        for (int i = 0; i < thickness; i++) {
            if (!DripstoneUtils.placeDripstoneBlockIfPossible(level, mutableBlockPos)) {
                return;
            }

            mutableBlockPos.move(direction);
        }
    }

    private double getChanceOfStalagmiteOrStalactite(int xRadius, int zRadius, int x, int z, DripstoneClusterConfiguration config) {
        int i = xRadius - Math.abs(x);
        int j = zRadius - Math.abs(z);
        int k = Math.min(i, j);
        return Mth.clampedMap(k, 0.0F, config.maxDistanceFromEdgeAffectingChanceOfDripstoneColumn, config.chanceOfDripstoneColumnAtMaxDistanceFromCenter, 1.0F);
    }

    private static float randomBetweenBiased(RandomSource random, float min, float max, float mean, float deviation) {
        return ClampedNormalFloat.sample(random, mean, deviation, min, max);
    }
}
