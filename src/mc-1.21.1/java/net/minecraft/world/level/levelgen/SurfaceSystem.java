package net.minecraft.world.level.levelgen;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.BlockColumn;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.carver.CarvingContext;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

public class SurfaceSystem {
    private static final BlockState WHITE_TERRACOTTA = Blocks.WHITE_TERRACOTTA.defaultBlockState();
    private static final BlockState ORANGE_TERRACOTTA = Blocks.ORANGE_TERRACOTTA.defaultBlockState();
    private static final BlockState TERRACOTTA = Blocks.TERRACOTTA.defaultBlockState();
    private static final BlockState YELLOW_TERRACOTTA = Blocks.YELLOW_TERRACOTTA.defaultBlockState();
    private static final BlockState BROWN_TERRACOTTA = Blocks.BROWN_TERRACOTTA.defaultBlockState();
    private static final BlockState RED_TERRACOTTA = Blocks.RED_TERRACOTTA.defaultBlockState();
    private static final BlockState LIGHT_GRAY_TERRACOTTA = Blocks.LIGHT_GRAY_TERRACOTTA.defaultBlockState();
    private static final BlockState PACKED_ICE = Blocks.PACKED_ICE.defaultBlockState();
    private static final BlockState SNOW_BLOCK = Blocks.SNOW_BLOCK.defaultBlockState();
    private final BlockState defaultBlock;
    private final int seaLevel;
    private final BlockState[] clayBands;
    private final NormalNoise clayBandsOffsetNoise;
    private final NormalNoise badlandsPillarNoise;
    private final NormalNoise badlandsPillarRoofNoise;
    private final NormalNoise badlandsSurfaceNoise;
    private final NormalNoise icebergPillarNoise;
    private final NormalNoise icebergPillarRoofNoise;
    private final NormalNoise icebergSurfaceNoise;
    private final PositionalRandomFactory noiseRandom;
    private final NormalNoise surfaceNoise;
    private final NormalNoise surfaceSecondaryNoise;

    public SurfaceSystem(RandomState randomState, BlockState defaultBlock, int seaLevel, PositionalRandomFactory noiseRandom) {
        this.defaultBlock = defaultBlock;
        this.seaLevel = seaLevel;
        this.noiseRandom = noiseRandom;
        this.clayBandsOffsetNoise = randomState.getOrCreateNoise(Noises.CLAY_BANDS_OFFSET);
        this.clayBands = generateBands(noiseRandom.fromHashOf(ResourceLocation.withDefaultNamespace("clay_bands")));
        this.surfaceNoise = randomState.getOrCreateNoise(Noises.SURFACE);
        this.surfaceSecondaryNoise = randomState.getOrCreateNoise(Noises.SURFACE_SECONDARY);
        this.badlandsPillarNoise = randomState.getOrCreateNoise(Noises.BADLANDS_PILLAR);
        this.badlandsPillarRoofNoise = randomState.getOrCreateNoise(Noises.BADLANDS_PILLAR_ROOF);
        this.badlandsSurfaceNoise = randomState.getOrCreateNoise(Noises.BADLANDS_SURFACE);
        this.icebergPillarNoise = randomState.getOrCreateNoise(Noises.ICEBERG_PILLAR);
        this.icebergPillarRoofNoise = randomState.getOrCreateNoise(Noises.ICEBERG_PILLAR_ROOF);
        this.icebergSurfaceNoise = randomState.getOrCreateNoise(Noises.ICEBERG_SURFACE);
    }

