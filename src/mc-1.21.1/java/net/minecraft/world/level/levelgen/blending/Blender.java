package net.minecraft.world.level.levelgen.blending;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.ImmutableMap.Builder;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction8;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.data.worldgen.NoiseData;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.minecraft.world.level.material.FluidState;
import org.apache.commons.lang3.mutable.MutableDouble;
import org.apache.commons.lang3.mutable.MutableObject;

public class Blender {
    private static final Blender EMPTY = new Blender(new Long2ObjectOpenHashMap(), new Long2ObjectOpenHashMap()) {
        @Override
        public Blender.BlendingOutput blendOffsetAndFactor(int i, int j) {
            return new Blender.BlendingOutput(1.0, 0.0);
        }

        @Override
        public double blendDensity(DensityFunction.FunctionContext functionContext, double d) {
            return d;
        }

        @Override
        public BiomeResolver getBiomeResolver(BiomeResolver biomeResolver) {
            return biomeResolver;
        }
    };
    private static final NormalNoise SHIFT_NOISE = NormalNoise.create(new XoroshiroRandomSource(42L), NoiseData.DEFAULT_SHIFT);
    private static final int HEIGHT_BLENDING_RANGE_CELLS = QuartPos.fromSection(7) - 1;
    private static final int HEIGHT_BLENDING_RANGE_CHUNKS = QuartPos.toSection(HEIGHT_BLENDING_RANGE_CELLS + 3);
    private static final int DENSITY_BLENDING_RANGE_CELLS = 2;
    private static final int DENSITY_BLENDING_RANGE_CHUNKS = QuartPos.toSection(5);
    private static final double OLD_CHUNK_XZ_RADIUS = 8.0;
    private final Long2ObjectOpenHashMap<BlendingData> heightAndBiomeBlendingData;
    private final Long2ObjectOpenHashMap<BlendingData> densityBlendingData;

    public static Blender empty() {
        return EMPTY;
    }

    public static Blender of(@Nullable WorldGenRegion region) {
        if (region == null) {
            return EMPTY;
        }

        ChunkPos chunkPos = region.getCenter();
        if (!region.isOldChunkAround(chunkPos, HEIGHT_BLENDING_RANGE_CHUNKS)) {
            return EMPTY;
        }

        Long2ObjectOpenHashMap<BlendingData> long2ObjectOpenHashMap = new Long2ObjectOpenHashMap<>();
        Long2ObjectOpenHashMap<BlendingData> long2ObjectOpenHashMap2 = new Long2ObjectOpenHashMap<>();
        int i = Mth.square(HEIGHT_BLENDING_RANGE_CHUNKS + 1);

        for (int j = -HEIGHT_BLENDING_RANGE_CHUNKS; j <= HEIGHT_BLENDING_RANGE_CHUNKS; j++) {
            for (int k = -HEIGHT_BLENDING_RANGE_CHUNKS; k <= HEIGHT_BLENDING_RANGE_CHUNKS; k++) {
                if (j * j + k * k <= i) {
                    int l = chunkPos.x + j;
                    int m = chunkPos.z + k;
                    BlendingData blendingData = BlendingData.getOrUpdateBlendingData(region, l, m);
                    if (blendingData != null) {
                        long2ObjectOpenHashMap.put(ChunkPos.asLong(l, m), blendingData);
                        if (j >= -DENSITY_BLENDING_RANGE_CHUNKS
                            && j <= DENSITY_BLENDING_RANGE_CHUNKS
                            && k >= -DENSITY_BLENDING_RANGE_CHUNKS
                            && k <= DENSITY_BLENDING_RANGE_CHUNKS) {
                            long2ObjectOpenHashMap2.put(ChunkPos.asLong(l, m), blendingData);
                        }
                    }
                }
            }
        }

        return long2ObjectOpenHashMap.isEmpty() && long2ObjectOpenHashMap2.isEmpty() ? EMPTY : new Blender(long2ObjectOpenHashMap, long2ObjectOpenHashMap2);
    }

    Blender(Long2ObjectOpenHashMap<BlendingData> heightAndBiomeBlendingData, Long2ObjectOpenHashMap<BlendingData> densityBlendingData) {
        this.heightAndBiomeBlendingData = heightAndBiomeBlendingData;
        this.densityBlendingData = densityBlendingData;
    }

