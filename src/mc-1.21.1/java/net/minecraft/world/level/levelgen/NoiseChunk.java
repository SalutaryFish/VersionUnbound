package net.minecraft.world.level.levelgen;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.ImmutableList.Builder;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.QuartPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ColumnPos;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.material.MaterialRuleList;

public class NoiseChunk implements DensityFunction.ContextProvider, DensityFunction.FunctionContext {
    private final NoiseSettings noiseSettings;
    final int cellCountXZ;
    final int cellCountY;
    final int cellNoiseMinY;
    private final int firstCellX;
    private final int firstCellZ;
    final int firstNoiseX;
    final int firstNoiseZ;
    final List<NoiseChunk.NoiseInterpolator> interpolators;
    final List<NoiseChunk.CacheAllInCell> cellCaches;
    private final Map<DensityFunction, DensityFunction> wrapped = new HashMap<>();
    private final Long2IntMap preliminarySurfaceLevel = new Long2IntOpenHashMap();
    private final Aquifer aquifer;
    private final DensityFunction initialDensityNoJaggedness;
    private final NoiseChunk.BlockStateFiller blockStateRule;
    private final Blender blender;
    private final NoiseChunk.FlatCache blendAlpha;
    private final NoiseChunk.FlatCache blendOffset;
    private final DensityFunctions.BeardifierOrMarker beardifier;
    private long lastBlendingDataPos = ChunkPos.INVALID_CHUNK_POS;
    private Blender.BlendingOutput lastBlendingOutput = new Blender.BlendingOutput(1.0, 0.0);
    final int noiseSizeXZ;
    final int cellWidth;
    final int cellHeight;
    boolean interpolating;
    boolean fillingCell;
    private int cellStartBlockX;
    int cellStartBlockY;
    private int cellStartBlockZ;
    int inCellX;
    int inCellY;
    int inCellZ;
    long interpolationCounter;
    long arrayInterpolationCounter;
    int arrayIndex;
    private final DensityFunction.ContextProvider sliceFillingContextProvider = new DensityFunction.ContextProvider() {
        @Override
        public DensityFunction.FunctionContext forIndex(int i) {
            NoiseChunk.this.cellStartBlockY = (i + NoiseChunk.this.cellNoiseMinY) * NoiseChunk.this.cellHeight;
            NoiseChunk.this.interpolationCounter++;
            NoiseChunk.this.inCellY = 0;
            NoiseChunk.this.arrayIndex = i;
            return NoiseChunk.this;
        }

        @Override
        public void fillAllDirectly(double[] ds, DensityFunction densityFunction) {
            for (int i = 0; i < NoiseChunk.this.cellCountY + 1; i++) {
                NoiseChunk.this.cellStartBlockY = (i + NoiseChunk.this.cellNoiseMinY) * NoiseChunk.this.cellHeight;
                NoiseChunk.this.interpolationCounter++;
                NoiseChunk.this.inCellY = 0;
                NoiseChunk.this.arrayIndex = i;
                ds[i] = densityFunction.compute(NoiseChunk.this);
            }
        }
    };

    public static NoiseChunk forChunk(
        ChunkAccess chunk,
        RandomState state,
        DensityFunctions.BeardifierOrMarker beardifierOrMarker,
        NoiseGeneratorSettings noiseGeneratorSettings,
        Aquifer.FluidPicker fluidPicke,
        Blender blender
    ) {
        NoiseSettings noiseSettings = noiseGeneratorSettings.noiseSettings().clampToHeightAccessor(chunk);
        ChunkPos chunkPos = chunk.getPos();
        int i = 16 / noiseSettings.getCellWidth();
        return new NoiseChunk(
            i, state, chunkPos.getMinBlockX(), chunkPos.getMinBlockZ(), noiseSettings, beardifierOrMarker, noiseGeneratorSettings, fluidPicke, blender
        );
    }

