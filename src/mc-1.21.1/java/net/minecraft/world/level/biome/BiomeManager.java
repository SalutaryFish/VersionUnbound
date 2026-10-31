package net.minecraft.world.level.biome;

import com.google.common.hash.Hashing;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.util.LinearCongruentialGenerator;
import net.minecraft.util.Mth;

public class BiomeManager {
    public static final int CHUNK_CENTER_QUART = QuartPos.fromBlock(8);
    private static final int ZOOM_BITS = 2;
    private static final int ZOOM = 4;
    private static final int ZOOM_MASK = 3;
    private final BiomeManager.NoiseBiomeSource noiseBiomeSource;
    private final long biomeZoomSeed;

    public BiomeManager(BiomeManager.NoiseBiomeSource noiseBiomeSource, long biomeZoomSeed) {
        this.noiseBiomeSource = noiseBiomeSource;
        this.biomeZoomSeed = biomeZoomSeed;
    }

    public static long obfuscateSeed(long seed) {
        return Hashing.sha256().hashLong(seed).asLong();
    }

    public BiomeManager withDifferentSource(BiomeManager.NoiseBiomeSource newSource) {
        return new BiomeManager(newSource, this.biomeZoomSeed);
    }

    public Holder<Biome> getBiome(BlockPos pos) {
        int i = pos.getX() - 2;
        int j = pos.getY() - 2;
        int k = pos.getZ() - 2;
        int l = i >> 2;
        int m = j >> 2;
        int n = k >> 2;
        double d = (i & 3) / 4.0;
        double e = (j & 3) / 4.0;
        double f = (k & 3) / 4.0;
        int o = 0;
        double g = Double.POSITIVE_INFINITY;

        for (int p = 0; p < 8; p++) {
            boolean bl = (p & 4) == 0;
            boolean bl2 = (p & 2) == 0;
            boolean bl3 = (p & 1) == 0;
            int q = bl ? l : l + 1;
            int r = bl2 ? m : m + 1;
            int s = bl3 ? n : n + 1;
            double h = bl ? d : d - 1.0;
            double t = bl2 ? e : e - 1.0;
            double u = bl3 ? f : f - 1.0;
            double v = getFiddledDistance(this.biomeZoomSeed, q, r, s, h, t, u);
            if (g > v) {
                o = p;
                g = v;
            }
        }

        int p = (o & 4) == 0 ? l : l + 1;
        int w = (o & 2) == 0 ? m : m + 1;
        int x = (o & 1) == 0 ? n : n + 1;
        return this.noiseBiomeSource.getNoiseBiome(p, w, x);
    }

    public Holder<Biome> getNoiseBiomeAtPosition(double x, double y, double z) {
        int i = QuartPos.fromBlock(Mth.floor(x));
        int j = QuartPos.fromBlock(Mth.floor(y));
        int k = QuartPos.fromBlock(Mth.floor(z));
        return this.getNoiseBiomeAtQuart(i, j, k);
    }

    public Holder<Biome> getNoiseBiomeAtPosition(BlockPos pos) {
        int i = QuartPos.fromBlock(pos.getX());
        int j = QuartPos.fromBlock(pos.getY());
        int k = QuartPos.fromBlock(pos.getZ());
        return this.getNoiseBiomeAtQuart(i, j, k);
    }

    public Holder<Biome> getNoiseBiomeAtQuart(int x, int y, int z) {
        return this.noiseBiomeSource.getNoiseBiome(x, y, z);
    }

    private static double getFiddledDistance(long seed, int x, int y, int z, double xNoise, double yNoise, double zNoise) {
        long l = seed;
        l = LinearCongruentialGenerator.next(l, x);
        l = LinearCongruentialGenerator.next(l, y);
        l = LinearCongruentialGenerator.next(l, z);
        l = LinearCongruentialGenerator.next(l, x);
        l = LinearCongruentialGenerator.next(l, y);
        l = LinearCongruentialGenerator.next(l, z);
        double d = getFiddle(l);
        l = LinearCongruentialGenerator.next(l, seed);
        double e = getFiddle(l);
        l = LinearCongruentialGenerator.next(l, seed);
        double f = getFiddle(l);
        return Mth.square(zNoise + f) + Mth.square(yNoise + e) + Mth.square(xNoise + d);
    }

    private static double getFiddle(long seed) {
        double d = Math.floorMod(seed >> 24, 1024) / 1024.0;
        return (d - 0.5) * 0.9;
    }

    public interface NoiseBiomeSource {
        /**
         * Gets the biome at the given quart positions.
         * Note that the coordinates passed into this method are 1/4 the scale of block coordinates.
         */
        Holder<Biome> getNoiseBiome(int var1, int var2, int var3);
    }
}
