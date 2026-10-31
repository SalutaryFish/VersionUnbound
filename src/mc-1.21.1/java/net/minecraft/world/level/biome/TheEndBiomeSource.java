package net.minecraft.world.level.biome;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.QuartPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.levelgen.DensityFunction;

public class TheEndBiomeSource extends BiomeSource {
    public static final MapCodec<TheEndBiomeSource> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                RegistryOps.retrieveElement(Biomes.THE_END),
                RegistryOps.retrieveElement(Biomes.END_HIGHLANDS),
                RegistryOps.retrieveElement(Biomes.END_MIDLANDS),
                RegistryOps.retrieveElement(Biomes.SMALL_END_ISLANDS),
                RegistryOps.retrieveElement(Biomes.END_BARRENS)
            )
            .apply(instance, instance.stable(TheEndBiomeSource::new))
    );
    private final Holder<Biome> end;
    private final Holder<Biome> highlands;
    private final Holder<Biome> midlands;
    private final Holder<Biome> islands;
    private final Holder<Biome> barrens;

    public static TheEndBiomeSource create(HolderGetter<Biome> biomeGetter) {
        return new TheEndBiomeSource(
            biomeGetter.getOrThrow(Biomes.THE_END),
            biomeGetter.getOrThrow(Biomes.END_HIGHLANDS),
            biomeGetter.getOrThrow(Biomes.END_MIDLANDS),
            biomeGetter.getOrThrow(Biomes.SMALL_END_ISLANDS),
            biomeGetter.getOrThrow(Biomes.END_BARRENS)
        );
    }

    private TheEndBiomeSource(Holder<Biome> end, Holder<Biome> highlands, Holder<Biome> midlands, Holder<Biome> islands, Holder<Biome> barrens) {
        this.end = end;
        this.highlands = highlands;
        this.midlands = midlands;
        this.islands = islands;
        this.barrens = barrens;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return Stream.of(this.end, this.highlands, this.midlands, this.islands, this.barrens);
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        int i = QuartPos.toBlock(x);
        int j = QuartPos.toBlock(y);
        int k = QuartPos.toBlock(z);
        int l = SectionPos.blockToSectionCoord(i);
        int m = SectionPos.blockToSectionCoord(k);
        if ((long)l * l + (long)m * m <= 4096L) {
            return this.end;
        } else {
            int n = (SectionPos.blockToSectionCoord(i) * 2 + 1) * 8;
            int o = (SectionPos.blockToSectionCoord(k) * 2 + 1) * 8;
            double d = sampler.erosion().compute(new DensityFunction.SinglePointContext(n, j, o));
            if (d > 0.25) {
                return this.highlands;
            } else if (d >= -0.0625) {
                return this.midlands;
            } else {
                return d < -0.21875 ? this.islands : this.barrens;
            }
        }
    }
}
