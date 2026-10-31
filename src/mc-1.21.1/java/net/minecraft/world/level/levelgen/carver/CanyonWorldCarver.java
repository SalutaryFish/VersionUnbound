package net.minecraft.world.level.levelgen.carver;

import com.mojang.serialization.Codec;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Aquifer;

/**
 * A carver responsible for creating ravines, or canyons.
 */
public class CanyonWorldCarver extends WorldCarver<CanyonCarverConfiguration> {
    public CanyonWorldCarver(Codec<CanyonCarverConfiguration> codec) {
        super(codec);
    }

    public boolean isStartChunk(CanyonCarverConfiguration config, RandomSource random) {
        return random.nextFloat() <= config.probability;
    }

    /**
     * Carves the given chunk with caves that originate from the given {@code chunkPos}.
     * This method is invoked 289 times in order to generate each chunk (once for every position in an 8 chunk radius, or 17x17 chunk area, centered around the target chunk).
     *
     * @see net.minecraft.world.level.chunk.ChunkGenerator#applyCarvers
     *
     * @param chunk    The chunk to be carved
     * @param chunkPos The chunk position this carver is being called from
     */
    public boolean carve(
        CarvingContext context,
        CanyonCarverConfiguration config,
        ChunkAccess chunk,
        Function<BlockPos, Holder<Biome>> biomeAccessor,
        RandomSource random,
        Aquifer aquifer,
        ChunkPos chunkPos,
        CarvingMask carvingMask
    ) {
        int i = (this.getRange() * 2 - 1) * 16;
        double d = chunkPos.getBlockX(random.nextInt(16));
        int j = config.y.sample(random, context);
        double e = chunkPos.getBlockZ(random.nextInt(16));
        float f = random.nextFloat() * (float) (Math.PI * 2);
        float g = config.verticalRotation.sample(random);
        double h = config.yScale.sample(random);
        float k = config.shape.thickness.sample(random);
        int l = (int)(i * config.shape.distanceFactor.sample(random));
        int m = 0;
        this.doCarve(context, config, chunk, biomeAccessor, random.nextLong(), aquifer, d, j, e, k, f, g, 0, l, h, carvingMask);
        return true;
    }

    private void doCarve(
        CarvingContext context,
        CanyonCarverConfiguration config,
        ChunkAccess chunk,
        Function<BlockPos, Holder<Biome>> biomeAccessor,
        long seed,
        Aquifer aquifer,
        double x,
        double y,
        double z,
        float thickness,
        float yaw,
        float pitch,
        int branchIndex,
        int branchCount,
        double horizontalVerticalRatio,
        CarvingMask carvingMask
    ) {
        RandomSource randomSource = RandomSource.create(seed);
        float[] fs = this.initWidthFactors(context, config, randomSource);
        float f = 0.0F;
        float g = 0.0F;

        for (int i = branchIndex; i < branchCount; i++) {
            double d = 1.5 + Mth.sin(i * (float) Math.PI / branchCount) * thickness;
            double e = d * horizontalVerticalRatio;
            d *= config.shape.horizontalRadiusFactor.sample(randomSource);
            e = this.updateVerticalRadius(config, randomSource, e, branchCount, i);
            float h = Mth.cos(pitch);
            float j = Mth.sin(pitch);
            x += Mth.cos(yaw) * h;
            y += j;
            z += Mth.sin(yaw) * h;
            pitch *= 0.7F;
            pitch += g * 0.05F;
            yaw += f * 0.05F;
            g *= 0.8F;
            f *= 0.5F;
            g += (randomSource.nextFloat() - randomSource.nextFloat()) * randomSource.nextFloat() * 2.0F;
            f += (randomSource.nextFloat() - randomSource.nextFloat()) * randomSource.nextFloat() * 4.0F;
            if (randomSource.nextInt(4) != 0) {
                if (!canReach(chunk.getPos(), x, z, i, branchCount, thickness)) {
                    return;
                }

                this.carveEllipsoid(
                    context,
                    config,
                    chunk,
                    biomeAccessor,
                    aquifer,
                    x,
                    y,
                    z,
                    d,
                    e,
                    carvingMask,
                    (skipContext, relativeX, relativeY, relativeZ, skipY) -> this.shouldSkip(skipContext, fs, relativeX, relativeY, relativeZ, skipY)
                );
            }
        }
    }

    /**
     * Generates a random array full of width factors which are used to create the uneven walls of a ravine.
     * @return An array of length {@code context.getGenDepth()}, populated with values between 1.0 and 2.0 inclusive.
     */
    private float[] initWidthFactors(CarvingContext context, CanyonCarverConfiguration config, RandomSource random) {
        int i = context.getGenDepth();
        float[] fs = new float[i];
        float f = 1.0F;

        for (int j = 0; j < i; j++) {
            if (j == 0 || random.nextInt(config.shape.widthSmoothness) == 0) {
                f = 1.0F + random.nextFloat() * random.nextFloat();
            }

            fs[j] = f * f;
        }

        return fs;
    }

    private double updateVerticalRadius(CanyonCarverConfiguration config, RandomSource random, double verticalRadius, float branchCount, float currentBranch) {
        float f = 1.0F - Mth.abs(0.5F - currentBranch / branchCount) * 2.0F;
        float g = config.shape.verticalRadiusDefaultFactor + config.shape.verticalRadiusCenterFactor * f;
        return g * verticalRadius * Mth.randomBetween(random, 0.75F, 1.0F);
    }

    private boolean shouldSkip(CarvingContext context, float[] widthFactors, double relativeX, double relativeY, double relativeZ, int y) {
        int i = y - context.getMinGenY();
        return (relativeX * relativeX + relativeZ * relativeZ) * widthFactors[i - 1] + relativeY * relativeY / 6.0 >= 1.0;
    }
}
