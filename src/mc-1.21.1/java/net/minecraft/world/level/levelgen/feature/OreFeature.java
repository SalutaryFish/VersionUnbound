package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import java.util.BitSet;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.BulkSectionAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;

public class OreFeature extends Feature<OreConfiguration> {
    public OreFeature(Codec<OreConfiguration> codec) {
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
        RandomSource randomSource = context.random();
        BlockPos blockPos = context.origin();
        WorldGenLevel worldGenLevel = context.level();
        OreConfiguration oreConfiguration = context.config();
        float f = randomSource.nextFloat() * (float) Math.PI;
        float g = oreConfiguration.size / 8.0F;
        int i = Mth.ceil((oreConfiguration.size / 16.0F * 2.0F + 1.0F) / 2.0F);
        double d = blockPos.getX() + Math.sin(f) * g;
        double e = blockPos.getX() - Math.sin(f) * g;
        double h = blockPos.getZ() + Math.cos(f) * g;
        double j = blockPos.getZ() - Math.cos(f) * g;
        int k = 2;
        double l = blockPos.getY() + randomSource.nextInt(3) - 2;
        double m = blockPos.getY() + randomSource.nextInt(3) - 2;
        int n = blockPos.getX() - Mth.ceil(g) - i;
        int o = blockPos.getY() - 2 - i;
        int p = blockPos.getZ() - Mth.ceil(g) - i;
        int q = 2 * (Mth.ceil(g) + i);
        int r = 2 * (2 + i);

        for (int s = n; s <= n + q; s++) {
            for (int t = p; t <= p + q; t++) {
                if (o <= worldGenLevel.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, s, t)) {
                    return this.doPlace(worldGenLevel, randomSource, oreConfiguration, d, e, h, j, l, m, n, o, p, q, r);
                }
            }
        }

        return false;
    }

    protected boolean doPlace(
        WorldGenLevel level,
        RandomSource random,
        OreConfiguration config,
        double minX,
        double maxX,
        double minZ,
        double maxZ,
        double minY,
        double maxY,
        int x,
        int y,
        int z,
        int width,
        int height
    ) {
        int i = 0;
        BitSet bitSet = new BitSet(width * height * width);
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
        int j = config.size;
        double[] ds = new double[j * 4];

        for (int k = 0; k < j; k++) {
            float f = (float)k / j;
            double d = Mth.lerp(f, minX, maxX);
            double e = Mth.lerp(f, minY, maxY);
            double g = Mth.lerp(f, minZ, maxZ);
            double h = random.nextDouble() * j / 16.0;
            double l = ((Mth.sin((float) Math.PI * f) + 1.0F) * h + 1.0) / 2.0;
            ds[k * 4 + 0] = d;
            ds[k * 4 + 1] = e;
            ds[k * 4 + 2] = g;
            ds[k * 4 + 3] = l;
        }

        for (int k = 0; k < j - 1; k++) {
            if (!(ds[k * 4 + 3] <= 0.0)) {
                for (int m = k + 1; m < j; m++) {
                    if (!(ds[m * 4 + 3] <= 0.0)) {
                        double d = ds[k * 4 + 0] - ds[m * 4 + 0];
                        double e = ds[k * 4 + 1] - ds[m * 4 + 1];
                        double g = ds[k * 4 + 2] - ds[m * 4 + 2];
                        double h = ds[k * 4 + 3] - ds[m * 4 + 3];
                        if (h * h > d * d + e * e + g * g) {
                            if (h > 0.0) {
                                ds[m * 4 + 3] = -1.0;
                            } else {
                                ds[k * 4 + 3] = -1.0;
                            }
                        }
                    }
                }
            }
        }

        try (BulkSectionAccess bulkSectionAccess = new BulkSectionAccess(level)) {
            for (int m = 0; m < j; m++) {
                double d = ds[m * 4 + 3];
                if (!(d < 0.0)) {
                    double e = ds[m * 4 + 0];
                    double g = ds[m * 4 + 1];
                    double h = ds[m * 4 + 2];
                    int n = Math.max(Mth.floor(e - d), x);
                    int o = Math.max(Mth.floor(g - d), y);
                    int p = Math.max(Mth.floor(h - d), z);
                    int q = Math.max(Mth.floor(e + d), n);
                    int r = Math.max(Mth.floor(g + d), o);
                    int s = Math.max(Mth.floor(h + d), p);

                    for (int t = n; t <= q; t++) {
                        double u = (t + 0.5 - e) / d;
                        if (u * u < 1.0) {
                            for (int v = o; v <= r; v++) {
                                double w = (v + 0.5 - g) / d;
                                if (u * u + w * w < 1.0) {
                                    for (int aa = p; aa <= s; aa++) {
                                        double ab = (aa + 0.5 - h) / d;
                                        if (u * u + w * w + ab * ab < 1.0 && !level.isOutsideBuildHeight(v)) {
                                            int ac = t - x + (v - y) * width + (aa - z) * width * height;
                                            if (!bitSet.get(ac)) {
                                                bitSet.set(ac);
                                                mutableBlockPos.set(t, v, aa);
                                                if (level.ensureCanWrite(mutableBlockPos)) {
                                                    LevelChunkSection levelChunkSection = bulkSectionAccess.getSection(mutableBlockPos);
                                                    if (levelChunkSection != null) {
                                                        int ad = SectionPos.sectionRelative(t);
                                                        int ae = SectionPos.sectionRelative(v);
                                                        int af = SectionPos.sectionRelative(aa);
                                                        BlockState blockState = levelChunkSection.getBlockState(ad, ae, af);

                                                        for (OreConfiguration.TargetBlockState targetBlockState : config.targetStates) {
                                                            if (canPlaceOre(
                                                                blockState, bulkSectionAccess::getBlockState, random, config, targetBlockState, mutableBlockPos
                                                            )) {
                                                                levelChunkSection.setBlockState(ad, ae, af, targetBlockState.state, false);
                                                                i++;
                                                                break;
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return i > 0;
    }

    public static boolean canPlaceOre(
        BlockState state,
        Function<BlockPos, BlockState> adjacentStateAccessor,
        RandomSource random,
        OreConfiguration config,
        OreConfiguration.TargetBlockState targetState,
        BlockPos.MutableBlockPos mutablePos
    ) {
        if (!targetState.target.test(state, random)) {
            return false;
        } else {
            return shouldSkipAirCheck(random, config.discardChanceOnAirExposure) ? true : !isAdjacentToAir(adjacentStateAccessor, mutablePos);
        }
    }

    protected static boolean shouldSkipAirCheck(RandomSource random, float chance) {
        if (chance <= 0.0F) {
            return true;
        } else {
            return chance >= 1.0F ? false : random.nextFloat() >= chance;
        }
    }
}