    public NoiseChunk(
        int cellCountXZ,
        RandomState random,
        int firstNoiseX,
        int firstNoiseZ,
        NoiseSettings noiseSettings,
        DensityFunctions.BeardifierOrMarker beardifier,
        NoiseGeneratorSettings noiseGeneratorSettings,
        Aquifer.FluidPicker fluidPicker,
        Blender blendifier
    ) {
        this.noiseSettings = noiseSettings;
        this.cellWidth = noiseSettings.getCellWidth();
        this.cellHeight = noiseSettings.getCellHeight();
        this.cellCountXZ = cellCountXZ;
        this.cellCountY = Mth.floorDiv(noiseSettings.height(), this.cellHeight);
        this.cellNoiseMinY = Mth.floorDiv(noiseSettings.minY(), this.cellHeight);
        this.firstCellX = Math.floorDiv(firstNoiseX, this.cellWidth);
        this.firstCellZ = Math.floorDiv(firstNoiseZ, this.cellWidth);
        this.interpolators = Lists.newArrayList();
        this.cellCaches = Lists.newArrayList();
        this.firstNoiseX = QuartPos.fromBlock(firstNoiseX);
        this.firstNoiseZ = QuartPos.fromBlock(firstNoiseZ);
        this.noiseSizeXZ = QuartPos.fromBlock(cellCountXZ * this.cellWidth);
        this.blender = blendifier;
        this.beardifier = beardifier;
        this.blendAlpha = new NoiseChunk.FlatCache(new NoiseChunk.BlendAlpha(), false);
        this.blendOffset = new NoiseChunk.FlatCache(new NoiseChunk.BlendOffset(), false);

        for (int i = 0; i <= this.noiseSizeXZ; i++) {
            int j = this.firstNoiseX + i;
            int k = QuartPos.toBlock(j);

            for (int l = 0; l <= this.noiseSizeXZ; l++) {
                int m = this.firstNoiseZ + l;
                int n = QuartPos.toBlock(m);
                Blender.BlendingOutput blendingOutput = blendifier.blendOffsetAndFactor(k, n);
                this.blendAlpha.values[i][l] = blendingOutput.alpha();
                this.blendOffset.values[i][l] = blendingOutput.blendingOffset();
            }
        }

        NoiseRouter noiseRouter = random.router();
        NoiseRouter noiseRouter2 = noiseRouter.mapAll(this::wrap);
        if (!noiseGeneratorSettings.isAquifersEnabled()) {
            this.aquifer = Aquifer.createDisabled(fluidPicker);
        } else {
            int k = SectionPos.blockToSectionCoord(firstNoiseX);
            int l = SectionPos.blockToSectionCoord(firstNoiseZ);
            this.aquifer = Aquifer.create(
                this, new ChunkPos(k, l), noiseRouter2, random.aquiferRandom(), noiseSettings.minY(), noiseSettings.height(), fluidPicker
            );
        }

        Builder<NoiseChunk.BlockStateFiller> builder = ImmutableList.builder();
        DensityFunction densityFunction = DensityFunctions.cacheAllInCell(
                DensityFunctions.add(noiseRouter2.finalDensity(), DensityFunctions.BeardifierMarker.INSTANCE)
            )
            .mapAll(this::wrap);
        builder.add(context -> this.aquifer.computeSubstance(context, densityFunction.compute(context)));
        if (noiseGeneratorSettings.oreVeinsEnabled()) {
            builder.add(OreVeinifier.create(noiseRouter2.veinToggle(), noiseRouter2.veinRidged(), noiseRouter2.veinGap(), random.oreRandom()));
        }

        this.blockStateRule = new MaterialRuleList(builder.build());
        this.initialDensityNoJaggedness = noiseRouter2.initialDensityWithoutJaggedness();
    }

    protected Climate.Sampler cachedClimateSampler(NoiseRouter noiseRouter, List<Climate.ParameterPoint> points) {
        return new Climate.Sampler(
            noiseRouter.temperature().mapAll(this::wrap),
            noiseRouter.vegetation().mapAll(this::wrap),
            noiseRouter.continents().mapAll(this::wrap),
            noiseRouter.erosion().mapAll(this::wrap),
            noiseRouter.depth().mapAll(this::wrap),
            noiseRouter.ridges().mapAll(this::wrap),
            points
        );
    }

