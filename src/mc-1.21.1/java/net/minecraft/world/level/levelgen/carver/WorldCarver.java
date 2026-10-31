package net.minecraft.world.level.levelgen.carver;

import com.google.common.collect.ImmutableSet;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import java.util.Set;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.apache.commons.lang3.mutable.MutableBoolean;

public abstract class WorldCarver<C extends CarverConfiguration> {
    public static final WorldCarver<CaveCarverConfiguration> CAVE = register("cave", new CaveWorldCarver(CaveCarverConfiguration.CODEC));
    public static final WorldCarver<CaveCarverConfiguration> NETHER_CAVE = register("nether_cave", new NetherWorldCarver(CaveCarverConfiguration.CODEC));
    public static final WorldCarver<CanyonCarverConfiguration> CANYON = register("canyon", new CanyonWorldCarver(CanyonCarverConfiguration.CODEC));
    protected static final BlockState AIR = Blocks.AIR.defaultBlockState();
    protected static final BlockState CAVE_AIR = Blocks.CAVE_AIR.defaultBlockState();
    protected static final FluidState WATER = Fluids.WATER.defaultFluidState();
    protected static final FluidState LAVA = Fluids.LAVA.defaultFluidState();
    protected Set<Fluid> liquids = ImmutableSet.of(Fluids.WATER);
    private final MapCodec<ConfiguredWorldCarver<C>> configuredCodec;

    private static <C extends CarverConfiguration, F extends WorldCarver<C>> F register(String key, F carver) {
        return Registry.register(BuiltInRegistries.CARVER, key, carver);
    }

    public WorldCarver(Codec<C> codec) {
        this.configuredCodec = codec.fieldOf("config").xmap(this::configured, ConfiguredWorldCarver::config);
    }

    public ConfiguredWorldCarver<C> configured(C config) {
        return new ConfiguredWorldCarver<>(this, config);
    }

    public MapCodec<ConfiguredWorldCarver<C>> configuredCodec() {
        return this.configuredCodec;
    }

    public int getRange() {
        return 4;
    }

    /**
     * Carves blocks in an ellipsoid (more accurately a spheroid), defined by a center (x, y, z) position, with a horizontal and vertical radius (the semi-axes)
     *
     * @param skipChecker Used to skip certain blocks within the carved region.
     */
    protected boolean carveEllipsoid(
        CarvingContext context,
        C config,
        ChunkAccess chunk,
        Function<BlockPos, Holder<Biome>> biomeAccessor,
        Aquifer aquifer,
        double x,
        double y,
        double z,
        double horizontalRadius,
        double verticalRadius,
        CarvingMask carvingMask,
        WorldCarver.CarveSkipChecker skipChecker
    ) {
        ChunkPos chunkPos = chunk.getPos();
        double d = chunkPos.getMiddleBlockX();
        double e = chunkPos.getMiddleBlockZ();
        double f = 16.0 + horizontalRadius * 2.0;
        if (!(Math.abs(x - d) > f) && !(Math.abs(z - e) > f)) {
            int i = chunkPos.getMinBlockX();
            int j = chunkPos.getMinBlockZ();
            int k = Math.max(Mth.floor(x - horizontalRadius) - i - 1, 0);
            int l = Math.min(Mth.floor(x + horizontalRadius) - i, 15);
            int m = Math.max(Mth.floor(y - verticalRadius) - 1, context.getMinGenY() + 1);
            int n = chunk.isUpgrading() ? 0 : 7;
            int o = Math.min(Mth.floor(y + verticalRadius) + 1, context.getMinGenY() + context.getGenDepth() - 1 - n);
            int p = Math.max(Mth.floor(z - horizontalRadius) - j - 1, 0);
            int q = Math.min(Mth.floor(z + horizontalRadius) - j, 15);
            boolean bl = false;
            BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
            BlockPos.MutableBlockPos mutableBlockPos2 = new BlockPos.MutableBlockPos();

            for (int r = k; r <= l; r++) {
                int s = chunkPos.getBlockX(r);
                double g = (s + 0.5 - x) / horizontalRadius;

                for (int t = p; t <= q; t++) {
                    int u = chunkPos.getBlockZ(t);
                    double h = (u + 0.5 - z) / horizontalRadius;
                    if (!(g * g + h * h >= 1.0)) {
                        MutableBoolean mutableBoolean = new MutableBoolean(false);

                        for (int v = o; v > m; v--) {
                            double w = (v - 0.5 - y) / verticalRadius;
                            if (!skipChecker.shouldSkip(context, g, w, h, v) && (!carvingMask.get(r, v, t) || isDebugEnabled(config))) {
                                carvingMask.set(r, v, t);
                                mutableBlockPos.set(s, v, u);
                                bl |= this.carveBlock(
                                    context, config, chunk, biomeAccessor, carvingMask, mutableBlockPos, mutableBlockPos2, aquifer, mutableBoolean
                                );
                            }
                        }
                    }
                }
            }

            return bl;
        } else {
            return false;
        }
    }

