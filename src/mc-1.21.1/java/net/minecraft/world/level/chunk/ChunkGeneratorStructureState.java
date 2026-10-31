package net.minecraft.world.level.chunk;

import com.google.common.base.Stopwatch;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.SectionPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.slf4j.Logger;

public class ChunkGeneratorStructureState {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final RandomState randomState;
    private final BiomeSource biomeSource;
    private final long levelSeed;
    private final long concentricRingsSeed;
    private final Map<Structure, List<StructurePlacement>> placementsForStructure = new Object2ObjectOpenHashMap<>();
    private final Map<ConcentricRingsStructurePlacement, CompletableFuture<List<ChunkPos>>> ringPositions = new Object2ObjectArrayMap<>();
    private boolean hasGeneratedPositions;
    private final List<Holder<StructureSet>> possibleStructureSets;

    public static ChunkGeneratorStructureState createForFlat(
        RandomState randomState, long levelSeed, BiomeSource biomeSource, Stream<Holder<StructureSet>> structureSets
    ) {
        List<Holder<StructureSet>> list = structureSets.filter(structureSet -> hasBiomesForStructureSet(structureSet.value(), biomeSource)).toList();
        return new ChunkGeneratorStructureState(randomState, biomeSource, levelSeed, 0L, list);
    }

    public static ChunkGeneratorStructureState createForNormal(
        RandomState randomState, long seed, BiomeSource biomeSource, HolderLookup<StructureSet> structureSetLookup
    ) {
        List<Holder<StructureSet>> list = structureSetLookup.listElements()
            .filter(structureSet -> hasBiomesForStructureSet(structureSet.value(), biomeSource))
            .collect(Collectors.toUnmodifiableList());
        return new ChunkGeneratorStructureState(randomState, biomeSource, seed, seed, list);
    }

    private static boolean hasBiomesForStructureSet(StructureSet structureSet, BiomeSource biomeSource) {
        Stream<Holder<Biome>> stream = structureSet.structures().stream().flatMap(structureEntry -> {
            Structure structure = structureEntry.structure().value();
            return structure.biomes().stream();
        });
        return stream.anyMatch(biomeSource.possibleBiomes()::contains);
    }

    private ChunkGeneratorStructureState(
        RandomState randomState, BiomeSource biomeSource, long levelSeed, long cocentricRingsSeed, List<Holder<StructureSet>> possibleStructureSets
    ) {
        this.randomState = randomState;
        this.levelSeed = levelSeed;
        this.biomeSource = biomeSource;
        this.concentricRingsSeed = cocentricRingsSeed;
        this.possibleStructureSets = possibleStructureSets;
    }

    public List<Holder<StructureSet>> possibleStructureSets() {
        return this.possibleStructureSets;
    }

    private void generatePositions() {
        Set<Holder<Biome>> set = this.biomeSource.possibleBiomes();
        this.possibleStructureSets()
            .forEach(
                structureSetHolder -> {
                    StructureSet structureSet = structureSetHolder.value();
                    boolean bl = false;

                    for (StructureSet.StructureSelectionEntry structureSelectionEntry : structureSet.structures()) {
                        Structure structure = structureSelectionEntry.structure().value();
                        if (structure.biomes().stream().anyMatch(set::contains)) {
                            this.placementsForStructure.computeIfAbsent(structure, key -> new ArrayList<>()).add(structureSet.placement());
                            bl = true;
                        }
                    }

                    if (bl && structureSet.placement() instanceof ConcentricRingsStructurePlacement concentricRingsStructurePlacement) {
                        this.ringPositions
                            .put(
                                concentricRingsStructurePlacement,
                                this.generateRingPositions((Holder<StructureSet>)structureSetHolder, concentricRingsStructurePlacement)
                            );
                    }
                }
            );
    }

    private CompletableFuture<List<ChunkPos>> generateRingPositions(Holder<StructureSet> structureSet, ConcentricRingsStructurePlacement placement) {
        if (placement.count() == 0) {
            return CompletableFuture.completedFuture(List.of());
        }

        Stopwatch stopwatch = Stopwatch.createStarted(Util.TICKER);
        int i = placement.distance();
        int j = placement.count();
        List<CompletableFuture<ChunkPos>> list = new ArrayList<>(j);
        int k = placement.spread();
        HolderSet<Biome> holderSet = placement.preferredBiomes();
        RandomSource randomSource = RandomSource.create();
        randomSource.setSeed(this.concentricRingsSeed);
        double d = randomSource.nextDouble() * Math.PI * 2.0;
        int l = 0;
        int m = 0;

        for (int n = 0; n < j; n++) {
            double e = 4 * i + i * m * 6 + (randomSource.nextDouble() - 0.5) * (i * 2.5);
            int o = (int)Math.round(Math.cos(d) * e);
            int p = (int)Math.round(Math.sin(d) * e);
            RandomSource randomSource2 = randomSource.fork();
            list.add(
                CompletableFuture.supplyAsync(
                    () -> {
                        Pair<BlockPos, Holder<Biome>> pair = this.biomeSource
                            .findBiomeHorizontal(
                                SectionPos.sectionToBlockCoord(o, 8),
                                0,
                                SectionPos.sectionToBlockCoord(p, 8),
                                112,
                                holderSet::contains,
                                randomSource2,
                                this.randomState.sampler()
                            );
                        if (pair != null) {
                            BlockPos blockPos = pair.getFirst();
                            return new ChunkPos(SectionPos.blockToSectionCoord(blockPos.getX()), SectionPos.blockToSectionCoord(blockPos.getZ()));
                        } else {
                            return new ChunkPos(o, p);
                        }
                    },
                    Util.backgroundExecutor()
                )
            );
            d += (Math.PI * 2) / k;
            if (++l == k) {
                m++;
                l = 0;
                k += 2 * k / (m + 1);
                k = Math.min(k, j - n);
                d += randomSource.nextDouble() * Math.PI * 2.0;
            }
        }

        return Util.sequence(list).thenApply(completed -> {
            double dx = stopwatch.stop().elapsed(TimeUnit.MILLISECONDS) / 1000.0;
            LOGGER.debug("Calculation for {} took {}s", structureSet, dx);
            return completed;
        });
    }

    public void ensureStructuresGenerated() {
        if (!this.hasGeneratedPositions) {
            this.generatePositions();
            this.hasGeneratedPositions = true;
        }
    }

    @Nullable
    public List<ChunkPos> getRingPositionsFor(ConcentricRingsStructurePlacement placement) {
        this.ensureStructuresGenerated();
        CompletableFuture<List<ChunkPos>> completableFuture = this.ringPositions.get(placement);
        return completableFuture != null ? completableFuture.join() : null;
    }

    public List<StructurePlacement> getPlacementsForStructure(Holder<Structure> structure) {
        this.ensureStructuresGenerated();
        return this.placementsForStructure.getOrDefault(structure.value(), List.of());
    }

    public RandomState randomState() {
        return this.randomState;
    }

    public boolean hasStructureChunkInRange(Holder<StructureSet> structureSet, int x, int z, int range) {
        StructurePlacement structurePlacement = structureSet.value().placement();

        for (int i = x - range; i <= x + range; i++) {
            for (int j = z - range; j <= z + range; j++) {
                if (structurePlacement.isStructureChunk(this, i, j)) {
                    return true;
                }
            }
        }

        return false;
    }

    public long getLevelSeed() {
        return this.levelSeed;
    }
}
