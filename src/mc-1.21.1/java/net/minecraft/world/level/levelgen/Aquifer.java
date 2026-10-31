package net.minecraft.world.level.levelgen;

import java.util.Arrays;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.OverworldBiomeBuilder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;
import org.apache.commons.lang3.mutable.MutableDouble;

/**
 * Aquifers are responsible for non-sea level fluids found in terrain generation, but also managing that different aquifers don't intersect with each other in ways that would create undesirable fluid placement.
 * The aquifer interface itself is a modifier on a per-block basis. It computes a block state to be placed for each position in the world.
 * <p>
 * Aquifers work by first partitioning a single chunk into a low resolution grid. They then generate, via various noise layers, an {@link NoiseBasedAquifer.AquiferStatus} at each grid point.
 * At each point, the grid cell containing that point is calculated, and then of the eight grid corners, the three closest aquifers are found, by square euclidean distance.
 * Borders between aquifers are created by comparing nearby aquifers to see if the given point is near-equidistant from them, indicating a border if so, or fluid/air depending on the aquifer height if not.
 */
public interface Aquifer {
    /**
     * Creates a standard noise based aquifer. This aquifer will place liquid (both water and lava), air, and stone as described above.
     */
    static Aquifer create(
        NoiseChunk chunk,
        ChunkPos chunkPos,
        NoiseRouter noiseRouter,
        PositionalRandomFactory positionalRandomFactory,
        int minY,
        int height,
        Aquifer.FluidPicker globalFluidPicker
    ) {
        return new Aquifer.NoiseBasedAquifer(chunk, chunkPos, noiseRouter, positionalRandomFactory, minY, height, globalFluidPicker);
    }

    /**
     * Creates a disabled, or no-op aquifer. This will fill any open areas below sea level with the default fluid.
     */
    static Aquifer createDisabled(Aquifer.FluidPicker defaultFluid) {
        return new Aquifer() {
            @Nullable
            @Override
            public BlockState computeSubstance(DensityFunction.FunctionContext functionContext, double d) {
                return d > 0.0
                    ? null
                    : defaultFluid.computeFluid(functionContext.blockX(), functionContext.blockY(), functionContext.blockZ()).at(functionContext.blockY());
            }

            @Override
            public boolean shouldScheduleFluidUpdate() {
                return false;
            }
        };
    }

    @Nullable
    BlockState computeSubstance(DensityFunction.FunctionContext var1, double var2);

    boolean shouldScheduleFluidUpdate();

    interface FluidPicker {
        Aquifer.FluidStatus computeFluid(int var1, int var2, int var3);
    }

    final class FluidStatus {
        /**
         * The y height of the aquifer.
         */
        final int fluidLevel;
        /**
         * The fluid state the aquifer is filled with.
         */
        final BlockState fluidType;

        public FluidStatus(int fluidLevel, BlockState fluidType) {
            this.fluidLevel = fluidLevel;
            this.fluidType = fluidType;
        }

        public BlockState at(int y) {
            return y < this.fluidLevel ? this.fluidType : Blocks.AIR.defaultBlockState();
        }
    }

    class NoiseBasedAquifer implements Aquifer {
        private static final int X_RANGE = 10;
        private static final int Y_RANGE = 9;
        private static final int Z_RANGE = 10;
        private static final int X_SEPARATION = 6;
        private static final int Y_SEPARATION = 3;
        private static final int Z_SEPARATION = 6;
        private static final int X_SPACING = 16;
        private static final int Y_SPACING = 12;
        private static final int Z_SPACING = 16;
        private static final int MAX_REASONABLE_DISTANCE_TO_AQUIFER_CENTER = 11;
        private static final double FLOWING_UPDATE_SIMULARITY = similarity(Mth.square(10), Mth.square(12));
        private final NoiseChunk noiseChunk;
        private final DensityFunction barrierNoise;
        private final DensityFunction fluidLevelFloodednessNoise;
        private final DensityFunction fluidLevelSpreadNoise;
        private final DensityFunction lavaNoise;
        private final PositionalRandomFactory positionalRandomFactory;
        private final Aquifer.FluidStatus[] aquiferCache;
        private final long[] aquiferLocationCache;
        private final Aquifer.FluidPicker globalFluidPicker;
        private final DensityFunction erosion;
        private final DensityFunction depth;
        private boolean shouldScheduleFluidUpdate;
        private final int minGridX;
        private final int minGridY;
        private final int minGridZ;
        private final int gridSizeX;
        private final int gridSizeZ;
        private static final int[][] SURFACE_SAMPLING_OFFSETS_IN_CHUNKS = new int[][]{
            {0, 0}, {-2, -1}, {-1, -1}, {0, -1}, {1, -1}, {-3, 0}, {-2, 0}, {-1, 0}, {1, 0}, {-2, 1}, {-1, 1}, {0, 1}, {1, 1}
        };