    @Nullable
    protected BlockState getInterpolatedState() {
        return this.blockStateRule.calculate(this);
    }

    @Override
    public int blockX() {
        return this.cellStartBlockX + this.inCellX;
    }

    @Override
    public int blockY() {
        return this.cellStartBlockY + this.inCellY;
    }

    @Override
    public int blockZ() {
        return this.cellStartBlockZ + this.inCellZ;
    }

    public int preliminarySurfaceLevel(int x, int z) {
        int i = QuartPos.toBlock(QuartPos.fromBlock(x));
        int j = QuartPos.toBlock(QuartPos.fromBlock(z));
        return this.preliminarySurfaceLevel.computeIfAbsent(ColumnPos.asLong(i, j), this::computePreliminarySurfaceLevel);
    }

    private int computePreliminarySurfaceLevel(long packedChunkPos) {
        int i = ColumnPos.getX(packedChunkPos);
        int j = ColumnPos.getZ(packedChunkPos);
        int k = this.noiseSettings.minY();

        for (int l = k + this.noiseSettings.height(); l >= k; l -= this.cellHeight) {
            if (this.initialDensityNoJaggedness.compute(new DensityFunction.SinglePointContext(i, l, j)) > 0.390625) {
                return l;
            }
        }

        return Integer.MAX_VALUE;
    }

    @Override
    public Blender getBlender() {
        return this.blender;
    }

    private void fillSlice(boolean isSlice0, int start) {
        this.cellStartBlockX = start * this.cellWidth;
        this.inCellX = 0;

        for (int i = 0; i < this.cellCountXZ + 1; i++) {
            int j = this.firstCellZ + i;
            this.cellStartBlockZ = j * this.cellWidth;
            this.inCellZ = 0;
            this.arrayInterpolationCounter++;

            for (NoiseChunk.NoiseInterpolator noiseInterpolator : this.interpolators) {
                double[] ds = (isSlice0 ? noiseInterpolator.slice0 : noiseInterpolator.slice1)[i];
                noiseInterpolator.fillArray(ds, this.sliceFillingContextProvider);
            }
        }

        this.arrayInterpolationCounter++;
    }

    public void initializeForFirstCellX() {
        if (this.interpolating) {
            throw new IllegalStateException("Staring interpolation twice");
        }

        this.interpolating = true;
        this.interpolationCounter = 0L;
        this.fillSlice(true, this.firstCellX);
    }

    public void advanceCellX(int increment) {
        this.fillSlice(false, this.firstCellX + increment + 1);
        this.cellStartBlockX = (this.firstCellX + increment) * this.cellWidth;
    }

    public NoiseChunk forIndex(int arrayIndex) {
        int i = Math.floorMod(arrayIndex, this.cellWidth);
        int j = Math.floorDiv(arrayIndex, this.cellWidth);
        int k = Math.floorMod(j, this.cellWidth);
        int l = this.cellHeight - 1 - Math.floorDiv(j, this.cellWidth);
        this.inCellX = k;
        this.inCellY = l;
        this.inCellZ = i;
        this.arrayIndex = arrayIndex;
        return this;
    }

    @Override
    public void fillAllDirectly(double[] values, DensityFunction function) {
        this.arrayIndex = 0;

        for (int i = this.cellHeight - 1; i >= 0; i--) {
            this.inCellY = i;

            for (int j = 0; j < this.cellWidth; j++) {
                this.inCellX = j;

                for (int k = 0; k < this.cellWidth; k++) {
                    this.inCellZ = k;
                    values[this.arrayIndex++] = function.compute(this);
                }
            }
        }
    }

    public void selectCellYZ(int y, int z) {
        this.interpolators.forEach(noiseInterpolator -> noiseInterpolator.selectCellYZ(y, z));
        this.fillingCell = true;
        this.cellStartBlockY = (y + this.cellNoiseMinY) * this.cellHeight;
        this.cellStartBlockZ = (this.firstCellZ + z) * this.cellWidth;
        this.arrayInterpolationCounter++;

        for (NoiseChunk.CacheAllInCell cacheAllInCell : this.cellCaches) {
            cacheAllInCell.noiseFiller.fillArray(cacheAllInCell.values, this);
        }

        this.arrayInterpolationCounter++;
        this.fillingCell = false;
    }

