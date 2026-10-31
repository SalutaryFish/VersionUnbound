package net.minecraft.world.level.biome;

import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;

public abstract class BiomeSource implements BiomeResolver {
    public static final Codec<BiomeSource> CODEC = BuiltInRegistries.BIOME_SOURCE.byNameCodec().dispatchStable(BiomeSource::codec, Function.identity());
    private final Supplier<Set<Holder<Biome>>> possibleBiomes = Suppliers.memoize(
        () -> this.collectPossibleBiomes().distinct().collect(ImmutableSet.toImmutableSet())
    );

    protected BiomeSource() {
    }

    protected abstract MapCodec<? extends BiomeSource> codec();

    protected abstract Stream<Holder<Biome>> collectPossibleBiomes();

    public Set<Holder<Biome>> possibleBiomes() {
        return this.possibleBiomes.get();
    }

    public Set<Holder<Biome>> getBiomesWithin(int x, int y, int z, int radius, Climate.Sampler sampler) {
        int i = QuartPos.fromBlock(x - radius);
        int j = QuartPos.fromBlock(y - radius);
        int k = QuartPos.fromBlock(z - radius);
        int l = QuartPos.fromBlock(x + radius);
        int m = QuartPos.fromBlock(y + radius);
        int n = QuartPos.fromBlock(z + radius);
        int o = l - i + 1;
        int p = m - j + 1;
        int q = n - k + 1;
        Set<Holder<Biome>> set = Sets.newHashSet();

        for (int r = 0; r < q; r++) {
            for (int s = 0; s < o; s++) {
                for (int t = 0; t < p; t++) {
                    int u = i + s;
                    int v = j + t;
                    int w = k + r;
                    set.add(this.getNoiseBiome(u, v, w, sampler));
                }
            }
        }

        return set;
    }

    @Nullable
    public Pair<BlockPos, Holder<Biome>> findBiomeHorizontal(
        int x, int y, int z, int radius, Predicate<Holder<Biome>> biomePredicate, RandomSource random, Climate.Sampler sampler
    ) {
        return this.findBiomeHorizontal(x, y, z, radius, 1, biomePredicate, random, false, sampler);
    }

    @Nullable
    public Pair<BlockPos, Holder<Biome>> findClosestBiome3d(
        BlockPos pos, int radius, int horizontalStep, int verticalStep, Predicate<Holder<Biome>> biomePredicate, Climate.Sampler sampler, LevelReader level
    ) {
        Set<Holder<Biome>> set = this.possibleBiomes().stream().filter(biomePredicate).collect(Collectors.toUnmodifiableSet());
        if (set.isEmpty()) {
            return null;
        }

        int i = Math.floorDiv(radius, horizontalStep);
        int[] is = Mth.outFromOrigin(pos.getY(), level.getMinBuildHeight() + 1, level.getMaxBuildHeight(), verticalStep).toArray();

        for (BlockPos.MutableBlockPos mutableBlockPos : BlockPos.spiralAround(BlockPos.ZERO, i, Direction.EAST, Direction.SOUTH)) {
            int j = pos.getX() + mutableBlockPos.getX() * horizontalStep;
            int k = pos.getZ() + mutableBlockPos.getZ() * horizontalStep;
            int l = QuartPos.fromBlock(j);
            int m = QuartPos.fromBlock(k);

            for (int n : is) {
                int o = QuartPos.fromBlock(n);
                Holder<Biome> holder = this.getNoiseBiome(l, o, m, sampler);
                if (set.contains(holder)) {
                    return Pair.of(new BlockPos(j, n, k), holder);
                }
            }
        }

        return null;
    }

    @Nullable
    public Pair<BlockPos, Holder<Biome>> findBiomeHorizontal(
        int x,
        int y,
        int z,
        int radius,
        int increment,
        Predicate<Holder<Biome>> biomePredicate,
        RandomSource random,
        boolean findClosest,
        Climate.Sampler sampler
    ) {
        int i = QuartPos.fromBlock(x);
        int j = QuartPos.fromBlock(z);
        int k = QuartPos.fromBlock(radius);
        int l = QuartPos.fromBlock(y);
        Pair<BlockPos, Holder<Biome>> pair = null;
        int m = 0;
        int n = findClosest ? 0 : k;
        int o = n;

        while (o <= k) {
            for (int p = SharedConstants.debugGenerateSquareTerrainWithoutNoise ? 0 : -o; p <= o; p += increment) {
                boolean bl = Math.abs(p) == o;

                for (int q = -o; q <= o; q += increment) {
                    if (findClosest) {
                        boolean bl2 = Math.abs(q) == o;
                        if (!bl2 && !bl) {
                            continue;
                        }
                    }

                    int r = i + q;
                    int s = j + p;
                    Holder<Biome> holder = this.getNoiseBiome(r, l, s, sampler);
                    if (biomePredicate.test(holder)) {
                        if (pair == null || random.nextInt(m + 1) == 0) {
                            BlockPos blockPos = new BlockPos(QuartPos.toBlock(r), y, QuartPos.toBlock(s));
                            if (findClosest) {
                                return Pair.of(blockPos, holder);
                            }

                            pair = Pair.of(blockPos, holder);
                        }

                        m++;
                    }
                }
            }

            o += increment;
        }

        return pair;
    }

    @Override
    public abstract Holder<Biome> getNoiseBiome(int var1, int var2, int var3, Climate.Sampler var4);

    public void addDebugInfo(List<String> info, BlockPos pos, Climate.Sampler sampler) {
    }
}
