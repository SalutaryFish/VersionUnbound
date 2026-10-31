package net.minecraft.world.level.chunk;

import com.google.common.base.Suppliers;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.random.WeightedRandomList;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureSpawnOverride;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.apache.commons.lang3.mutable.MutableBoolean;

public abstract class ChunkGenerator {
    public static final Codec<ChunkGenerator> CODEC = BuiltInRegistries.CHUNK_GENERATOR
        .byNameCodec()
        .dispatchStable(ChunkGenerator::codec, Function.identity());
    protected final BiomeSource biomeSource;
    private final Supplier<List<FeatureSorter.StepFeatureData>> featuresPerStep;
    private final Function<Holder<Biome>, BiomeGenerationSettings> generationSettingsGetter;

    public ChunkGenerator(BiomeSource biomeSource) {
        this(biomeSource, biome -> biome.value().getGenerationSettings());
    }

    public ChunkGenerator(BiomeSource biomeSource, Function<Holder<Biome>, BiomeGenerationSettings> generationSettingsGetter) {
        this.biomeSource = biomeSource;
        this.generationSettingsGetter = generationSettingsGetter;
        this.featuresPerStep = Suppliers.memoize(
            () -> FeatureSorter.buildFeaturesPerStep(List.copyOf(biomeSource.possibleBiomes()), biome -> generationSettingsGetter.apply(biome).features(), true)
        );
    }

    public void validate() {
        this.featuresPerStep.get();
    }

    protected abstract MapCodec<? extends ChunkGenerator> codec();