    /**
     * Carves a single block, replacing it with the appropriate state if possible, and handles replacing exposed dirt with grass.
     *
     * @param pos            The position to carve at. The method does not mutate this
     *                       position.
     * @param checkPos       An additional mutable block position object to be used
     *                       and modified by the method
     * @param reachedSurface Set to true if the block carved was the surface, which is
     *                       checked as being either grass or mycelium
     */
    protected boolean carveBlock(
        CarvingContext context,
        C config,
        ChunkAccess chunk,
        Function<BlockPos, Holder<Biome>> biomeGetter,
        CarvingMask carvingMask,
        BlockPos.MutableBlockPos pos,
        BlockPos.MutableBlockPos checkPos,
        Aquifer aquifer,
        MutableBoolean reachedSurface
    ) {
        BlockState blockState = chunk.getBlockState(pos);
        if (blockState.is(Blocks.GRASS_BLOCK) || blockState.is(Blocks.MYCELIUM)) {
            reachedSurface.setTrue();
        }

        if (!this.canReplaceBlock(config, blockState) && !isDebugEnabled(config)) {
            return false;
        }

        BlockState blockState2 = this.getCarveState(context, config, pos, aquifer);
        if (blockState2 == null) {
            return false;
        }

        chunk.setBlockState(pos, blockState2, false);
        if (aquifer.shouldScheduleFluidUpdate() && !blockState2.getFluidState().isEmpty()) {
            chunk.markPosForPostprocessing(pos);
        }

        if (reachedSurface.isTrue()) {
            checkPos.setWithOffset(pos, Direction.DOWN);
            if (chunk.getBlockState(checkPos).is(Blocks.DIRT)) {
                context.topMaterial(biomeGetter, chunk, checkPos, !blockState2.getFluidState().isEmpty()).ifPresent(blockStatex -> {
                    chunk.setBlockState(checkPos, blockStatex, false);
                    if (!blockStatex.getFluidState().isEmpty()) {
                        chunk.markPosForPostprocessing(checkPos);
                    }
                });
            }
        }

        return true;
    }

    @Nullable
    private BlockState getCarveState(CarvingContext context, C config, BlockPos pos, Aquifer aquifer) {
        if (pos.getY() <= config.lavaLevel.resolveY(context)) {
            return LAVA.createLegacyBlock();
        } else {
            BlockState blockState = aquifer.computeSubstance(new DensityFunction.SinglePointContext(pos.getX(), pos.getY(), pos.getZ()), 0.0);
            if (blockState == null) {
                return isDebugEnabled(config) ? config.debugSettings.getBarrierState() : null;
            } else {
                return isDebugEnabled(config) ? getDebugState(config, blockState) : blockState;
            }
        }
    }

    private static BlockState getDebugState(CarverConfiguration config, BlockState state) {
        if (state.is(Blocks.AIR)) {
            return config.debugSettings.getAirState();
        } else if (state.is(Blocks.WATER)) {
            BlockState blockState = config.debugSettings.getWaterState();
            return blockState.hasProperty(BlockStateProperties.WATERLOGGED) ? blockState.setValue(BlockStateProperties.WATERLOGGED, true) : blockState;
        } else {
            return state.is(Blocks.LAVA) ? config.debugSettings.getLavaState() : state;
        }
    }

    /**
     * Carves the given chunk with caves that originate from the given {@code chunkPos}.
     * This method is invoked 289 times in order to generate each chunk (once for every position in an 8 chunk radius, or 17x17 chunk area, centered around the target chunk).
     *
     * @see net.minecraft.world.level.chunk.ChunkGenerator#applyCarvers
     *
     * @param var3 The chunk to be carved
     * @param var7 The chunk position this carver is being called from
     */
    public abstract boolean carve(
        CarvingContext var1, C var2, ChunkAccess var3, Function<BlockPos, Holder<Biome>> var4, RandomSource var5, Aquifer var6, ChunkPos var7, CarvingMask var8
    );

    public abstract boolean isStartChunk(C var1, RandomSource var2);

    protected boolean canReplaceBlock(C config, BlockState state) {
        return state.is(config.replaceable);
    }

    protected static boolean canReach(ChunkPos chunkPos, double x, double z, int branchIndex, int branchCount, float width) {
        double d = chunkPos.getMiddleBlockX();
        double e = chunkPos.getMiddleBlockZ();
        double f = x - d;
        double g = z - e;
        double h = branchCount - branchIndex;
        double i = width + 2.0F + 16.0F;
        return f * f + g * g - h * h <= i * i;
    }

    private static boolean isDebugEnabled(CarverConfiguration config) {
        return config.debugSettings.isDebugMode();
    }

    /**
     * Used to define certain positions to skip or ignore when carving.
     */
    public interface CarveSkipChecker {
        boolean shouldSkip(CarvingContext var1, double var2, double var4, double var6, int var8);
    }
}