    public void updateForY(int cellEndBlockY, double y) {
        this.inCellY = cellEndBlockY - this.cellStartBlockY;
        this.interpolators.forEach(noiseInterpolator -> noiseInterpolator.updateForY(y));
    }

    public void updateForX(int cellEndBlockX, double x) {
        this.inCellX = cellEndBlockX - this.cellStartBlockX;
        this.interpolators.forEach(noiseInterpolator -> noiseInterpolator.updateForX(x));
    }

    public void updateForZ(int cellEndBlockZ, double z) {
        this.inCellZ = cellEndBlockZ - this.cellStartBlockZ;
        this.interpolationCounter++;
        this.interpolators.forEach(noiseInterpolator -> noiseInterpolator.updateForZ(z));
    }

    public void stopInterpolation() {
        if (!this.interpolating) {
            throw new IllegalStateException("Staring interpolation twice");
        }

        this.interpolating = false;
    }

    public void swapSlices() {
        this.interpolators.forEach(NoiseChunk.NoiseInterpolator::swapSlices);
    }

    public Aquifer aquifer() {
        return this.aquifer;
    }

    protected int cellWidth() {
        return this.cellWidth;
    }

    protected int cellHeight() {
        return this.cellHeight;
    }

    Blender.BlendingOutput getOrComputeBlendingOutput(int chunkX, int chunkZ) {
        long l = ChunkPos.asLong(chunkX, chunkZ);
        if (this.lastBlendingDataPos == l) {
            return this.lastBlendingOutput;
        }

        this.lastBlendingDataPos = l;
        Blender.BlendingOutput blendingOutput = this.blender.blendOffsetAndFactor(chunkX, chunkZ);
        this.lastBlendingOutput = blendingOutput;
        return blendingOutput;
    }

    protected DensityFunction wrap(DensityFunction densityFunction) {
        return this.wrapped.computeIfAbsent(densityFunction, this::wrapNew);
    }

    private DensityFunction wrapNew(DensityFunction densityFunction) {
        if (densityFunction instanceof DensityFunctions.Marker marker) {
            return switch (marker.type()) {
                case Interpolated -> new NoiseChunk.NoiseInterpolator(marker.wrapped());
                case FlatCache -> new NoiseChunk.FlatCache(marker.wrapped(), true);
                case Cache2D -> new NoiseChunk.Cache2D(marker.wrapped());
                case CacheOnce -> new NoiseChunk.CacheOnce(marker.wrapped());
                case CacheAllInCell -> new NoiseChunk.CacheAllInCell(marker.wrapped());
            };
        } else {
            if (this.blender != Blender.empty()) {
                if (densityFunction == DensityFunctions.BlendAlpha.INSTANCE) {
                    return this.blendAlpha;
                }

                if (densityFunction == DensityFunctions.BlendOffset.INSTANCE) {
                    return this.blendOffset;
                }
            }

            if (densityFunction == DensityFunctions.BeardifierMarker.INSTANCE) {
                return this.beardifier;
            } else {
                return densityFunction instanceof DensityFunctions.HolderHolder holderHolder ? holderHolder.function().value() : densityFunction;
            }
        }
    }

    class BlendAlpha implements NoiseChunk.NoiseChunkDensityFunction {
        @Override
        public DensityFunction wrapped() {
            return DensityFunctions.BlendAlpha.INSTANCE;
        }

        @Override
        public DensityFunction mapAll(DensityFunction.Visitor visitor) {
            return this.wrapped().mapAll(visitor);
        }

        @Override
        public double compute(DensityFunction.FunctionContext functionContext) {
            return NoiseChunk.this.getOrComputeBlendingOutput(functionContext.blockX(), functionContext.blockZ()).alpha();
        }

        @Override
        public void fillArray(double[] ds, DensityFunction.ContextProvider contextProvider) {
            contextProvider.fillAllDirectly(ds, this);
        }

        @Override
        public double minValue() {
            return 0.0;
        }