        NoiseBasedAquifer(
            NoiseChunk noiseChunk,
            ChunkPos chunkPos,
            NoiseRouter noiseRouter,
            PositionalRandomFactory positionalRandomFactory,
            int minY,
            int height,
            Aquifer.FluidPicker globalFluidPicker
        ) {
            this.noiseChunk = noiseChunk;
            this.barrierNoise = noiseRouter.barrierNoise();
            this.fluidLevelFloodednessNoise = noiseRouter.fluidLevelFloodednessNoise();
            this.fluidLevelSpreadNoise = noiseRouter.fluidLevelSpreadNoise();
            this.lavaNoise = noiseRouter.lavaNoise();
            this.erosion = noiseRouter.erosion();
            this.depth = noiseRouter.depth();
            this.positionalRandomFactory = positionalRandomFactory;
            this.minGridX = this.gridX(chunkPos.getMinBlockX()) - 1;
            this.globalFluidPicker = globalFluidPicker;
            int i = this.gridX(chunkPos.getMaxBlockX()) + 1;
            this.gridSizeX = i - this.minGridX + 1;
            this.minGridY = this.gridY(minY) - 1;
            int j = this.gridY(minY + height) + 1;
            int k = j - this.minGridY + 1;
            this.minGridZ = this.gridZ(chunkPos.getMinBlockZ()) - 1;
            int l = this.gridZ(chunkPos.getMaxBlockZ()) + 1;
            this.gridSizeZ = l - this.minGridZ + 1;
            int m = this.gridSizeX * k * this.gridSizeZ;
            this.aquiferCache = new Aquifer.FluidStatus[m];
            this.aquiferLocationCache = new long[m];
            Arrays.fill(this.aquiferLocationCache, Long.MAX_VALUE);
        }

        /**
         * @return A cache index based on grid positions.
         */
        private int getIndex(int gridX, int gridY, int gridZ) {
            int i = gridX - this.minGridX;
            int j = gridY - this.minGridY;
            int k = gridZ - this.minGridZ;
            return (j * this.gridSizeZ + k) * this.gridSizeX + i;
        }

        @Nullable
        @Override
        public BlockState computeSubstance(DensityFunction.FunctionContext context, double substance) {
            int i = context.blockX();
            int j = context.blockY();
            int k = context.blockZ();
            if (substance > 0.0) {
                this.shouldScheduleFluidUpdate = false;
                return null;
            }

            Aquifer.FluidStatus fluidStatus = this.globalFluidPicker.computeFluid(i, j, k);
            if (fluidStatus.at(j).is(Blocks.LAVA)) {
                this.shouldScheduleFluidUpdate = false;
                return Blocks.LAVA.defaultBlockState();
            }

            int l = Math.floorDiv(i - 5, 16);
            int m = Math.floorDiv(j + 1, 12);
            int n = Math.floorDiv(k - 5, 16);
            int o = Integer.MAX_VALUE;
            int p = Integer.MAX_VALUE;
            int q = Integer.MAX_VALUE;
            long r = 0L;
            long s = 0L;
            long t = 0L;

            for (int u = 0; u <= 1; u++) {
                for (int v = -1; v <= 1; v++) {
                    for (int w = 0; w <= 1; w++) {
                        int x = l + u;
                        int y = m + v;
                        int z = n + w;
                        int aa = this.getIndex(x, y, z);
                        long ab = this.aquiferLocationCache[aa];
                        long ac;
                        if (ab != Long.MAX_VALUE) {
                            ac = ab;
                        } else {
                            RandomSource randomSource = this.positionalRandomFactory.at(x, y, z);
                            ac = BlockPos.asLong(x * 16 + randomSource.nextInt(10), y * 12 + randomSource.nextInt(9), z * 16 + randomSource.nextInt(10));
                            this.aquiferLocationCache[aa] = ac;
                        }

                        int ad = BlockPos.getX(ac) - i;
                        int ae = BlockPos.getY(ac) - j;
                        int af = BlockPos.getZ(ac) - k;
                        int ag = ad * ad + ae * ae + af * af;
                        if (o >= ag) {
                            t = s;
                            s = r;
                            r = ac;
                            q = p;
                            p = o;
                            o = ag;
                        } else if (p >= ag) {
                            t = s;
                            s = ac;
                            q = p;
                            p = ag;
                        } else if (q >= ag) {
                            t = ac;
                            q = ag;
                        }
                    }
                }
            }

            Aquifer.FluidStatus fluidStatus2 = this.getAquiferStatus(r);
            double d = similarity(o, p);
            BlockState blockState = fluidStatus2.at(j);
            BlockState blockState2 = blockState;
            if (d <= 0.0) {
                this.shouldScheduleFluidUpdate = d >= FLOWING_UPDATE_SIMULARITY;
                return blockState2;
            }

            if (blockState.is(Blocks.WATER) && this.globalFluidPicker.computeFluid(i, j - 1, k).at(j - 1).is(Blocks.LAVA)) {
                this.shouldScheduleFluidUpdate = true;
                return blockState2;
            }

            MutableDouble mutableDouble = new MutableDouble(Double.NaN);
            Aquifer.FluidStatus fluidStatus3 = this.getAquiferStatus(s);
            double e = d * this.calculatePressure(context, mutableDouble, fluidStatus2, fluidStatus3);
            if (substance + e > 0.0) {
                this.shouldScheduleFluidUpdate = false;
                return null;
            }

            Aquifer.FluidStatus fluidStatus4 = this.getAquiferStatus(t);
            double f = similarity(o, q);
            if (f > 0.0) {
                double g = d * f * this.calculatePressure(context, mutableDouble, fluidStatus2, fluidStatus4);
                if (substance + g > 0.0) {
                    this.shouldScheduleFluidUpdate = false;
                    return null;
                }
            }

            double g = similarity(p, q);
            if (g > 0.0) {
                double h = d * g * this.calculatePressure(context, mutableDouble, fluidStatus3, fluidStatus4);
                if (substance + h > 0.0) {
                    this.shouldScheduleFluidUpdate = false;
                    return null;
                }
            }

            this.shouldScheduleFluidUpdate = true;
            return blockState2;
        }