    public Blender.BlendingOutput blendOffsetAndFactor(int x, int z) {
        int i = QuartPos.fromBlock(x);
        int j = QuartPos.fromBlock(z);
        double d = this.getBlendingDataValue(i, 0, j, BlendingData::getHeight);
        if (d != Double.MAX_VALUE) {
            return new Blender.BlendingOutput(0.0, heightToOffset(d));
        }

        MutableDouble mutableDouble = new MutableDouble(0.0);
        MutableDouble mutableDouble2 = new MutableDouble(0.0);
        MutableDouble mutableDouble3 = new MutableDouble(Double.POSITIVE_INFINITY);
        this.heightAndBiomeBlendingData
            .forEach(
                (long_, blendingData) -> blendingData.iterateHeights(
                    QuartPos.fromSection(ChunkPos.getX(long_)), QuartPos.fromSection(ChunkPos.getZ(long_)), (k, l, dx) -> {
                        double ex = Mth.length(i - k, j - l);
                        if (!(ex > HEIGHT_BLENDING_RANGE_CELLS)) {
                            if (ex < mutableDouble3.doubleValue()) {
                                mutableDouble3.setValue(ex);
                            }

                            double fx = 1.0 / (ex * ex * ex * ex);
                            mutableDouble2.add(dx * fx);
                            mutableDouble.add(fx);
                        }
                    }
                )
            );
        if (mutableDouble3.doubleValue() == Double.POSITIVE_INFINITY) {
            return new Blender.BlendingOutput(1.0, 0.0);
        }

        double e = mutableDouble2.doubleValue() / mutableDouble.doubleValue();
        double f = Mth.clamp(mutableDouble3.doubleValue() / (HEIGHT_BLENDING_RANGE_CELLS + 1), 0.0, 1.0);
        f = 3.0 * f * f - 2.0 * f * f * f;
        return new Blender.BlendingOutput(f, heightToOffset(e));
    }

    private static double heightToOffset(double height) {
        double d = 1.0;
        double e = height + 0.5;
        double f = Mth.positiveModulo(e, 8.0);
        return 1.0 * (32.0 * (e - 128.0) - 3.0 * (e - 120.0) * f + 3.0 * f * f) / (128.0 * (32.0 - 3.0 * f));
    }

    public double blendDensity(DensityFunction.FunctionContext context, double density) {
        int i = QuartPos.fromBlock(context.blockX());
        int j = context.blockY() / 8;
        int k = QuartPos.fromBlock(context.blockZ());
        double d = this.getBlendingDataValue(i, j, k, BlendingData::getDensity);
        if (d != Double.MAX_VALUE) {
            return d;
        }

        MutableDouble mutableDouble = new MutableDouble(0.0);
        MutableDouble mutableDouble2 = new MutableDouble(0.0);
        MutableDouble mutableDouble3 = new MutableDouble(Double.POSITIVE_INFINITY);
        this.densityBlendingData
            .forEach(
                (long_, blendingData) -> blendingData.iterateDensities(
                    QuartPos.fromSection(ChunkPos.getX(long_)), QuartPos.fromSection(ChunkPos.getZ(long_)), j - 1, j + 1, (l, m, n, dx) -> {
                        double ex = Mth.length(i - l, (j - m) * 2, k - n);
                        if (!(ex > 2.0)) {
                            if (ex < mutableDouble3.doubleValue()) {
                                mutableDouble3.setValue(ex);
                            }

                            double fx = 1.0 / (ex * ex * ex * ex);
                            mutableDouble2.add(dx * fx);
                            mutableDouble.add(fx);
                        }
                    }
                )
            );
        if (mutableDouble3.doubleValue() == Double.POSITIVE_INFINITY) {
            return density;
        }

        double e = mutableDouble2.doubleValue() / mutableDouble.doubleValue();
        double f = Mth.clamp(mutableDouble3.doubleValue() / 3.0, 0.0, 1.0);
        return Mth.lerp(f, e, density);
    }