        @Override
        public double maxValue() {
            return 1.0;
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return DensityFunctions.BlendAlpha.CODEC;
        }
    }

    class BlendOffset implements NoiseChunk.NoiseChunkDensityFunction {
        @Override
        public DensityFunction wrapped() {
            return DensityFunctions.BlendOffset.INSTANCE;
        }

        @Override
        public DensityFunction mapAll(DensityFunction.Visitor visitor) {
            return this.wrapped().mapAll(visitor);
        }

        @Override
        public double compute(DensityFunction.FunctionContext functionContext) {
            return NoiseChunk.this.getOrComputeBlendingOutput(functionContext.blockX(), functionContext.blockZ()).blendingOffset();
        }

        @Override
        public void fillArray(double[] ds, DensityFunction.ContextProvider contextProvider) {
            contextProvider.fillAllDirectly(ds, this);
        }

        @Override
        public double minValue() {
            return Double.NEGATIVE_INFINITY;
        }

        @Override
        public double maxValue() {
            return Double.POSITIVE_INFINITY;
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return DensityFunctions.BlendOffset.CODEC;
        }
    }

    @FunctionalInterface
    public interface BlockStateFiller {
        @Nullable
        BlockState calculate(DensityFunction.FunctionContext var1);
    }

    static class Cache2D implements DensityFunctions.MarkerOrMarked, NoiseChunk.NoiseChunkDensityFunction {
        private final DensityFunction function;
        private long lastPos2D = ChunkPos.INVALID_CHUNK_POS;
        private double lastValue;

        Cache2D(DensityFunction function) {
            this.function = function;
        }

        @Override
        public double compute(DensityFunction.FunctionContext context) {
            int i = context.blockX();
            int j = context.blockZ();
            long l = ChunkPos.asLong(i, j);
            if (this.lastPos2D == l) {
                return this.lastValue;
            }

            this.lastPos2D = l;
            double d = this.function.compute(context);
            this.lastValue = d;
            return d;
        }

        @Override
        public void fillArray(double[] array, DensityFunction.ContextProvider contextProvider) {
            this.function.fillArray(array, contextProvider);
        }

        @Override
        public DensityFunction wrapped() {
            return this.function;
        }

        @Override
        public DensityFunctions.Marker.Type type() {
            return DensityFunctions.Marker.Type.Cache2D;
        }
    }

    class CacheAllInCell implements DensityFunctions.MarkerOrMarked, NoiseChunk.NoiseChunkDensityFunction {
        final DensityFunction noiseFiller;
        final double[] values;

        CacheAllInCell(final DensityFunction noiseFilter) {
            this.noiseFiller = noiseFilter;
            this.values = new double[NoiseChunk.this.cellWidth * NoiseChunk.this.cellWidth * NoiseChunk.this.cellHeight];
            NoiseChunk.this.cellCaches.add(this);
        }

        @Override
        public double compute(DensityFunction.FunctionContext context) {
            if (context != NoiseChunk.this) {
                return this.noiseFiller.compute(context);
            }

            if (!NoiseChunk.this.interpolating) {
                throw new IllegalStateException("Trying to sample interpolator outside the interpolation loop");
            }

            int i = NoiseChunk.this.inCellX;
            int j = NoiseChunk.this.inCellY;
            int k = NoiseChunk.this.inCellZ;
            return i >= 0 && j >= 0 && k >= 0 && i < NoiseChunk.this.cellWidth && j < NoiseChunk.this.cellHeight && k < NoiseChunk.this.cellWidth
                ? this.values[((NoiseChunk.this.cellHeight - 1 - j) * NoiseChunk.this.cellWidth + i) * NoiseChunk.this.cellWidth + k]
                : this.noiseFiller.compute(context);
        }

        @Override
        public void fillArray(double[] array, DensityFunction.ContextProvider contextProvider) {
            contextProvider.fillAllDirectly(array, this);
        }

        @Override
        public DensityFunction wrapped() {
            return this.noiseFiller;
        }

        @Override
        public DensityFunctions.Marker.Type type() {
            return DensityFunctions.Marker.Type.CacheAllInCell;
        }
    }