    public void buildSurface(
        RandomState randomState,
        BiomeManager biomeManager,
        Registry<Biome> biomes,
        boolean useLegacyRandomSource,
        WorldGenerationContext context,
        ChunkAccess chunk,
        NoiseChunk noiseChunk,
        SurfaceRules.RuleSource ruleSource
    ) {
        final BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
        final ChunkPos chunkPos = chunk.getPos();
        int i = chunkPos.getMinBlockX();
        int j = chunkPos.getMinBlockZ();
        BlockColumn blockColumn = new BlockColumn() {
            @Override
            public BlockState getBlock(int i) {
                return chunk.getBlockState(mutableBlockPos.setY(i));
            }

            @Override
            public void setBlock(int i, BlockState blockState) {
                LevelHeightAccessor levelHeightAccessor = chunk.getHeightAccessorForGeneration();
                if (i >= levelHeightAccessor.getMinBuildHeight() && i < levelHeightAccessor.getMaxBuildHeight()) {
                    chunk.setBlockState(mutableBlockPos.setY(i), blockState, false);
                    if (!blockState.getFluidState().isEmpty()) {
                        chunk.markPosForPostprocessing(mutableBlockPos);
                    }
                }
            }

            @Override
            public String toString() {
                return "ChunkBlockColumn " + chunkPos;
            }
        };
        SurfaceRules.Context context2 = new SurfaceRules.Context(this, randomState, chunk, noiseChunk, biomeManager::getBiome, biomes, context);
        SurfaceRules.SurfaceRule surfaceRule = ruleSource.apply(context2);
        BlockPos.MutableBlockPos mutableBlockPos2 = new BlockPos.MutableBlockPos();

        for (int k = 0; k < 16; k++) {
            for (int l = 0; l < 16; l++) {
                int m = i + k;
                int n = j + l;
                int o = chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, k, l) + 1;
                mutableBlockPos.setX(m).setZ(n);
                Holder<Biome> holder = biomeManager.getBiome(mutableBlockPos2.set(m, useLegacyRandomSource ? 0 : o, n));
                if (holder.is(Biomes.ERODED_BADLANDS)) {
                    this.erodedBadlandsExtension(blockColumn, m, n, o, chunk);
                }

                int p = chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, k, l) + 1;
                context2.updateXZ(m, n);
                int q = 0;
                int r = Integer.MIN_VALUE;
                int s = Integer.MAX_VALUE;
                int t = chunk.getMinBuildHeight();

                for (int u = p; u >= t; u--) {
                    BlockState blockState = blockColumn.getBlock(u);
                    if (blockState.isAir()) {
                        q = 0;
                        r = Integer.MIN_VALUE;
                    } else if (!blockState.getFluidState().isEmpty()) {
                        if (r == Integer.MIN_VALUE) {
                            r = u + 1;
                        }
                    } else {
                        if (s >= u) {
                            s = DimensionType.WAY_BELOW_MIN_Y;

                            for (int v = u - 1; v >= t - 1; v--) {
                                BlockState blockState2 = blockColumn.getBlock(v);
                                if (!this.isStone(blockState2)) {
                                    s = v + 1;
                                    break;
                                }
                            }
                        }

                        q++;
                        int v = u - s + 1;
                        context2.updateY(q, v, r, m, u, n);
                        if (blockState == this.defaultBlock) {
                            BlockState blockState2 = surfaceRule.tryApply(m, u, n);
                            if (blockState2 != null) {
                                blockColumn.setBlock(u, blockState2);
                            }
                        }
                    }
                }

                if (holder.is(Biomes.FROZEN_OCEAN) || holder.is(Biomes.DEEP_FROZEN_OCEAN)) {
                    this.frozenOceanExtension(context2.getMinSurfaceLevel(), holder.value(), blockColumn, mutableBlockPos2, m, n, o);
                }
            }
        }
    }

    protected int getSurfaceDepth(int x, int z) {
        double d = this.surfaceNoise.getValue(x, 0.0, z);
        return (int)(d * 2.75 + 3.0 + this.noiseRandom.at(x, 0, z).nextDouble() * 0.25);
    }

    protected double getSurfaceSecondary(int x, int z) {
        return this.surfaceSecondaryNoise.getValue(x, 0.0, z);
    }

    private boolean isStone(BlockState state) {
        return !state.isAir() && state.getFluidState().isEmpty();
    }

    @Deprecated
    public Optional<BlockState> topMaterial(
        SurfaceRules.RuleSource rule,
        CarvingContext context,
        Function<BlockPos, Holder<Biome>> biomeGetter,
        ChunkAccess chunk,
        NoiseChunk noiseChunk,
        BlockPos pos,
        boolean hasFluid
    ) {
        SurfaceRules.Context context2 = new SurfaceRules.Context(
            this, context.randomState(), chunk, noiseChunk, biomeGetter, context.registryAccess().registryOrThrow(Registries.BIOME), context
        );
        SurfaceRules.SurfaceRule surfaceRule = rule.apply(context2);
        int i = pos.getX();
        int j = pos.getY();
        int k = pos.getZ();
        context2.updateXZ(i, k);
        context2.updateY(1, 1, hasFluid ? j + 1 : Integer.MIN_VALUE, i, j, k);
        BlockState blockState = surfaceRule.tryApply(i, j, k);
        return Optional.ofNullable(blockState);
    }

    private void erodedBadlandsExtension(BlockColumn blockColumn, int x, int z, int height, LevelHeightAccessor level) {
        double d = 0.2;
        double e = Math.min(Math.abs(this.badlandsSurfaceNoise.getValue(x, 0.0, z) * 8.25), this.badlandsPillarNoise.getValue(x * 0.2, 0.0, z * 0.2) * 15.0);
        if (!(e <= 0.0)) {
            double f = 0.75;
            double g = 1.5;
            double h = Math.abs(this.badlandsPillarRoofNoise.getValue(x * 0.75, 0.0, z * 0.75) * 1.5);
            double i = 64.0 + Math.min(e * e * 2.5, Math.ceil(h * 50.0) + 24.0);
            int j = Mth.floor(i);
            if (height <= j) {
                for (int k = j; k >= level.getMinBuildHeight(); k--) {
                    BlockState blockState = blockColumn.getBlock(k);
                    if (blockState.is(this.defaultBlock.getBlock())) {
                        break;
                    }

                    if (blockState.is(Blocks.WATER)) {
                        return;
                    }
                }

                for (int k = j; k >= level.getMinBuildHeight() && blockColumn.getBlock(k).isAir(); k--) {
                    blockColumn.setBlock(k, this.defaultBlock);
                }
            }
        }
    }

    private void frozenOceanExtension(int minSurfaceLevel, Biome biome, BlockColumn blockColumn, BlockPos.MutableBlockPos topWaterPos, int x, int z, int height) {
        double d = 1.28;
        double e = Math.min(Math.abs(this.icebergSurfaceNoise.getValue(x, 0.0, z) * 8.25), this.icebergPillarNoise.getValue(x * 1.28, 0.0, z * 1.28) * 15.0);
        if (!(e <= 1.8)) {
            double f = 1.17;
            double g = 1.5;
            double h = Math.abs(this.icebergPillarRoofNoise.getValue(x * 1.17, 0.0, z * 1.17) * 1.5);
            double i = Math.min(e * e * 1.2, Math.ceil(h * 40.0) + 14.0);
            if (biome.shouldMeltFrozenOceanIcebergSlightly(topWaterPos.set(x, 63, z))) {
                i -= 2.0;
            }

            double j;
            if (i > 2.0) {
                j = this.seaLevel - i - 7.0;
                i += this.seaLevel;
            } else {
                i = 0.0;
                j = 0.0;
            }

            double k = i;
            RandomSource randomSource = this.noiseRandom.at(x, 0, z);
            int l = 2 + randomSource.nextInt(4);
            int m = this.seaLevel + 18 + randomSource.nextInt(10);
            int n = 0;

            for (int o = Math.max(height, (int)k + 1); o >= minSurfaceLevel; o--) {
                if (blockColumn.getBlock(o).isAir() && o < (int)k && randomSource.nextDouble() > 0.01
                    || blockColumn.getBlock(o).is(Blocks.WATER) && o > (int)j && o < this.seaLevel && j != 0.0 && randomSource.nextDouble() > 0.15) {
                    if (n <= l && o > m) {
                        blockColumn.setBlock(o, SNOW_BLOCK);
                        n++;
                    } else {
                        blockColumn.setBlock(o, PACKED_ICE);
                    }
                }
            }
        }
    }

    private static BlockState[] generateBands(RandomSource random) {
        BlockState[] blockStates = new BlockState[192];
        Arrays.fill(blockStates, TERRACOTTA);

        for (int i = 0; i < blockStates.length; i++) {
            i += random.nextInt(5) + 1;
            if (i < blockStates.length) {
                blockStates[i] = ORANGE_TERRACOTTA;
            }
        }

        makeBands(random, blockStates, 1, YELLOW_TERRACOTTA);
        makeBands(random, blockStates, 2, BROWN_TERRACOTTA);
        makeBands(random, blockStates, 1, RED_TERRACOTTA);
        int i = random.nextIntBetweenInclusive(9, 15);
        int j = 0;

        for (int k = 0; j < i && k < blockStates.length; k += random.nextInt(16) + 4) {
            blockStates[k] = WHITE_TERRACOTTA;
            if (k - 1 > 0 && random.nextBoolean()) {
                blockStates[k - 1] = LIGHT_GRAY_TERRACOTTA;
            }

            if (k + 1 < blockStates.length && random.nextBoolean()) {
                blockStates[k + 1] = LIGHT_GRAY_TERRACOTTA;
            }

            j++;
        }

        return blockStates;
    }

    private static void makeBands(RandomSource random, BlockState[] output, int minSize, BlockState state) {
        int i = random.nextIntBetweenInclusive(6, 15);

        for (int j = 0; j < i; j++) {
            int k = minSize + random.nextInt(3);
            int l = random.nextInt(output.length);

            for (int m = 0; l + m < output.length && m < k; m++) {
                output[l + m] = state;
            }
        }
    }

    protected BlockState getBand(int x, int y, int z) {
        int i = (int)Math.round(this.clayBandsOffsetNoise.getValue(x, 0.0, z) * 4.0);
        return this.clayBands[(y + i + this.clayBands.length) % this.clayBands.length];
    }
}
