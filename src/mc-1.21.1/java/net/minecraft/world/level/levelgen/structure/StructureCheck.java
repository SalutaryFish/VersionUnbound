package net.minecraft.world.level.levelgen.structure;

import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.Long2BooleanMap;
import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.visitors.CollectFields;
import net.minecraft.nbt.visitors.FieldSelector;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.storage.ChunkScanAccess;
import net.minecraft.world.level.chunk.storage.ChunkStorage;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.slf4j.Logger;

public class StructureCheck {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int NO_STRUCTURE = -1;
    private final ChunkScanAccess storageAccess;
    private final RegistryAccess registryAccess;
    private final StructureTemplateManager structureTemplateManager;
    private final ResourceKey<Level> dimension;
    private final ChunkGenerator chunkGenerator;
    private final RandomState randomState;
    private final LevelHeightAccessor heightAccessor;
    private final BiomeSource biomeSource;
    private final long seed;
    private final DataFixer fixerUpper;
    private final Long2ObjectMap<Object2IntMap<Structure>> loadedChunks = new Long2ObjectOpenHashMap<>();
    private final Map<Structure, Long2BooleanMap> featureChecks = new HashMap<>();

    public StructureCheck(
        ChunkScanAccess storageAccess,
        RegistryAccess registryAccess,
        StructureTemplateManager structureTemplateManager,
        ResourceKey<Level> dimension,
        ChunkGenerator chunkGenerator,
        RandomState randomState,
        LevelHeightAccessor heightAccessor,
        BiomeSource biomeSource,
        long seed,
        DataFixer fixerUpper
    ) {
        this.storageAccess = storageAccess;
        this.registryAccess = registryAccess;
        this.structureTemplateManager = structureTemplateManager;
        this.dimension = dimension;
        this.chunkGenerator = chunkGenerator;
        this.randomState = randomState;
        this.heightAccessor = heightAccessor;
        this.biomeSource = biomeSource;
        this.seed = seed;
        this.fixerUpper = fixerUpper;
    }

    public StructureCheckResult checkStart(ChunkPos chunkPos, Structure structure, StructurePlacement placement, boolean skipKnownStructures) {
        long l = chunkPos.toLong();
        Object2IntMap<Structure> object2IntMap = this.loadedChunks.get(l);
        if (object2IntMap != null) {
            return this.checkStructureInfo(object2IntMap, structure, skipKnownStructures);
        }

        StructureCheckResult structureCheckResult = this.tryLoadFromStorage(chunkPos, structure, skipKnownStructures, l);
        if (structureCheckResult != null) {
            return structureCheckResult;
        }

        if (!placement.applyAdditionalChunkRestrictions(chunkPos.x, chunkPos.z, this.seed)) {
            return StructureCheckResult.START_NOT_PRESENT;
        }

        boolean bl = this.featureChecks
            .computeIfAbsent(structure, structurex -> new Long2BooleanOpenHashMap())
            .computeIfAbsent(l, lx -> this.canCreateStructure(chunkPos, structure));
        return !bl ? StructureCheckResult.START_NOT_PRESENT : StructureCheckResult.CHUNK_LOAD_NEEDED;
    }

    private boolean canCreateStructure(ChunkPos chunkPos, Structure structure) {
        return structure.findValidGenerationPoint(
                new Structure.GenerationContext(
                    this.registryAccess,
                    this.chunkGenerator,
                    this.biomeSource,
                    this.randomState,
                    this.structureTemplateManager,
                    this.seed,
                    chunkPos,
                    this.heightAccessor,
                    structure.biomes()::contains
                )
            )
            .isPresent();
    }