    class CacheOnce implements DensityFunctions.MarkerOrMarked, NoiseChunk.NoiseChunkDensityFunction {
        private final DensityFunction function;
        private long lastCounter;
        private long lastArrayCounter;
        private double lastValue;
        @Nullable
        private double[] lastArray;

        CacheOnce(final DensityFunction function) {
            this.function = function;
        }

        @Override
        public double compute(DensityFunction.FunctionContext context) {
            if (context != NoiseChunk.this) {
                return this.function.compute(context);
            }

            if (this.lastArray != null && this.lastArrayCounter == NoiseChunk.this.arrayInterpolationCounter) {
                return this.lastArray[NoiseChunk.this.arrayIndex];
            }

            if (this.lastCounter == NoiseChunk.this.interpolationCounter) {
                return this.lastValue;
            }

            this.lastCounter = NoiseChunk.this.interpolationCounter;
            double d = this.function.compute(context);
            this.lastValue = d;
            return d;
        }

        @Override
        public void fillArray(double[] array, DensityFunction.ContextProvider contextProvider) {
            if (this.lastArray != null && this.lastArrayCounter == NoiseChunk.this.arrayInterpolationCounter) {
                System.arraycopy(this.lastArray, 0, array, 0, array.length);
            } else {
                this.wrapped().fillArray(array, contextProvider);
                if (this.lastArray != null && this.lastArray.length == array.length) {
                    System.arraycopy(array, 0, this.lastArray, 0, array.length);
                } else {
                    this.lastArray = (double[])array.clone();
                }

                this.lastArrayCounter = NoiseChunk.this.arrayInterpolationCounter;
            }
        }

        @Override
        public DensityFunction wrapped() {
            return this.function;
        }

        @Override
        public DensityFunctions.Marker.Type type() {
            return DensityFunctions.Marker.Type.CacheOnce;
        }
    }

    class FlatCache implements DensityFunctions.MarkerOrMarked, NoiseChunk.NoiseChunkDensityFunction {
        private final DensityFunction noiseFiller;
        final double[][] values;

        FlatCache(final DensityFunction noiseFiller, final boolean computeValues) {
            this.noiseFiller = noiseFiller;
            this.values = new double[NoiseChunk.this.noiseSizeXZ + 1][NoiseChunk.this.noiseSizeXZ + 1];
            if (computeValues) {
                for (int i = 0; i <= NoiseChunk.this.noiseSizeXZ; i++) {
                    int j = NoiseChunk.this.firstNoiseX + i;
                    int k = QuartPos.toBlock(j);

                    for (int l = 0; l <= NoiseChunk.this.noiseSizeXZ; l++) {
                        int m = NoiseChunk.this.firstNoiseZ + l;
                        int n = QuartPos.toBlock(m);
                        this.values[i][l] = noiseFiller.compute(new DensityFunction.SinglePointContext(k, 0, n));
                    }
                }
            }
        }

        @Override
        public double compute(DensityFunction.FunctionContext context) {
            int i = QuartPos.fromBlock(context.blockX());
            int j = QuartPos.fromBlock(context.blockZ());
            int k = i - NoiseChunk.this.firstNoiseX;
            int l = j - NoiseChunk.this.firstNoiseZ;
            int m = this.values.length;
            return k >= 0 && l >= 0 && k < m && l < m ? this.values[k][l] : this.noiseFiller.compute(context);
        }

        @Override
        public void fillArray(double[] array, DensityFunction.ContextProvider contextProvider) {
            contextProvider.fillAllDirectly(array, this);
        }

        @Override
        public DensityFunction wrapped() {
            return this.noiseFiller;
        }

        @Override
        public DensityFunctions.Marker.Type type() {
            return DensityFunctions.Marker.Type.FlatCache;
        }
    }

    interface NoiseChunkDensityFunction extends DensityFunction {
        DensityFunction wrapped();

        @Override
        default double minValue() {
            return this.wrapped().minValue();
        }

        @Override
        default double maxValue() {
            return this.wrapped().maxValue();
        }
    }