    private double getBlendingDataValue(int x, int y, int z, Blender.CellValueGetter getter) {
        int i = QuartPos.toSection(x);
        int j = QuartPos.toSection(z);
        boolean bl = (x & 3) == 0;
        boolean bl2 = (z & 3) == 0;
        double d = this.getBlendingDataValue(getter, i, j, x, y, z);
        if (d == Double.MAX_VALUE) {
            if (bl && bl2) {
                d = this.getBlendingDataValue(getter, i - 1, j - 1, x, y, z);
            }

            if (d == Double.MAX_VALUE) {
                if (bl) {
                    d = this.getBlendingDataValue(getter, i - 1, j, x, y, z);
                }

                if (d == Double.MAX_VALUE && bl2) {
                    d = this.getBlendingDataValue(getter, i, j - 1, x, y, z);
                }
            }
        }

        return d;
    }

    private double getBlendingDataValue(Blender.CellValueGetter getter, int sectionX, int sectionZ, int x, int y, int z) {
        BlendingData blendingData = this.heightAndBiomeBlendingData.get(ChunkPos.asLong(sectionX, sectionZ));
        return blendingData != null ? getter.get(blendingData, x - QuartPos.fromSection(sectionX), y, z - QuartPos.fromSection(sectionZ)) : Double.MAX_VALUE;
    }

    public BiomeResolver getBiomeResolver(BiomeResolver resolver) {
        return (x, y, z, sampler) -> {
            Holder<Biome> holder = this.blendBiome(x, y, z);
            return holder == null ? resolver.getNoiseBiome(x, y, z, sampler) : holder;
        };
    }

    @Nullable
    private Holder<Biome> blendBiome(int x, int y, int z) {
        MutableDouble mutableDouble = new MutableDouble(Double.POSITIVE_INFINITY);
        MutableObject<Holder<Biome>> mutableObject = new MutableObject<>();
        this.heightAndBiomeBlendingData
            .forEach(
                (long_, blendingData) -> blendingData.iterateBiomes(
                    QuartPos.fromSection(ChunkPos.getX(long_)), y, QuartPos.fromSection(ChunkPos.getZ(long_)), (kx, l, holder) -> {
                        double dx = Mth.length(x - kx, z - l);
                        if (!(dx > HEIGHT_BLENDING_RANGE_CELLS)) {
                            if (dx < mutableDouble.doubleValue()) {
                                mutableObject.setValue(holder);
                                mutableDouble.setValue(dx);
                            }
                        }
                    }
                )
            );
        if (mutableDouble.doubleValue() == Double.POSITIVE_INFINITY) {
            return null;
        }

        double d = SHIFT_NOISE.getValue(x, 0.0, z) * 12.0;
        double e = Mth.clamp((mutableDouble.doubleValue() + d) / (HEIGHT_BLENDING_RANGE_CELLS + 1), 0.0, 1.0);
        return e > 0.5 ? null : mutableObject.getValue();
    }

