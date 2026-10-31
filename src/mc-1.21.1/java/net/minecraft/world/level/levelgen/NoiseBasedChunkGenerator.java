package net.minecraft.world.level.levelgen;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Suppliers;
import com.google.common.collect.Sets;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.text.DecimalFormat;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.carver.CarvingContext;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import org.apache.commons.lang3.mutable.MutableObject;

public final class NoiseBasedChunkGenerator extends ChunkGenerator {
    public static final MapCodec<NoiseBasedChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                BiomeSource.CODEC.fieldOf("biome_source").forGetter(generator -> generator.biomeSource),
                NoiseGeneratorSettings.CODEC.fieldOf("settings").forGetter(generator -> generator.settings)
            )
            .apply(instance, instance.stable(NoiseBasedChunkGenerator::new))
    );
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private final Holder<NoiseGeneratorSettings> settings;
    private final Supplier<Aquifer.FluidPicker> globalFluidPicker;

    public NoiseBasedChunkGenerator(BiomeSource biomeSource, Holder<NoiseGeneratorSettings> settings) {
        super(biomeSource);
        this.settings = settings;
        this.globalFluidPicker = Suppliers.memoize(() -> createFluidPicker(settings.value()));
    }

    private static Aquifer.FluidPicker createFluidPicker(NoiseGeneratorSettings settings) {
        Aquifer.FluidStatus fluidStatus = new Aquifer.FluidStatus(-54, Blocks.LAVA.defaultBlockState());
        int i = settings.seaLevel();
        Aquifer.FluidStatus fluidStatus2 = new Aquifer.FluidStatus(i, settings.defaultFluid());
        Aquifer.FluidStatus fluidStatus3 = new Aquifer.FluidStatus(DimensionType.MIN_Y * 2, Blocks.AIR.defaultBlockState());
        return (j, k, l) -> k < Math.min(-54, i) ? fluidStatus : fluidStatus2;
    }

    @Override
    public CompletableFuture<ChunkAccess> createBiomes(RandomState randomState, Blender blender, StructureManager structureManager, ChunkAccess chunk) {
        return CompletableFuture.supplyAsync(Util.wrapThreadWithTaskName("init_biomes", () -> {
            this.doCreateBiomes(blender, randomState, structureManager, chunk);
            return chunk;
        }), Util.backgroundExecutor());
    }

    private void doCreateBiomes(Blender blender, RandomState random, StructureManager structureManager, ChunkAccess chunk) {
        NoiseChunk noiseChunk = chunk.getOrCreateNoiseChunk(chunkAccess -> this.createNoiseChunk(chunkAccess, structureManager, blender, random));
        BiomeResolver biomeResolver = BelowZeroRetrogen.getBiomeResolver(blender.getBiomeResolver(this.biomeSource), chunk);
        chunk.fillBiomesFromNoise(biomeResolver, noiseChunk.cachedClimateSampler(random.router(), this.settings.value().spawnTarget()));
    }

    private NoiseChunk createNoiseChunk(ChunkAccess chunk, StructureManager structureManager, Blender blender, RandomState random) {
        return NoiseChunk.forChunk(
            chunk, random, Beardifier.forStructuresInChunk(structureManager, chunk.getPos()), this.settings.value(), this.globalFluidPicker.get(), blender
        );
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    public Holder<NoiseGeneratorSettings> generatorSettings() {
        return this.settings;
    }

    public boolean stable(ResourceKey<NoiseGeneratorSettings> settings) {
        return this.settings.is(settings);
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
        return this.iterateNoiseColumn(level, random, x, z, null, type.isOpaque()).orElse(level.getMinBuildHeight());
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor height, RandomState random) {
        MutableObject<NoiseColumn> mutableObject = new MutableObject<>();
        this.iterateNoiseColumn(height, random, x, z, mutableObject, null);
        return mutableObject.getValue();
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState random, BlockPos pos) {
        DecimalFormat decimalFormat = new DecimalFormat("0.000");
        NoiseRouter noiseRouter = random.router();
        DensityFunction.SinglePointContext singlePointContext = new DensityFunction.SinglePointContext(pos.getX(), pos.getY(), pos.getZ());
        double d = noiseRouter.ridges().compute(singlePointContext);
        info.add(
            "NoiseRouter T: "
                + decimalFormat.format(noiseRouter.temperature().compute(singlePointContext))
                + " V: "
                + decimalFormat.format(noiseRouter.vegetation().compute(singlePointContext))
                + " C: "
                + decimalFormat.format(noiseRouter.continents().compute(singlePointContext))
                + " E: "
                + decimalFormat.format(noiseRouter.erosion().compute(singlePointContext))
                + " D: "
                + decimalFormat.format(noiseRouter.depth().compute(singlePointContext))
                + " W: "
                + decimalFormat.format(d)
                + " PV: "
                + decimalFormat.format(NoiseRouterData.peaksAndValleys((float)d))
                + " AS: "
                + decimalFormat.format(noiseRouter.initialDensityWithoutJaggedness().compute(singlePointContext))
                + " N: "
                + decimalFormat.format(noiseRouter.finalDensity().compute(singlePointContext))
        );
    }

    private OptionalInt iterateNoiseColumn(
        LevelHeightAccessor level, RandomState random, int x, int z, @Nullable MutableObject<NoiseColumn> column, @Nullable Predicate<BlockState> stoppingState
    ) {
        NoiseSettings noiseSettings = this.settings.value().noiseSettings().clampToHeightAccessor(level);
        int i = noiseSettings.getCellHeight();
        int j = noiseSettings.minY();
        int k = Mth.floorDiv(j, i);
        int l = Mth.floorDiv(noiseSettings.height(), i);
        if (l <= 0) {
            return OptionalInt.empty();
        }

        BlockState[] blockStates;
        if (column == null) {
            blockStates = null;
        } else {
            blockStates = new BlockState[noiseSettings.height()];
            column.setValue(new NoiseColumn(j, blockStates));
        }

        int m = noiseSettings.getCellWidth();
        int n = Math.floorDiv(x, m);
        int o = Math.floorDiv(z, m);
        int p = Math.floorMod(x, m);
        int q = Math.floorMod(z, m);
        int r = n * m;
        int s = o * m;
        double d = (double)p / m;
        double e = (double)q / m;
        NoiseChunk noiseChunk = new NoiseChunk(
            1, random, r, s, noiseSettings, DensityFunctions.BeardifierMarker.INSTANCE, this.settings.value(), this.globalFluidPicker.get(), Blender.empty()
        );
        noiseChunk.initializeForFirstCellX();
        noiseChunk.advanceCellX(0);

        for (int t = l - 1; t >= 0; t--) {
            noiseChunk.selectCellYZ(t, 0);

            for (int u = i - 1; u >= 0; u--) {
                int v = (k + t) * i + u;
                double f = (double)u / i;
                noiseChunk.updateForY(v, f);
                noiseChunk.updateForX(x, d);
                noiseChunk.updateForZ(z, e);
                BlockState blockState = noiseChunk.getInterpolatedState();
                BlockState blockState2 = blockState == null ? this.settings.value().defaultBlock() : blockState;
                if (blockStates != null) {
                    int w = t * i + u;
                    blockStates[w] = blockState2;
                }

                if (stoppingState != null && stoppingState.test(blockState2)) {
                    noiseChunk.stopInterpolation();
                    return OptionalInt.of(v + 1);
                }
            }
        }

        noiseChunk.stopInterpolation();
        return OptionalInt.empty();
    }

    @Override
    public void buildSurface(WorldGenRegion level, StructureManager structureManager, RandomState random, ChunkAccess chunk) {
        if (!SharedConstants.debugVoidTerrain(chunk.getPos())) {
            WorldGenerationContext worldGenerationContext = new WorldGenerationContext(this, level);
            this.buildSurface(
                chunk,
                worldGenerationContext,
                random,
                structureManager,
                level.getBiomeManager(),
                level.registryAccess().registryOrThrow(Registries.BIOME),
                Blender.of(level)
            );
        }
    }

    @VisibleForTesting
    public void buildSurface(
        ChunkAccess chunk,
        WorldGenerationContext context,
        RandomState random,
        StructureManager structureManager,
        BiomeManager biomeManager,
        Registry<Biome> biomes,
        Blender blender
    ) {
        NoiseChunk noiseChunk = chunk.getOrCreateNoiseChunk(chunkAccess -> this.createNoiseChunk(chunkAccess, structureManager, blender, random));
        NoiseGeneratorSettings noiseGeneratorSettings = this.settings.value();
        random.surfaceSystem()
            .buildSurface(
                random, biomeManager, biomes, noiseGeneratorSettings.useLegacyRandomSource(), context, chunk, noiseChunk, noiseGeneratorSettings.surfaceRule()
            );
    }

    @Override
    public void applyCarvers(
        WorldGenRegion level,
        long seed,
        RandomState random,
        BiomeManager biomeManager,
        StructureManager structureManager,
        ChunkAccess chunk,
        GenerationStep.Carving step
    ) {
        BiomeManager biomeManager2 = biomeManager.withDifferentSource((ix, jx, kx) -> this.biomeSource.getNoiseBiome(ix, jx, kx, random.sampler()));
        WorldgenRandom worldgenRandom = new WorldgenRandom(new LegacyRandomSource(RandomSupport.generateUniqueSeed()));
        int i = 8;
        ChunkPos chunkPos = chunk.getPos();
        NoiseChunk noiseChunk = chunk.getOrCreateNoiseChunk(chunkAccessx -> this.createNoiseChunk(chunkAccessx, structureManager, Blender.of(level), random));
        Aquifer aquifer = noiseChunk.aquifer();
        CarvingContext carvingContext = new CarvingContext(
            this, level.registryAccess(), chunk.getHeightAccessorForGeneration(), noiseChunk, random, this.settings.value().surfaceRule()
        );
        CarvingMask carvingMask = ((ProtoChunk)chunk).getOrCreateCarvingMask(step);

        for (int j = -8; j <= 8; j++) {
            for (int k = -8; k <= 8; k++) {
                ChunkPos chunkPos2 = new ChunkPos(chunkPos.x + j, chunkPos.z + k);
                ChunkAccess chunkAccess = level.getChunk(chunkPos2.x, chunkPos2.z);
                BiomeGenerationSettings biomeGenerationSettings = chunkAccess.carverBiome(
                    () -> this.getBiomeGenerationSettings(
                        this.biomeSource
                            .getNoiseBiome(QuartPos.fromBlock(chunkPos2.getMinBlockX()), 0, QuartPos.fromBlock(chunkPos2.getMinBlockZ()), random.sampler())
                    )
                );
                Iterable<Holder<ConfiguredWorldCarver<?>>> iterable = biomeGenerationSettings.getCarvers(step);
                int l = 0;

                for (Holder<ConfiguredWorldCarver<?>> holder : iterable) {
                    ConfiguredWorldCarver<?> configuredWorldCarver = holder.value();
                    worldgenRandom.setLargeFeatureSeed(seed + l, chunkPos2.x, chunkPos2.z);
                    if (configuredWorldCarver.isStartChunk(worldgenRandom)) {
                        configuredWorldCarver.carve(carvingContext, chunk, biomeManager2::getBiome, worldgenRandom, aquifer, chunkPos2, carvingMask);
                    }

                    l++;
                }
            }
        }
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState, StructureManager structureManager, ChunkAccess chunk) {
        NoiseSettings noiseSettings = this.settings.value().noiseSettings().clampToHeightAccessor(chunk.getHeightAccessorForGeneration());
        int i = noiseSettings.minY();
        int j = Mth.floorDiv(i, noiseSettings.getCellHeight());
        int k = Mth.floorDiv(noiseSettings.height(), noiseSettings.getCellHeight());
        return k <= 0 ? CompletableFuture.completedFuture(chunk) : CompletableFuture.supplyAsync(Util.wrapThreadWithTaskName("wgen_fill_noise", () -> {
            int l = chunk.getSectionIndex(k * noiseSettings.getCellHeight() - 1 + i);
            int m = chunk.getSectionIndex(i);
            Set<LevelChunkSection> set = Sets.newHashSet();

            for (int n = l; n >= m; n--) {
                LevelChunkSection levelChunkSection = chunk.getSection(n);
                levelChunkSection.acquire();
                set.add(levelChunkSection);
            }

            try {
                return this.doFill(blender, structureManager, randomState, chunk, j, k);
            } finally {
                for (LevelChunkSection levelChunkSection3 : set) {
                    levelChunkSection3.release();
                }
            }
        }), Util.backgroundExecutor());
    }

    private ChunkAccess doFill(Blender blender, StructureManager structureManager, RandomState random, ChunkAccess chunk, int minCellY, int cellCountY) {
        NoiseChunk noiseChunk = chunk.getOrCreateNoiseChunk(chunkAccess -> this.createNoiseChunk(chunkAccess, structureManager, blender, random));
        Heightmap heightmap = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap heightmap2 = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        ChunkPos chunkPos = chunk.getPos();
        int i = chunkPos.getMinBlockX();
        int j = chunkPos.getMinBlockZ();
        Aquifer aquifer = noiseChunk.aquifer();
        noiseChunk.initializeForFirstCellX();
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
        int k = noiseChunk.cellWidth();
        int l = noiseChunk.cellHeight();
        int m = 16 / k;
        int n = 16 / k;

        for (int o = 0; o < m; o++) {
            noiseChunk.advanceCellX(o);

            for (int p = 0; p < n; p++) {
                int q = chunk.getSectionsCount() - 1;
                LevelChunkSection levelChunkSection = chunk.getSection(q);

                for (int r = cellCountY - 1; r >= 0; r--) {
                    noiseChunk.selectCellYZ(r, p);

                    for (int s = l - 1; s >= 0; s--) {
                        int t = (minCellY + r) * l + s;
                        int u = t & 15;
                        int v = chunk.getSectionIndex(t);
                        if (q != v) {
                            q = v;
                            levelChunkSection = chunk.getSection(v);
                        }

                        double d = (double)s / l;
                        noiseChunk.updateForY(t, d);

                        for (int w = 0; w < k; w++) {
                            int x = i + o * k + w;
                            int y = x & 15;
                            double e = (double)w / k;
                            noiseChunk.updateForX(x, e);

                            for (int z = 0; z < k; z++) {
                                int aa = j + p * k + z;
                                int ab = aa & 15;
                                double f = (double)z / k;
                                noiseChunk.updateForZ(aa, f);
                                BlockState blockState = noiseChunk.getInterpolatedState();
                                if (blockState == null) {
                                    blockState = this.settings.value().defaultBlock();
                                }

                                blockState = this.debugPreliminarySurfaceLevel(noiseChunk, x, t, aa, blockState);
                                if (blockState != AIR && !SharedConstants.debugVoidTerrain(chunk.getPos())) {
                                    levelChunkSection.setBlockState(y, u, ab, blockState, false);
                                    heightmap.update(y, t, ab, blockState);
                                    heightmap2.update(y, t, ab, blockState);
                                    if (aquifer.shouldScheduleFluidUpdate() && !blockState.getFluidState().isEmpty()) {
                                        mutableBlockPos.set(x, t, aa);
                                        chunk.markPosForPostprocessing(mutableBlockPos);
                                    }
                                }
                            }
                        }
                    }
                }
            }

            noiseChunk.swapSlices();
        }

        noiseChunk.stopInterpolation();
        return chunk;
    }

    private BlockState debugPreliminarySurfaceLevel(NoiseChunk chunk, int x, int y, int z, BlockState state) {
        return state;
    }

    @Override
    public int getGenDepth() {
        return this.settings.value().noiseSettings().height();
    }

    @Override
    public int getSeaLevel() {
        return this.settings.value().seaLevel();
    }

    @Override
    public int getMinY() {
        return this.settings.value().noiseSettings().minY();
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion level) {
        if (!this.settings.value().disableMobGeneration()) {
            ChunkPos chunkPos = level.getCenter();
            Holder<Biome> holder = level.getBiome(chunkPos.getWorldPosition().atY(level.getMaxBuildHeight() - 1));
            WorldgenRandom worldgenRandom = new WorldgenRandom(new LegacyRandomSource(RandomSupport.generateUniqueSeed()));
            worldgenRandom.setDecorationSeed(level.getSeed(), chunkPos.getMinBlockX(), chunkPos.getMinBlockZ());
            NaturalSpawner.spawnMobsForChunkGeneration(level, holder, chunkPos, worldgenRandom);
        }
    }
}
