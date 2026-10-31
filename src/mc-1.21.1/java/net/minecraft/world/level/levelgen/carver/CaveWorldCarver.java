package net.minecraft.world.level.levelgen.carver;

import com.mojang.serialization.Codec;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Aquifer;

/**
 * A carver which creates Minecraft's most common cave types.
 */
public class CaveWorldCarver extends WorldCarver<CaveCarverConfiguration> {
    public CaveWorldCarver(Codec<CaveCarverConfiguration> codec) {
        super(codec);
    }

    public boolean isStartChunk(CaveCarverConfiguration config, RandomSource random) {
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
        CaveCarverConfiguration config,
        ChunkAccess chunk,
        Function<BlockPos, Holder<Biome>> biomeAccessor,
        RandomSource random,
        Aquifer aquifer,
        ChunkPos chunkPos,
        CarvingMask carvingMask
    ) {
        int i = SectionPos.sectionToBlockCoord(this.getRange() * 2 - 1);
        int j = random.nextInt(random.nextInt(random.nextInt(this.getCaveBound()) + 1) + 1);

        for (int k = 0; k < j; k++) {
            double d = chunkPos.getBlockX(random.nextInt(16));
            double e = config.y.sample(random, context);
            double f = chunkPos.getBlockZ(random.nextInt(16));
            double g = config.horizontalRadiusMultiplier.sample(random);
            double h = config.verticalRadiusMultiplier.sample(random);
            double l = config.floorLevel.sample(random);
            WorldCarver.CarveSkipChecker carveSkipChecker = (skipContext, relativeX, relativeY, relativeZ, y) -> shouldSkip(relativeX, relativeY, relativeZ, l);
            int m = 1;
            if (random.nextInt(4) == 0) {
                double n = config.yScale.sample(random);
                float o = 1.0F + random.nextFloat() * 6.0F;
                this.createRoom(context, config, chunk, biomeAccessor, aquifer, d, e, f, o, n, carvingMask, carveSkipChecker);
                m += random.nextInt(4);
            }

            for (int p = 0; p < m; p++) {
                float q = random.nextFloat() * (float) (Math.PI * 2);
                float o = (random.nextFloat() - 0.5F) / 4.0F;
                float r = this.getThickness(random);
                int s = i - random.nextInt(i / 4);
                int t = 0;
                this.createTunnel(
                    context,
                    config,
                    chunk,
                    biomeAccessor,
                    random.nextLong(),
                    aquifer,
                    d,
                    e,
                    f,
                    g,
                    h,
                    r,
                    q,
                    o,
                    0,
                    s,
                    this.getYScale(),
                    carvingMask,
                    carveSkipChecker
                );
            }
        }

        return true;
    }

    protected int getCaveBound() {
        return 15;
    }

    protected float getThickness(RandomSource random) {
        float f = random.nextFloat() * 2.0F + random.nextFloat();
        if (random.nextInt(10) == 0) {
            f *= random.nextFloat() * random.nextFloat() * 3.0F + 1.0F;
        }

        return f;
    }

    protected double getYScale() {
        return 1.0;
    }

    protected void createRoom(
        CarvingContext context,
        CaveCarverConfiguration config,
        ChunkAccess chunk,
        Function<BlockPos, Holder<Biome>> biomeAccessor,
        Aquifer aquifer,
        double x,
        double y,
        double z,
        float radius,
        double horizontalVerticalRatio,
        CarvingMask carvingMask,
        WorldCarver.CarveSkipChecker skipChecker
    ) {
        double d = 1.5 + Mth.sin((float) (Math.PI / 2)) * radius;
        double e = d * horizontalVerticalRatio;
        this.carveEllipsoid(context, config, chunk, biomeAccessor, aquifer, x + 1.0, y, z, d, e, carvingMask, skipChecker);
    }

    protected void createTunnel(
        CarvingContext context,
        CaveCarverConfiguration config,
        ChunkAccess chunk,
        Function<BlockPos, Holder<Biome>> biomeAccessor,
        long seed,
        Aquifer aquifer,
        double x,
        double y,
        double z,
        double horizontalRadiusMultiplier,
        double verticalRadiusMultiplier,
        float thickness,
        float yaw,
        float pitch,
        int branchIndex,
        int branchCount,
        double horizontalVerticalRatio,
        CarvingMask carvingMask,
        WorldCarver.CarveSkipChecker skipChecker
    ) {
        RandomSource randomSource = RandomSource.create(seed);
        int i = randomSource.nextInt(branchCount / 2) + branchCount / 4;
        boolean bl = randomSource.nextInt(6) == 0;
        float f = 0.0F;
        float g = 0.0F;

        for (int j = branchIndex; j < branchCount; j++) {
            double d = 1.5 + Mth.sin((float) Math.PI * j / branchCount) * thickness;
            double e = d * horizontalVerticalRatio;
            float h = Mth.cos(pitch);
            x += Mth.cos(yaw) * h;
            y += Mth.sin(pitch);
            z += Mth.sin(yaw) * h;
            pitch *= bl ? 0.92F : 0.7F;
            pitch += g * 0.1F;
            yaw += f * 0.1F;
            g *= 0.9F;
            f *= 0.75F;
            g += (randomSource.nextFloat() - randomSource.nextFloat()) * randomSource.nextFloat() * 2.0F;
            f += (randomSource.nextFloat() - randomSource.nextFloat()) * randomSource.nextFloat() * 4.0F;
            if (j == i && thickness > 1.0F) {
                this.createTunnel(
                    context,
                    config,
                    chunk,
                    biomeAccessor,
                    randomSource.nextLong(),
                    aquifer,
                    x,
                    y,
                    z,
                    horizontalRadiusMultiplier,
                    verticalRadiusMultiplier,
                    randomSource.nextFloat() * 0.5F + 0.5F,
                    yaw - (float) (Math.PI / 2),
                    pitch / 3.0F,
                    j,
                    branchCount,
                    1.0,
                    carvingMask,
                    skipChecker
                );
                this.createTunnel(
                    context,
                    config,
                    chunk,
                    biomeAccessor,
                    randomSource.nextLong(),
                    aquifer,
                    x,
                    y,
                    z,
                    horizontalRadiusMultiplier,
                    verticalRadiusMultiplier,
                    randomSource.nextFloat() * 0.5F + 0.5F,
                    yaw + (float) (Math.PI / 2),
                    pitch / 3.0F,
                    j,
                    branchCount,
                    1.0,
                    carvingMask,
                    skipChecker
                );
                return;
            }

            if (randomSource.nextInt(4) != 0) {
                if (!canReach(chunk.getPos(), x, z, j, branchCount, thickness)) {
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
                    d * horizontalRadiusMultiplier,
                    e * verticalRadiusMultiplier,
                    carvingMask,
                    skipChecker
                );
            }
        }
    }

    private static boolean shouldSkip(double relative, double relativeY, double relativeZ, double minrelativeY) {
        return relativeY <= minrelativeY ? true : relative * relative + relativeY * relativeY + relativeZ * relativeZ >= 1.0;
    }
}