    public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> structureSetLookup, RandomState randomState, long seed) {
        return ChunkGeneratorStructureState.createForNormal(randomState, seed, this.biomeSource, structureSetLookup);
    }

    public Optional<ResourceKey<MapCodec<? extends ChunkGenerator>>> getTypeNameForDataFixer() {
        return BuiltInRegistries.CHUNK_GENERATOR.getResourceKey(this.codec());
    }

    public CompletableFuture<ChunkAccess> createBiomes(RandomState randomState, Blender blender, StructureManager structureManager, ChunkAccess chunk) {
        return CompletableFuture.supplyAsync(Util.wrapThreadWithTaskName("init_biomes", () -> {
            chunk.fillBiomesFromNoise(this.biomeSource, randomState.sampler());
            return chunk;
        }), Util.backgroundExecutor());
    }

    public abstract void applyCarvers(
        WorldGenRegion var1, long var2, RandomState var4, BiomeManager var5, StructureManager var6, ChunkAccess var7, GenerationStep.Carving var8
    );

    @Nullable
    public Pair<BlockPos, Holder<Structure>> findNearestMapStructure(
        ServerLevel level, HolderSet<Structure> structure, BlockPos pos, int searchRadius, boolean skipKnownStructures
    ) {
        ChunkGeneratorStructureState chunkGeneratorStructureState = level.getChunkSource().getGeneratorState();
        Map<StructurePlacement, Set<Holder<Structure>>> map = new Object2ObjectArrayMap<>();

        for (Holder<Structure> holder : structure) {
            for (StructurePlacement structurePlacement : chunkGeneratorStructureState.getPlacementsForStructure(holder)) {
                map.computeIfAbsent(structurePlacement, key -> new ObjectArraySet<>()).add(holder);
            }
        }

        if (map.isEmpty()) {
            return null;
        }

        Pair<BlockPos, Holder<Structure>> pair = null;
        double d = Double.MAX_VALUE;
        StructureManager structureManager = level.structureManager();
        List<Entry<StructurePlacement, Set<Holder<Structure>>>> list = new ArrayList<>(map.size());

        for (Entry<StructurePlacement, Set<Holder<Structure>>> entry : map.entrySet()) {
            StructurePlacement structurePlacement2 = entry.getKey();
            if (structurePlacement2 instanceof ConcentricRingsStructurePlacement concentricRingsStructurePlacement) {
                Pair<BlockPos, Holder<Structure>> pair2 = this.getNearestGeneratedStructure(
                    entry.getValue(), level, structureManager, pos, skipKnownStructures, concentricRingsStructurePlacement
                );
                if (pair2 != null) {
                    BlockPos blockPos = pair2.getFirst();
                    double e = pos.distSqr(blockPos);
                    if (e < d) {
                        d = e;
                        pair = pair2;
                    }
                }
            } else if (structurePlacement2 instanceof RandomSpreadStructurePlacement) {
                list.add(entry);
            }
        }

        if (!list.isEmpty()) {
            int i = SectionPos.blockToSectionCoord(pos.getX());
            int j = SectionPos.blockToSectionCoord(pos.getZ());

            for (int k = 0; k <= searchRadius; k++) {
                boolean bl = false;

                for (Entry<StructurePlacement, Set<Holder<Structure>>> entry2 : list) {
                    RandomSpreadStructurePlacement randomSpreadStructurePlacement = (RandomSpreadStructurePlacement)entry2.getKey();
                    Pair<BlockPos, Holder<Structure>> pair3 = getNearestGeneratedStructure(
                        entry2.getValue(),
                        level,
                        structureManager,
                        i,
                        j,
                        k,
                        skipKnownStructures,
                        chunkGeneratorStructureState.getLevelSeed(),
                        randomSpreadStructurePlacement
                    );
                    if (pair3 != null) {
                        bl = true;
                        double f = pos.distSqr(pair3.getFirst());
                        if (f < d) {
                            d = f;
                            pair = pair3;
                        }
                    }
                }

                if (bl) {
                    return pair;
                }
            }
        }

        return pair;
    }

    @Nullable
    private Pair<BlockPos, Holder<Structure>> getNearestGeneratedStructure(
        Set<Holder<Structure>> structureHoldersSet,
        ServerLevel level,
        StructureManager structureManager,
        BlockPos pos,
        boolean skipKnownStructures,
        ConcentricRingsStructurePlacement placement
    ) {
        List<ChunkPos> list = level.getChunkSource().getGeneratorState().getRingPositionsFor(placement);
        if (list == null) {
            throw new IllegalStateException("Somehow tried to find structures for a placement that doesn't exist");
        }

        Pair<BlockPos, Holder<Structure>> pair = null;
        double d = Double.MAX_VALUE;
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

        for (ChunkPos chunkPos : list) {
            mutableBlockPos.set(SectionPos.sectionToBlockCoord(chunkPos.x, 8), 32, SectionPos.sectionToBlockCoord(chunkPos.z, 8));
            double e = mutableBlockPos.distSqr(pos);
            boolean bl = pair == null || e < d;
            if (bl) {
                Pair<BlockPos, Holder<Structure>> pair2 = getStructureGeneratingAt(
                    structureHoldersSet, level, structureManager, skipKnownStructures, placement, chunkPos
                );
                if (pair2 != null) {
                    pair = pair2;
                    d = e;
                }
            }
        }

        return pair;
    }

    @Nullable
    private static Pair<BlockPos, Holder<Structure>> getNearestGeneratedStructure(
        Set<Holder<Structure>> structureHoldersSet,
        LevelReader level,
        StructureManager structureManager,
        int x,
        int y,
        int z,
        boolean skipKnownStructures,
        long seed,
        RandomSpreadStructurePlacement spreadPlacement
    ) {
        int i = spreadPlacement.spacing();

        for (int j = -z; j <= z; j++) {
            boolean bl = j == -z || j == z;

            for (int k = -z; k <= z; k++) {
                boolean bl2 = k == -z || k == z;
                if (bl || bl2) {
                    int l = x + i * j;
                    int m = y + i * k;
                    ChunkPos chunkPos = spreadPlacement.getPotentialStructureChunk(seed, l, m);
                    Pair<BlockPos, Holder<Structure>> pair = getStructureGeneratingAt(
                        structureHoldersSet, level, structureManager, skipKnownStructures, spreadPlacement, chunkPos
                    );
                    if (pair != null) {
                        return pair;
                    }
                }
            }
        }

        return null;
    }

    @Nullable
    private static Pair<BlockPos, Holder<Structure>> getStructureGeneratingAt(
        Set<Holder<Structure>> structureHoldersSet,
        LevelReader level,
        StructureManager structureManager,
        boolean skipKnownStructures,
        StructurePlacement placement,
        ChunkPos chunkPos
    ) {
        for (Holder<Structure> holder : structureHoldersSet) {
            StructureCheckResult structureCheckResult = structureManager.checkStructurePresence(chunkPos, holder.value(), placement, skipKnownStructures);
            if (structureCheckResult != StructureCheckResult.START_NOT_PRESENT) {
                if (!skipKnownStructures && structureCheckResult == StructureCheckResult.START_PRESENT) {
                    return Pair.of(placement.getLocatePos(chunkPos), holder);
                }

                ChunkAccess chunkAccess = level.getChunk(chunkPos.x, chunkPos.z, ChunkStatus.STRUCTURE_STARTS);
                StructureStart structureStart = structureManager.getStartForStructure(SectionPos.bottomOf(chunkAccess), holder.value(), chunkAccess);
                if (structureStart != null && structureStart.isValid() && (!skipKnownStructures || tryAddReference(structureManager, structureStart))) {
                    return Pair.of(placement.getLocatePos(structureStart.getChunkPos()), holder);
                }
            }
        }

        return null;
    }

    private static boolean tryAddReference(StructureManager structureManager, StructureStart structureStart) {
        if (structureStart.canBeReferenced()) {
            structureManager.addReference(structureStart);
            return true;
        } else {
            return false;
        }
    }

    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager) {
        ChunkPos chunkPos = chunk.getPos();
        if (!SharedConstants.debugVoidTerrain(chunkPos)) {
            SectionPos sectionPos = SectionPos.of(chunkPos, level.getMinSection());
            BlockPos blockPos = sectionPos.origin();
            Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
            Map<Integer, List<Structure>> map = registry.stream().collect(Collectors.groupingBy(structure -> structure.step().ordinal()));
            List<FeatureSorter.StepFeatureData> list = this.featuresPerStep.get();
            WorldgenRandom worldgenRandom = new WorldgenRandom(new XoroshiroRandomSource(RandomSupport.generateUniqueSeed()));
            long l = worldgenRandom.setDecorationSeed(level.getSeed(), blockPos.getX(), blockPos.getZ());
            Set<Holder<Biome>> set = new ObjectArraySet<>();
            ChunkPos.rangeClosed(sectionPos.chunk(), 1).forEach(chunkPos2 -> {
                ChunkAccess chunkAccess = level.getChunk(chunkPos2.x, chunkPos2.z);

                for (LevelChunkSection levelChunkSection : chunkAccess.getSections()) {
                    levelChunkSection.getBiomes().getAll(set::add);
                }
            });
            set.retainAll(this.biomeSource.possibleBiomes());
            int i = list.size();

            try {
                Registry<PlacedFeature> registry2 = level.registryAccess().registryOrThrow(Registries.PLACED_FEATURE);
                int j = Math.max(GenerationStep.Decoration.values().length, i);

                for (int k = 0; k < j; k++) {
                    int m = 0;
                    if (structureManager.shouldGenerateStructures()) {
                        for (Structure structure : map.getOrDefault(k, Collections.emptyList())) {
                            worldgenRandom.setFeatureSeed(l, m, k);
                            Supplier<String> supplier = () -> registry.getResourceKey(structure).map(Object::toString).orElseGet(structure::toString);

                            try {
                                level.setCurrentlyGenerating(supplier);
                                structureManager.startsForStructure(sectionPos, structure)
                                    .forEach(
                                        structureStart -> structureStart.placeInChunk(
                                            level, structureManager, this, worldgenRandom, getWritableArea(chunk), chunkPos
                                        )
                                    );
                            } catch (Exception exception) {
                                CrashReport crashReport = CrashReport.forThrowable(exception, "Feature placement");
                                crashReport.addCategory("Feature").setDetail("Description", supplier::get);
                                throw new ReportedException(crashReport);
                            }

                            m++;
                        }
                    }

                    if (k < i) {
                        IntSet intSet = new IntArraySet();

                        for (Holder<Biome> holder : set) {
                            List<HolderSet<PlacedFeature>> list3 = this.generationSettingsGetter.apply(holder).features();
                            if (k < list3.size()) {
                                HolderSet<PlacedFeature> holderSet = list3.get(k);
                                FeatureSorter.StepFeatureData stepFeatureData = list.get(k);
                                holderSet.stream()
                                    .map(Holder::value)
                                    .forEach(placedFeature2 -> intSet.add(stepFeatureData.indexMapping().applyAsInt(placedFeature2)));
                            }
                        }

                        int n = intSet.size();
                        int[] is = intSet.toIntArray();
                        Arrays.sort(is);
                        FeatureSorter.StepFeatureData stepFeatureData2 = list.get(k);

                        for (int o = 0; o < n; o++) {
                            int p = is[o];
                            PlacedFeature placedFeature = stepFeatureData2.features().get(p);
                            Supplier<String> supplier2 = () -> registry2.getResourceKey(placedFeature).map(Object::toString).orElseGet(placedFeature::toString);
                            worldgenRandom.setFeatureSeed(l, p, k);

                            try {
                                level.setCurrentlyGenerating(supplier2);
                                placedFeature.placeWithBiomeCheck(level, this, worldgenRandom, blockPos);
                            } catch (Exception exception2) {
                                CrashReport crashReport2 = CrashReport.forThrowable(exception2, "Feature placement");
                                crashReport2.addCategory("Feature").setDetail("Description", supplier2::get);
                                throw new ReportedException(crashReport2);
                            }
                        }
                    }
                }

                level.setCurrentlyGenerating(null);
            } catch (Exception exception3) {
                CrashReport crashReport3 = CrashReport.forThrowable(exception3, "Biome decoration");
                crashReport3.addCategory("Generation").setDetail("CenterX", chunkPos.x).setDetail("CenterZ", chunkPos.z).setDetail("Decoration Seed", l);
                throw new ReportedException(crashReport3);
            }
        }
    }

    private static BoundingBox getWritableArea(ChunkAccess chunk) {
        ChunkPos chunkPos = chunk.getPos();
        int i = chunkPos.getMinBlockX();
        int j = chunkPos.getMinBlockZ();
        LevelHeightAccessor levelHeightAccessor = chunk.getHeightAccessorForGeneration();
        int k = levelHeightAccessor.getMinBuildHeight() + 1;
        int l = levelHeightAccessor.getMaxBuildHeight() - 1;
        return new BoundingBox(i, k, j, i + 15, l, j + 15);
    }

    public abstract void buildSurface(WorldGenRegion var1, StructureManager var2, RandomState var3, ChunkAccess var4);

    public abstract void spawnOriginalMobs(WorldGenRegion var1);

    public int getSpawnHeight(LevelHeightAccessor level) {
        return 64;
    }

    public BiomeSource getBiomeSource() {
        return this.biomeSource;
    }

    public abstract int getGenDepth();

    public WeightedRandomList<MobSpawnSettings.SpawnerData> getMobsAt(
        Holder<Biome> biome, StructureManager structureManager, MobCategory category, BlockPos pos
    ) {
        Map<Structure, LongSet> map = structureManager.getAllStructuresAt(pos);

        for (Entry<Structure, LongSet> entry : map.entrySet()) {
            Structure structure = entry.getKey();
            StructureSpawnOverride structureSpawnOverride = structure.spawnOverrides().get(category);
            if (structureSpawnOverride != null) {
                MutableBoolean mutableBoolean = new MutableBoolean(false);
                Predicate<StructureStart> predicate = structureSpawnOverride.boundingBox() == StructureSpawnOverride.BoundingBoxType.PIECE
                    ? structureStart -> structureManager.structureHasPieceAt(pos, structureStart)
                    : structureStart -> structureStart.getBoundingBox().isInside(pos);
                structureManager.fillStartsForStructure(structure, entry.getValue(), structureStart -> {
                    if (mutableBoolean.isFalse() && predicate.test(structureStart)) {
                        mutableBoolean.setTrue();
                    }
                });
                if (mutableBoolean.isTrue()) {
                    return structureSpawnOverride.spawns();
                }
            }
        }

        return biome.value().getMobSettings().getMobs(category);
    }

    public void createStructures(
        RegistryAccess registryAccess,
        ChunkGeneratorStructureState structureState,
        StructureManager structureManager,
        ChunkAccess chunk,
        StructureTemplateManager structureTemplateManager
    ) {
        ChunkPos chunkPos = chunk.getPos();
        SectionPos sectionPos = SectionPos.bottomOf(chunk);
        RandomState randomState = structureState.randomState();
        structureState.possibleStructureSets()
            .forEach(
                structureSet -> {
                    StructurePlacement structurePlacement = structureSet.value().placement();
                    List<StructureSet.StructureSelectionEntry> list = structureSet.value().structures();

                    for (StructureSet.StructureSelectionEntry structureSelectionEntry : list) {
                        StructureStart structureStart = structureManager.getStartForStructure(sectionPos, structureSelectionEntry.structure().value(), chunk);
                        if (structureStart != null && structureStart.isValid()) {
                            return;
                        }
                    }

                    if (structurePlacement.isStructureChunk(structureState, chunkPos.x, chunkPos.z)) {
                        if (list.size() == 1) {
                            this.tryGenerateStructure(
                                list.get(0),
                                structureManager,
                                registryAccess,
                                randomState,
                                structureTemplateManager,
                                structureState.getLevelSeed(),
                                chunk,
                                chunkPos,
                                sectionPos
                            );
                        } else {
                            ArrayList<StructureSet.StructureSelectionEntry> arrayList = new ArrayList<>(list.size());
                            arrayList.addAll(list);
                            WorldgenRandom worldgenRandom = new WorldgenRandom(new LegacyRandomSource(0L));
                            worldgenRandom.setLargeFeatureSeed(structureState.getLevelSeed(), chunkPos.x, chunkPos.z);
                            int i = 0;

                            for (StructureSet.StructureSelectionEntry structureSelectionEntry2 : arrayList) {
                                i += structureSelectionEntry2.weight();
                            }

                            while (!arrayList.isEmpty()) {
                                int j = worldgenRandom.nextInt(i);
                                int k = 0;

                                for (StructureSet.StructureSelectionEntry structureSelectionEntry3 : arrayList) {
                                    j -= structureSelectionEntry3.weight();
                                    if (j < 0) {
                                        break;
                                    }

                                    k++;
                                }

                                StructureSet.StructureSelectionEntry structureSelectionEntry4 = arrayList.get(k);
                                if (this.tryGenerateStructure(
                                    structureSelectionEntry4,
                                    structureManager,
                                    registryAccess,
                                    randomState,
                                    structureTemplateManager,
                                    structureState.getLevelSeed(),
                                    chunk,
                                    chunkPos,
                                    sectionPos
                                )) {
                                    return;
                                }

                                arrayList.remove(k);
                                i -= structureSelectionEntry4.weight();
                            }
                        }
                    }
                }
            );
    }

    private boolean tryGenerateStructure(
        StructureSet.StructureSelectionEntry structureSelectionEntry,
        StructureManager structureManager,
        RegistryAccess registryAccess,
        RandomState random,
        StructureTemplateManager structureTemplateManager,
        long seed,
        ChunkAccess chunk,
        ChunkPos chunkPos,
        SectionPos sectionPos
    ) {
        Structure structure = structureSelectionEntry.structure().value();
        int i = fetchReferences(structureManager, chunk, sectionPos, structure);
        HolderSet<Biome> holderSet = structure.biomes();
        Predicate<Holder<Biome>> predicate = holderSet::contains;
        StructureStart structureStart = structure.generate(
            registryAccess, this, this.biomeSource, random, structureTemplateManager, seed, chunkPos, i, chunk, predicate
        );
        if (structureStart.isValid()) {
            structureManager.setStartForStructure(sectionPos, structure, structureStart, chunk);
            return true;
        } else {
            return false;
        }
    }

    private static int fetchReferences(StructureManager structureManager, ChunkAccess chunk, SectionPos sectionPos, Structure structure) {
        StructureStart structureStart = structureManager.getStartForStructure(sectionPos, structure, chunk);
        return structureStart != null ? structureStart.getReferences() : 0;
    }

    public void createReferences(WorldGenLevel level, StructureManager structureManager, ChunkAccess chunk) {
        int i = 8;
        ChunkPos chunkPos = chunk.getPos();
        int j = chunkPos.x;
        int k = chunkPos.z;
        int l = chunkPos.getMinBlockX();
        int m = chunkPos.getMinBlockZ();
        SectionPos sectionPos = SectionPos.bottomOf(chunk);

        for (int n = j - 8; n <= j + 8; n++) {
            for (int o = k - 8; o <= k + 8; o++) {
                long p = ChunkPos.asLong(n, o);

                for (StructureStart structureStart : level.getChunk(n, o).getAllStarts().values()) {
                    try {
                        if (structureStart.isValid() && structureStart.getBoundingBox().intersects(l, m, l + 15, m + 15)) {
                            structureManager.addReferenceForStructure(sectionPos, structureStart.getStructure(), p, chunk);
                            DebugPackets.sendStructurePacket(level, structureStart);
                        }
                    } catch (Exception exception) {
                        CrashReport crashReport = CrashReport.forThrowable(exception, "Generating structure reference");
                        CrashReportCategory crashReportCategory = crashReport.addCategory("Structure");
                        Optional<? extends Registry<Structure>> optional = level.registryAccess().registry(Registries.STRUCTURE);
                        crashReportCategory.setDetail(
                            "Id", () -> optional.<String>map(registry -> registry.getKey(structureStart.getStructure()).toString()).orElse("UNKNOWN")
                        );
                        crashReportCategory.setDetail("Name", () -> BuiltInRegistries.STRUCTURE_TYPE.getKey(structureStart.getStructure().type()).toString());
                        crashReportCategory.setDetail("Class", () -> structureStart.getStructure().getClass().getCanonicalName());
                        throw new ReportedException(crashReport);
                    }
                }
            }
        }
    }

    public abstract CompletableFuture<ChunkAccess> fillFromNoise(Blender var1, RandomState var2, StructureManager var3, ChunkAccess var4);

    public abstract int getSeaLevel();

    public abstract int getMinY();

    public abstract int getBaseHeight(int var1, int var2, Heightmap.Types var3, LevelHeightAccessor var4, RandomState var5);

    public abstract NoiseColumn getBaseColumn(int var1, int var2, LevelHeightAccessor var3, RandomState var4);

    public int getFirstFreeHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
        return this.getBaseHeight(x, z, type, level, random);
    }

    public int getFirstOccupiedHeight(int x, int z, Heightmap.Types types, LevelHeightAccessor level, RandomState random) {
        return this.getBaseHeight(x, z, types, level, random) - 1;
    }

    public abstract void addDebugScreenInfo(List<String> var1, RandomState var2, BlockPos var3);

    @Deprecated
    public BiomeGenerationSettings getBiomeGenerationSettings(Holder<Biome> biome) {
        return this.generationSettingsGetter.apply(biome);
    }
}