    @Nullable
    private StructureCheckResult tryLoadFromStorage(ChunkPos chunkPos, Structure structure, boolean skipKnownStructures, long packedChunkPos) {
        CollectFields collectFields = new CollectFields(
            new FieldSelector(IntTag.TYPE, "DataVersion"),
            new FieldSelector("Level", "Structures", CompoundTag.TYPE, "Starts"),
            new FieldSelector("structures", CompoundTag.TYPE, "starts")
        );

        try {
            this.storageAccess.scanChunk(chunkPos, collectFields).join();
        } catch (Exception exception) {
            LOGGER.warn("Failed to read chunk {}", chunkPos, exception);
            return StructureCheckResult.CHUNK_LOAD_NEEDED;
        }

        if (!(collectFields.getResult() instanceof CompoundTag compoundTag)) {
            return null;
        } else {
            int i = ChunkStorage.getVersion(compoundTag);
            if (i <= 1493) {
                return StructureCheckResult.CHUNK_LOAD_NEEDED;
            }

            ChunkStorage.injectDatafixingContext(compoundTag, this.dimension, this.chunkGenerator.getTypeNameForDataFixer());

            CompoundTag compoundTag2;
            try {
                compoundTag2 = DataFixTypes.CHUNK.updateToCurrentVersion(this.fixerUpper, compoundTag, i);
            } catch (Exception exception2) {
                LOGGER.warn("Failed to partially datafix chunk {}", chunkPos, exception2);
                return StructureCheckResult.CHUNK_LOAD_NEEDED;
            }

            Object2IntMap<Structure> object2IntMap = this.loadStructures(compoundTag2);
            if (object2IntMap == null) {
                return null;
            }

            this.storeFullResults(packedChunkPos, object2IntMap);
            return this.checkStructureInfo(object2IntMap, structure, skipKnownStructures);
        }
    }

    @Nullable
    private Object2IntMap<Structure> loadStructures(CompoundTag tag) {
        if (!tag.contains("structures", 10)) {
            return null;
        }

        CompoundTag compoundTag = tag.getCompound("structures");
        if (!compoundTag.contains("starts", 10)) {
            return null;
        }

        CompoundTag compoundTag2 = compoundTag.getCompound("starts");
        if (compoundTag2.isEmpty()) {
            return Object2IntMaps.emptyMap();
        }

        Object2IntMap<Structure> object2IntMap = new Object2IntOpenHashMap<>();
        Registry<Structure> registry = this.registryAccess.registryOrThrow(Registries.STRUCTURE);

        for (String string : compoundTag2.getAllKeys()) {
            ResourceLocation resourceLocation = ResourceLocation.tryParse(string);
            if (resourceLocation != null) {
                Structure structure = registry.get(resourceLocation);
                if (structure != null) {
                    CompoundTag compoundTag3 = compoundTag2.getCompound(string);
                    if (!compoundTag3.isEmpty()) {
                        String string2 = compoundTag3.getString("id");
                        if (!"INVALID".equals(string2)) {
                            int i = compoundTag3.getInt("references");
                            object2IntMap.put(structure, i);
                        }
                    }
                }
            }
        }

        return object2IntMap;
    }

    private static Object2IntMap<Structure> deduplicateEmptyMap(Object2IntMap<Structure> map) {
        return map.isEmpty() ? Object2IntMaps.emptyMap() : map;
    }

    private StructureCheckResult checkStructureInfo(Object2IntMap<Structure> structureChunks, Structure structure, boolean skipKnownStructures) {
        int i = structureChunks.getOrDefault(structure, -1);
        return i == -1 || skipKnownStructures && i != 0 ? StructureCheckResult.START_NOT_PRESENT : StructureCheckResult.START_PRESENT;
    }

    public void onStructureLoad(ChunkPos chunkPos, Map<Structure, StructureStart> chunkStarts) {
        long l = chunkPos.toLong();
        Object2IntMap<Structure> object2IntMap = new Object2IntOpenHashMap<>();
        chunkStarts.forEach((structure, structureStart) -> {
            if (structureStart.isValid()) {
                object2IntMap.put(structure, structureStart.getReferences());
            }
        });
        this.storeFullResults(l, object2IntMap);
    }

    private void storeFullResults(long chunkPos, Object2IntMap<Structure> structureChunks) {
        this.loadedChunks.put(chunkPos, deduplicateEmptyMap(structureChunks));
        this.featureChecks.values().forEach(long2BooleanMap -> long2BooleanMap.remove(chunkPos));
    }

    public void incrementReference(ChunkPos pos, Structure structure) {
        this.loadedChunks.compute(pos.toLong(), (long_, object2IntMap) -> {
            if (object2IntMap == null || object2IntMap.isEmpty()) {
                object2IntMap = new Object2IntOpenHashMap<>();
            }

            object2IntMap.computeInt(structure, (structurexx, integer) -> integer == null ? 1 : integer + 1);
            return object2IntMap;
        });
    }
}