        @Override
        public boolean shouldScheduleFluidUpdate() {
            return this.shouldScheduleFluidUpdate;
        }

        /**
         * Compares two distances (between aquifers).
         * @return {@code 1.0} if the distances are equal, and returns smaller values the more different in absolute value the two distances are.
         */
        private static double similarity(int firstDistance, int secondDistance) {
            double d = 25.0;
            return 1.0 - Math.abs(secondDistance - firstDistance) / 25.0;
        }

        private double calculatePressure(
            DensityFunction.FunctionContext context, MutableDouble substance, Aquifer.FluidStatus firstFluid, Aquifer.FluidStatus secondFluid
        ) {
            int i = context.blockY();
            BlockState blockState = firstFluid.at(i);
            BlockState blockState2 = secondFluid.at(i);
            if ((!blockState.is(Blocks.LAVA) || !blockState2.is(Blocks.WATER)) && (!blockState.is(Blocks.WATER) || !blockState2.is(Blocks.LAVA))) {
                int j = Math.abs(firstFluid.fluidLevel - secondFluid.fluidLevel);
                if (j == 0) {
                    return 0.0;
                }

                double d = 0.5 * (firstFluid.fluidLevel + secondFluid.fluidLevel);
                double e = i + 0.5 - d;
                double f = j / 2.0;
                double g = 0.0;
                double h = 2.5;
                double k = 1.5;
                double l = 3.0;
                double m = 10.0;
                double n = 3.0;
                double o = f - Math.abs(e);
                double q;
                if (e > 0.0) {
                    double p = 0.0 + o;
                    if (p > 0.0) {
                        q = p / 1.5;
                    } else {
                        q = p / 2.5;
                    }
                } else {
                    double p = 3.0 + o;
                    if (p > 0.0) {
                        q = p / 3.0;
                    } else {
                        q = p / 10.0;
                    }
                }

                double p = 2.0;
                double r;
                if (!(q < -2.0) && !(q > 2.0)) {
                    double s = substance.getValue();
                    if (Double.isNaN(s)) {
                        double t = this.barrierNoise.compute(context);
                        substance.setValue(t);
                        r = t;
                    } else {
                        r = s;
                    }
                } else {
                    r = 0.0;
                }

                return 2.0 * (r + q);
            } else {
                return 2.0;
            }
        }

        private int gridX(int x) {
            return Math.floorDiv(x, 16);
        }

        private int gridY(int y) {
            return Math.floorDiv(y, 12);
        }

        private int gridZ(int z) {
            return Math.floorDiv(z, 16);
        }

        /**
         * Calculates the aquifer at a given location. Internally references a cache using the grid positions as an index. If the cache is not populated, computes a new aquifer at that grid location using {@link #computeFluid}.
         *
         * @param packedPos The aquifer block position, packed into a {@code long}.
         */
        private Aquifer.FluidStatus getAquiferStatus(long packedPos) {
            int i = BlockPos.getX(packedPos);
            int j = BlockPos.getY(packedPos);
            int k = BlockPos.getZ(packedPos);
            int l = this.gridX(i);
            int m = this.gridY(j);
            int n = this.gridZ(k);
            int o = this.getIndex(l, m, n);
            Aquifer.FluidStatus fluidStatus = this.aquiferCache[o];
            if (fluidStatus != null) {
                return fluidStatus;
            }

            Aquifer.FluidStatus fluidStatus2 = this.computeFluid(i, j, k);
            this.aquiferCache[o] = fluidStatus2;
            return fluidStatus2;
        }

