package net.minecraft.util;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class ParticleUtils {
    public static void spawnParticlesOnBlockFaces(Level level, BlockPos pos, ParticleOptions particle, IntProvider count) {
        for (Direction direction : Direction.values()) {
            spawnParticlesOnBlockFace(level, pos, particle, count, direction, () -> getRandomSpeedRanges(level.random), 0.55);
        }
    }

    public static void spawnParticlesOnBlockFace(
        Level level, BlockPos pos, ParticleOptions particle, IntProvider count, Direction direction, Supplier<Vec3> speedSupplier, double spread
    ) {
        int i = count.sample(level.random);

        for (int j = 0; j < i; j++) {
            spawnParticleOnFace(level, pos, direction, particle, speedSupplier.get(), spread);
        }
    }

    private static Vec3 getRandomSpeedRanges(RandomSource random) {
        return new Vec3(Mth.nextDouble(random, -0.5, 0.5), Mth.nextDouble(random, -0.5, 0.5), Mth.nextDouble(random, -0.5, 0.5));
    }

    public static void spawnParticlesAlongAxis(Direction.Axis axis, Level level, BlockPos pos, double spread, ParticleOptions particle, UniformInt count) {
        Vec3 vec3 = Vec3.atCenterOf(pos);
        boolean bl = axis == Direction.Axis.X;
        boolean bl2 = axis == Direction.Axis.Y;
        boolean bl3 = axis == Direction.Axis.Z;
        int i = count.sample(level.random);

        for (int j = 0; j < i; j++) {
            double d = vec3.x + Mth.nextDouble(level.random, -1.0, 1.0) * (bl ? 0.5 : spread);
            double e = vec3.y + Mth.nextDouble(level.random, -1.0, 1.0) * (bl2 ? 0.5 : spread);
            double f = vec3.z + Mth.nextDouble(level.random, -1.0, 1.0) * (bl3 ? 0.5 : spread);
            double g = bl ? Mth.nextDouble(level.random, -1.0, 1.0) : 0.0;
            double h = bl2 ? Mth.nextDouble(level.random, -1.0, 1.0) : 0.0;
            double k = bl3 ? Mth.nextDouble(level.random, -1.0, 1.0) : 0.0;
            level.addParticle(particle, d, e, f, g, h, k);
        }
    }

    public static void spawnParticleOnFace(Level level, BlockPos pos, Direction direction, ParticleOptions particle, Vec3 speed, double spread) {
        Vec3 vec3 = Vec3.atCenterOf(pos);
        int i = direction.getStepX();
        int j = direction.getStepY();
        int k = direction.getStepZ();
        double d = vec3.x + (i == 0 ? Mth.nextDouble(level.random, -0.5, 0.5) : i * spread);
        double e = vec3.y + (j == 0 ? Mth.nextDouble(level.random, -0.5, 0.5) : j * spread);
        double f = vec3.z + (k == 0 ? Mth.nextDouble(level.random, -0.5, 0.5) : k * spread);
        double g = i == 0 ? speed.x() : 0.0;
        double h = j == 0 ? speed.y() : 0.0;
        double l = k == 0 ? speed.z() : 0.0;
        level.addParticle(particle, d, e, f, g, h, l);
    }

    public static void spawnParticleBelow(Level level, BlockPos pos, RandomSource random, ParticleOptions particle) {
        double d = pos.getX() + random.nextDouble();
        double e = pos.getY() - 0.05;
        double f = pos.getZ() + random.nextDouble();
        level.addParticle(particle, d, e, f, 0.0, 0.0, 0.0);
    }

    public static void spawnParticleInBlock(LevelAccessor level, BlockPos pos, int count, ParticleOptions particle) {
        double d = 0.5;
        BlockState blockState = level.getBlockState(pos);
        double e = blockState.isAir() ? 1.0 : blockState.getShape(level, pos).max(Direction.Axis.Y);
        spawnParticles(level, pos, count, 0.5, e, true, particle);
    }

    public static void spawnParticles(
        LevelAccessor level, BlockPos pos, int count, double xzSpread, double ySpread, boolean allowInAir, ParticleOptions particle
    ) {
        RandomSource randomSource = level.getRandom();

        for (int i = 0; i < count; i++) {
            double d = randomSource.nextGaussian() * 0.02;
            double e = randomSource.nextGaussian() * 0.02;
            double f = randomSource.nextGaussian() * 0.02;
            double g = 0.5 - xzSpread;
            double h = pos.getX() + g + randomSource.nextDouble() * xzSpread * 2.0;
            double j = pos.getY() + randomSource.nextDouble() * ySpread;
            double k = pos.getZ() + g + randomSource.nextDouble() * xzSpread * 2.0;
            if (allowInAir || !level.getBlockState(BlockPos.containing(h, j, k).below()).isAir()) {
                level.addParticle(particle, h, j, k, d, e, f);
            }
        }
    }

    public static void spawnSmashAttackParticles(LevelAccessor level, BlockPos pos, int power) {
        Vec3 vec3 = pos.getCenter().add(0.0, 0.5, 0.0);
        BlockParticleOption blockParticleOption = new BlockParticleOption(ParticleTypes.DUST_PILLAR, level.getBlockState(pos));

        for (int i = 0; i < power / 3.0F; i++) {
            double d = vec3.x + level.getRandom().nextGaussian() / 2.0;
            double e = vec3.y;
            double f = vec3.z + level.getRandom().nextGaussian() / 2.0;
            double g = level.getRandom().nextGaussian() * 0.2F;
            double h = level.getRandom().nextGaussian() * 0.2F;
            double j = level.getRandom().nextGaussian() * 0.2F;
            level.addParticle(blockParticleOption, d, e, f, g, h, j);
        }

        for (int i = 0; i < power / 1.5F; i++) {
            double d = vec3.x + 3.5 * Math.cos(i) + level.getRandom().nextGaussian() / 2.0;
            double e = vec3.y;
            double f = vec3.z + 3.5 * Math.sin(i) + level.getRandom().nextGaussian() / 2.0;
            double g = level.getRandom().nextGaussian() * 0.05F;
            double h = level.getRandom().nextGaussian() * 0.05F;
            double j = level.getRandom().nextGaussian() * 0.05F;
            level.addParticle(blockParticleOption, d, e, f, g, h, j);
        }
    }
}