    public static void generateBorderTicks(WorldGenRegion region, ChunkAccess chunk) {
        ChunkPos chunkPos = chunk.getPos();
        boolean bl = chunk.isOldNoiseGeneration();
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
        BlockPos blockPos = new BlockPos(chunkPos.getMinBlockX(), 0, chunkPos.getMinBlockZ());
        BlendingData blendingData = chunk.getBlendingData();
        if (blendingData != null) {
            int i = blendingData.getAreaWithOldGeneration().getMinBuildHeight();
            int j = blendingData.getAreaWithOldGeneration().getMaxBuildHeight() - 1;
            if (bl) {
                for (int k = 0; k < 16; k++) {
                    for (int l = 0; l < 16; l++) {
                        generateBorderTick(chunk, mutableBlockPos.setWithOffset(blockPos, k, i - 1, l));
                        generateBorderTick(chunk, mutableBlockPos.setWithOffset(blockPos, k, i, l));
                        generateBorderTick(chunk, mutableBlockPos.setWithOffset(blockPos, k, j, l));
                        generateBorderTick(chunk, mutableBlockPos.setWithOffset(blockPos, k, j + 1, l));
                    }
                }
            }

            for (Direction direction : Direction.Plane.HORIZONTAL) {
                if (region.getChunk(chunkPos.x + direction.getStepX(), chunkPos.z + direction.getStepZ()).isOldNoiseGeneration() != bl) {
                    int m = direction == Direction.EAST ? 15 : 0;
                    int n = direction == Direction.WEST ? 0 : 15;
                    int o = direction == Direction.SOUTH ? 15 : 0;
                    int p = direction == Direction.NORTH ? 0 : 15;

                    for (int q = m; q <= n; q++) {
                        for (int r = o; r <= p; r++) {
                            int s = Math.min(j, chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, q, r)) + 1;

                            for (int t = i; t < s; t++) {
                                generateBorderTick(chunk, mutableBlockPos.setWithOffset(blockPos, q, t, r));
                            }
                        }
                    }
                }
            }
        }
    }

    private static void generateBorderTick(ChunkAccess chunk, BlockPos pos) {
        BlockState blockState = chunk.getBlockState(pos);
        if (blockState.is(BlockTags.LEAVES)) {
            chunk.markPosForPostprocessing(pos);
        }

        FluidState fluidState = chunk.getFluidState(pos);
        if (!fluidState.isEmpty()) {
            chunk.markPosForPostprocessing(pos);
        }
    }

    public static void addAroundOldChunksCarvingMaskFilter(WorldGenLevel level, ProtoChunk chunk) {
        ChunkPos chunkPos = chunk.getPos();
        Builder<Direction8, BlendingData> builder = ImmutableMap.builder();

        for (Direction8 direction8 : Direction8.values()) {
            int i = chunkPos.x + direction8.getStepX();
            int j = chunkPos.z + direction8.getStepZ();
            BlendingData blendingData = level.getChunk(i, j).getBlendingData();
            if (blendingData != null) {
                builder.put(direction8, blendingData);
            }
        }

        ImmutableMap<Direction8, BlendingData> immutableMap = builder.build();
        if (chunk.isOldNoiseGeneration() || !immutableMap.isEmpty()) {
            Blender.DistanceGetter distanceGetter = makeOldChunkDistanceGetter(chunk.getBlendingData(), immutableMap);
            CarvingMask.Mask mask = (ix, jx, k) -> {
                double d = ix + 0.5 + SHIFT_NOISE.getValue(ix, jx, k) * 4.0;
                double e = jx + 0.5 + SHIFT_NOISE.getValue(jx, k, ix) * 4.0;
                double f = k + 0.5 + SHIFT_NOISE.getValue(k, ix, jx) * 4.0;
                return distanceGetter.getDistance(d, e, f) < 4.0;
            };
            Stream.of(GenerationStep.Carving.values()).map(chunk::getOrCreateCarvingMask).forEach(carvingMask -> carvingMask.setAdditionalMask(mask));
        }
    }

    public static Blender.DistanceGetter makeOldChunkDistanceGetter(@Nullable BlendingData blendingData, Map<Direction8, BlendingData> surroundingBlendingData) {
        List<Blender.DistanceGetter> list = Lists.newArrayList();
        if (blendingData != null) {
            list.add(makeOffsetOldChunkDistanceGetter(null, blendingData));
        }

        surroundingBlendingData.forEach((direction8, blendingDatax) -> list.add(makeOffsetOldChunkDistanceGetter(direction8, blendingDatax)));
        return (d, e, f) -> {
            double g = Double.POSITIVE_INFINITY;

            for (Blender.DistanceGetter distanceGetter : list) {
                double h = distanceGetter.getDistance(d, e, f);
                if (h < g) {
                    g = h;
                }
            }

            return g;
        };
    }

    private static Blender.DistanceGetter makeOffsetOldChunkDistanceGetter(@Nullable Direction8 direction, BlendingData blendingData) {
        double d = 0.0;
        double e = 0.0;
        if (direction != null) {
            for (Direction direction2 : direction.getDirections()) {
                d += direction2.getStepX() * 16;
                e += direction2.getStepZ() * 16;
            }
        }

        double f = d;
        double g = e;
        double h = blendingData.getAreaWithOldGeneration().getHeight() / 2.0;
        double i = blendingData.getAreaWithOldGeneration().getMinBuildHeight() + h;
        return (hx, ix, j) -> distanceToCube(hx - 8.0 - f, ix - i, j - 8.0 - g, 8.0, h, 8.0);
    }

    private static double distanceToCube(double x1, double y1, double z1, double x2, double y2, double z2) {
        double d = Math.abs(x1) - x2;
        double e = Math.abs(y1) - y2;
        double f = Math.abs(z1) - z2;
        return Mth.length(Math.max(0.0, d), Math.max(0.0, e), Math.max(0.0, f));
    }

    public record BlendingOutput(double alpha, double blendingOffset) {
    }

    interface CellValueGetter {
        double get(BlendingData var1, int var2, int var3, int var4);
    }

    public interface DistanceGetter {
        double getDistance(double var1, double var3, double var5);
    }
}