        private Aquifer.FluidStatus computeFluid(int x, int y, int z) {
            Aquifer.FluidStatus fluidStatus = this.globalFluidPicker.computeFluid(x, y, z);
            int i = Integer.MAX_VALUE;
            int j = y + 12;
            int k = y - 12;
            boolean bl = false;

            for (int[] is : SURFACE_SAMPLING_OFFSETS_IN_CHUNKS) {
                int l = x + SectionPos.sectionToBlockCoord(is[0]);
                int m = z + SectionPos.sectionToBlockCoord(is[1]);
                int n = this.noiseChunk.preliminarySurfaceLevel(l, m);
                int o = n + 8;
                boolean bl2 = is[0] == 0 && is[1] == 0;
                if (bl2 && k > o) {
                    return fluidStatus;
                }

                boolean bl3 = j > o;
                if (bl3 || bl2) {
                    Aquifer.FluidStatus fluidStatus2 = this.globalFluidPicker.computeFluid(l, o, m);
                    if (!fluidStatus2.at(o).isAir()) {
                        if (bl2) {
                            bl = true;
                        }

                        if (bl3) {
                            return fluidStatus2;
                        }
                    }
                }

                i = Math.min(i, n);
            }

            int p = this.computeSurfaceLevel(x, y, z, fluidStatus, i, bl);
            return new Aquifer.FluidStatus(p, this.computeFluidType(x, y, z, fluidStatus, p));
        }

        private int computeSurfaceLevel(int x, int y, int z, Aquifer.FluidStatus fluidStatus, int maxSurfaceLevel, boolean fluidPresent) {
            DensityFunction.SinglePointContext singlePointContext = new DensityFunction.SinglePointContext(x, y, z);
            double d;
            double e;
            if (OverworldBiomeBuilder.isDeepDarkRegion(this.erosion, this.depth, singlePointContext)) {
                d = -1.0;
                e = -1.0;
            } else {
                int i = maxSurfaceLevel + 8 - y;
                int j = 64;
                double f = fluidPresent ? Mth.clampedMap(i, 0.0, 64.0, 1.0, 0.0) : 0.0;
                double g = Mth.clamp(this.fluidLevelFloodednessNoise.compute(singlePointContext), -1.0, 1.0);
                double h = Mth.map(f, 1.0, 0.0, -0.3, 0.8);
                double k = Mth.map(f, 1.0, 0.0, -0.8, 0.4);
                d = g - k;
                e = g - h;
            }

            int i;
            if (e > 0.0) {
                i = fluidStatus.fluidLevel;
            } else if (d > 0.0) {
                i = this.computeRandomizedFluidSurfaceLevel(x, y, z, maxSurfaceLevel);
            } else {
                i = DimensionType.WAY_BELOW_MIN_Y;
            }

            return i;
        }

        private int computeRandomizedFluidSurfaceLevel(int x, int y, int z, int maxSurfaceLevel) {
            int i = 16;
            int j = 40;
            int k = Math.floorDiv(x, 16);
            int l = Math.floorDiv(y, 40);
            int m = Math.floorDiv(z, 16);
            int n = l * 40 + 20;
            int o = 10;
            double d = this.fluidLevelSpreadNoise.compute(new DensityFunction.SinglePointContext(k, l, m)) * 10.0;
            int p = Mth.quantize(d, 3);
            int q = n + p;
            return Math.min(maxSurfaceLevel, q);
        }

        private BlockState computeFluidType(int x, int y, int z, Aquifer.FluidStatus fluidStatus, int surfaceLevel) {
            BlockState blockState = fluidStatus.fluidType;
            if (surfaceLevel <= -10 && surfaceLevel != DimensionType.WAY_BELOW_MIN_Y && fluidStatus.fluidType != Blocks.LAVA.defaultBlockState()) {
                int i = 64;
                int j = 40;
                int k = Math.floorDiv(x, 64);
                int l = Math.floorDiv(y, 40);
                int m = Math.floorDiv(z, 64);
                double d = this.lavaNoise.compute(new DensityFunction.SinglePointContext(k, l, m));
                if (Math.abs(d) > 0.3) {
                    blockState = Blocks.LAVA.defaultBlockState();
                }
            }

            return blockState;
        }
    }
}