    public class NoiseInterpolator implements DensityFunctions.MarkerOrMarked, NoiseChunk.NoiseChunkDensityFunction {
        double[][] slice0;
        double[][] slice1;
        private final DensityFunction noiseFiller;
        private double noise000;
        private double noise001;
        private double noise100;
        private double noise101;
        private double noise010;
        private double noise011;
        private double noise110;
        private double noise111;
        private double valueXZ00;
        private double valueXZ10;
        private double valueXZ01;
        private double valueXZ11;
        private double valueZ0;
        private double valueZ1;
        private double value;

        NoiseInterpolator(final DensityFunction noiseFilter) {
            this.noiseFiller = noiseFilter;
            this.slice0 = this.allocateSlice(NoiseChunk.this.cellCountY, NoiseChunk.this.cellCountXZ);
            this.slice1 = this.allocateSlice(NoiseChunk.this.cellCountY, NoiseChunk.this.cellCountXZ);
            NoiseChunk.this.interpolators.add(this);
        }

        private double[][] allocateSlice(int cellCountY, int cellCountXZ) {
            int i = cellCountXZ + 1;
            int j = cellCountY + 1;
            double[][] ds = new double[i][j];

            for (int k = 0; k < i; k++) {
                ds[k] = new double[j];
            }

            return ds;
        }

        void selectCellYZ(int y, int z) {
            this.noise000 = this.slice0[z][y];
            this.noise001 = this.slice0[z + 1][y];
            this.noise100 = this.slice1[z][y];
            this.noise101 = this.slice1[z + 1][y];
            this.noise010 = this.slice0[z][y + 1];
            this.noise011 = this.slice0[z + 1][y + 1];
            this.noise110 = this.slice1[z][y + 1];
            this.noise111 = this.slice1[z + 1][y + 1];
        }

        void updateForY(double y) {
            this.valueXZ00 = Mth.lerp(y, this.noise000, this.noise010);
            this.valueXZ10 = Mth.lerp(y, this.noise100, this.noise110);
            this.valueXZ01 = Mth.lerp(y, this.noise001, this.noise011);
            this.valueXZ11 = Mth.lerp(y, this.noise101, this.noise111);
        }

        void updateForX(double x) {
            this.valueZ0 = Mth.lerp(x, this.valueXZ00, this.valueXZ10);
            this.valueZ1 = Mth.lerp(x, this.valueXZ01, this.valueXZ11);
        }

        void updateForZ(double z) {
            this.value = Mth.lerp(z, this.valueZ0, this.valueZ1);
        }

        @Override
        public double compute(DensityFunction.FunctionContext context) {
            if (context != NoiseChunk.this) {
                return this.noiseFiller.compute(context);
            } else if (!NoiseChunk.this.interpolating) {
                throw new IllegalStateException("Trying to sample interpolator outside the interpolation loop");
            } else {
                return NoiseChunk.this.fillingCell
                    ? Mth.lerp3(
                        (double)NoiseChunk.this.inCellX / NoiseChunk.this.cellWidth,
                        (double)NoiseChunk.this.inCellY / NoiseChunk.this.cellHeight,
                        (double)NoiseChunk.this.inCellZ / NoiseChunk.this.cellWidth,
                        this.noise000,
                        this.noise100,
                        this.noise010,
                        this.noise110,
                        this.noise001,
                        this.noise101,
                        this.noise011,
                        this.noise111
                    )
                    : this.value;
            }
        }

        @Override
        public void fillArray(double[] array, DensityFunction.ContextProvider contextProvider) {
            if (NoiseChunk.this.fillingCell) {
                contextProvider.fillAllDirectly(array, this);
            } else {
                this.wrapped().fillArray(array, contextProvider);
            }
        }

        @Override
        public DensityFunction wrapped() {
            return this.noiseFiller;
        }

        private void swapSlices() {
            double[][] ds = this.slice0;
            this.slice0 = this.slice1;
            this.slice1 = ds;
        }

        @Override
        public DensityFunctions.Marker.Type type() {
            return DensityFunctions.Marker.Type.Interpolated;
        }
    }
}
