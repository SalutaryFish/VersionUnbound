package net.minecraft.world.level.chunk.storage;

import com.google.common.collect.Maps;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.shorts.ShortList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Map.Entry;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NbtException;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.ShortTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkType;
import net.minecraft.world.level.levelgen.BelowZeroRetrogen;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.ProtoChunkTicks;
import org.slf4j.Logger;

public class ChunkSerializer {
    private static final Codec<PalettedContainer<BlockState>> BLOCK_STATE_CODEC = PalettedContainer.codecRW(
        Block.BLOCK_STATE_REGISTRY, BlockState.CODEC, PalettedContainer.Strategy.SECTION_STATES, Blocks.AIR.defaultBlockState()
    );
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String TAG_UPGRADE_DATA = "UpgradeData";
    private static final String BLOCK_TICKS_TAG = "block_ticks";
    private static final String FLUID_TICKS_TAG = "fluid_ticks";
    public static final String X_POS_TAG = "xPos";
    public static final String Z_POS_TAG = "zPos";
    public static final String HEIGHTMAPS_TAG = "Heightmaps";
    public static final String IS_LIGHT_ON_TAG = "isLightOn";
    public static final String SECTIONS_TAG = "sections";
    public static final String BLOCK_LIGHT_TAG = "BlockLight";
    public static final String SKY_LIGHT_TAG = "SkyLight";

    public static ProtoChunk read(ServerLevel level, PoiManager poiManager, RegionStorageInfo regionStorageInfo, ChunkPos pos, CompoundTag tag) {
        ChunkPos chunkPos = new ChunkPos(tag.getInt("xPos"), tag.getInt("zPos"));
        if (!Objects.equals(pos, chunkPos)) {
            LOGGER.error("Chunk file at {} is in the wrong location; relocating. (Expected {}, got {})", pos, pos, chunkPos);
            level.getServer().reportMisplacedChunk(chunkPos, pos, regionStorageInfo);
        }

        UpgradeData upgradeData = tag.contains("UpgradeData", 10) ? new UpgradeData(tag.getCompound("UpgradeData"), level) : UpgradeData.EMPTY;
        boolean bl = tag.getBoolean("isLightOn");
        ListTag listTag = tag.getList("sections", 10);
        int i = level.getSectionsCount();
        LevelChunkSection[] levelChunkSections = new LevelChunkSection[i];
        boolean bl2 = level.dimensionType().hasSkyLight();
        ChunkSource chunkSource = level.getChunkSource();
        LevelLightEngine levelLightEngine = chunkSource.getLightEngine();
        Registry<Biome> registry = level.registryAccess().registryOrThrow(Registries.BIOME);
        Codec<PalettedContainerRO<Holder<Biome>>> codec = makeBiomeCodec(registry);
        boolean bl3 = false;

        for (int j = 0; j < listTag.size(); j++) {
            CompoundTag compoundTag = listTag.getCompound(j);
            int k = compoundTag.getByte("Y");
            int l = level.getSectionIndexFromSectionY(k);
            if (l >= 0 && l < levelChunkSections.length) {
                PalettedContainer<BlockState> palettedContainer;
                if (compoundTag.contains("block_states", 10)) {
                    palettedContainer = BLOCK_STATE_CODEC.parse(NbtOps.INSTANCE, compoundTag.getCompound("block_states"))
                        .promotePartial(errorMessage -> logErrors(pos, k, errorMessage))
                        .getOrThrow(ChunkSerializer.ChunkReadException::new);
                } else {
                    palettedContainer = new PalettedContainer<>(
                        Block.BLOCK_STATE_REGISTRY, Blocks.AIR.defaultBlockState(), PalettedContainer.Strategy.SECTION_STATES
                    );
                }

                PalettedContainerRO<Holder<Biome>> palettedContainerRO;
                if (compoundTag.contains("biomes", 10)) {
                    palettedContainerRO = codec.parse(NbtOps.INSTANCE, compoundTag.getCompound("biomes"))
                        .promotePartial(errorMessage -> logErrors(pos, k, errorMessage))
                        .getOrThrow(ChunkSerializer.ChunkReadException::new);
                } else {
                    palettedContainerRO = new PalettedContainer<>(
                        registry.asHolderIdMap(), registry.getHolderOrThrow(Biomes.PLAINS), PalettedContainer.Strategy.SECTION_BIOMES
                    );
                }

                LevelChunkSection levelChunkSection = new LevelChunkSection(palettedContainer, palettedContainerRO);
                levelChunkSections[l] = levelChunkSection;
                SectionPos sectionPos = SectionPos.of(pos, k);
                poiManager.checkConsistencyWithBlocks(sectionPos, levelChunkSection);
            }

            boolean bl4 = compoundTag.contains("BlockLight", 7);
            boolean bl5 = bl2 && compoundTag.contains("SkyLight", 7);
            if (bl4 || bl5) {
                if (!bl3) {
                    levelLightEngine.retainData(pos, true);
                    bl3 = true;
                }

                if (bl4) {
                    levelLightEngine.queueSectionData(LightLayer.BLOCK, SectionPos.of(pos, k), new DataLayer(compoundTag.getByteArray("BlockLight")));
                }

                if (bl5) {
                    levelLightEngine.queueSectionData(LightLayer.SKY, SectionPos.of(pos, k), new DataLayer(compoundTag.getByteArray("SkyLight")));
                }
            }
        }

        long m = tag.getLong("InhabitedTime");
        ChunkType chunkType = getChunkTypeFromTag(tag);
        BlendingData blendingData;
        if (tag.contains("blending_data", 10)) {
            blendingData = BlendingData.CODEC
                .parse(new Dynamic<>(NbtOps.INSTANCE, tag.getCompound("blending_data")))
                .resultOrPartial(LOGGER::error)
                .orElse(null);
        } else {
            blendingData = null;
        }

        ChunkAccess chunkAccess;
        if (chunkType == ChunkType.LEVELCHUNK) {
            LevelChunkTicks<Block> levelChunkTicks = LevelChunkTicks.load(
                tag.getList("block_ticks", 10), id -> BuiltInRegistries.BLOCK.getOptional(ResourceLocation.tryParse(id)), pos
            );
            LevelChunkTicks<Fluid> levelChunkTicks2 = LevelChunkTicks.load(
                tag.getList("fluid_ticks", 10), id -> BuiltInRegistries.FLUID.getOptional(ResourceLocation.tryParse(id)), pos
            );
            chunkAccess = new LevelChunk(
                level.getLevel(), pos, upgradeData, levelChunkTicks, levelChunkTicks2, m, levelChunkSections, postLoadChunk(level, tag), blendingData
            );
        } else {
            ProtoChunkTicks<Block> protoChunkTicks = ProtoChunkTicks.load(
                tag.getList("block_ticks", 10), id -> BuiltInRegistries.BLOCK.getOptional(ResourceLocation.tryParse(id)), pos
            );
            ProtoChunkTicks<Fluid> protoChunkTicks2 = ProtoChunkTicks.load(
                tag.getList("fluid_ticks", 10), id -> BuiltInRegistries.FLUID.getOptional(ResourceLocation.tryParse(id)), pos
            );
            ProtoChunk protoChunk = new ProtoChunk(pos, upgradeData, levelChunkSections, protoChunkTicks, protoChunkTicks2, level, registry, blendingData);
            chunkAccess = protoChunk;
            chunkAccess.setInhabitedTime(m);
            if (tag.contains("below_zero_retrogen", 10)) {
                BelowZeroRetrogen.CODEC
                    .parse(new Dynamic<>(NbtOps.INSTANCE, tag.getCompound("below_zero_retrogen")))
                    .resultOrPartial(LOGGER::error)
                    .ifPresent(protoChunk::setBelowZeroRetrogen);
            }

            ChunkStatus chunkStatus = ChunkStatus.byName(tag.getString("Status"));
            protoChunk.setPersistedStatus(chunkStatus);
            if (chunkStatus.isOrAfter(ChunkStatus.INITIALIZE_LIGHT)) {
                protoChunk.setLightEngine(levelLightEngine);
            }
        }

        chunkAccess.setLightCorrect(bl);
        CompoundTag compoundTag2 = tag.getCompound("Heightmaps");
        EnumSet<Heightmap.Types> enumSet = EnumSet.noneOf(Heightmap.Types.class);

        for (Heightmap.Types types : chunkAccess.getPersistedStatus().heightmapsAfter()) {
            String string = types.getSerializationKey();
            if (compoundTag2.contains(string, 12)) {
                chunkAccess.setHeightmap(types, compoundTag2.getLongArray(string));
            } else {
                enumSet.add(types);
            }
        }

        Heightmap.primeHeightmaps(chunkAccess, enumSet);
        CompoundTag compoundTag3 = tag.getCompound("structures");
        chunkAccess.setAllStarts(unpackStructureStart(StructurePieceSerializationContext.fromLevel(level), compoundTag3, level.getSeed()));
        chunkAccess.setAllReferences(unpackStructureReferences(level.registryAccess(), pos, compoundTag3));
        if (tag.getBoolean("shouldSave")) {
            chunkAccess.setUnsaved(true);
        }

        ListTag listTag2 = tag.getList("PostProcessing", 9);

        for (int n = 0; n < listTag2.size(); n++) {
            ListTag listTag3 = listTag2.getList(n);

            for (int o = 0; o < listTag3.size(); o++) {
                chunkAccess.addPackedPostProcess(listTag3.getShort(o), n);
            }
        }

        if (chunkType == ChunkType.LEVELCHUNK) {
            return new ImposterProtoChunk((LevelChunk)chunkAccess, false);
        }

        ProtoChunk protoChunk2 = (ProtoChunk)chunkAccess;
        ListTag listTag3 = tag.getList("entities", 10);

        for (int o = 0; o < listTag3.size(); o++) {
            protoChunk2.addEntity(listTag3.getCompound(o));
        }

        ListTag listTag4 = tag.getList("block_entities", 10);

        for (int p = 0; p < listTag4.size(); p++) {
            CompoundTag compoundTag4 = listTag4.getCompound(p);
            chunkAccess.setBlockEntityNbt(compoundTag4);
        }

        CompoundTag compoundTag5 = tag.getCompound("CarvingMasks");

        for (String string2 : compoundTag5.getAllKeys()) {
            GenerationStep.Carving carving = GenerationStep.Carving.valueOf(string2);
            protoChunk2.setCarvingMask(carving, new CarvingMask(compoundTag5.getLongArray(string2), chunkAccess.getMinBuildHeight()));
        }

        return protoChunk2;
    }

    private static void logErrors(ChunkPos chunkPos, int chunkSectionY, String errorMessage) {
        LOGGER.error("Recoverable errors when loading section [{}, {}, {}]: {}", chunkPos.x, chunkSectionY, chunkPos.z, errorMessage);
    }

    private static Codec<PalettedContainerRO<Holder<Biome>>> makeBiomeCodec(Registry<Biome> biomeRegistry) {
        return PalettedContainer.codecRO(
            biomeRegistry.asHolderIdMap(),
            biomeRegistry.holderByNameCodec(),
            PalettedContainer.Strategy.SECTION_BIOMES,
            biomeRegistry.getHolderOrThrow(Biomes.PLAINS)
        );
    }

    public static CompoundTag write(ServerLevel level, ChunkAccess chunk) {
        ChunkPos chunkPos = chunk.getPos();
        CompoundTag compoundTag = NbtUtils.addCurrentDataVersion(new CompoundTag());
        compoundTag.putInt("xPos", chunkPos.x);
        compoundTag.putInt("yPos", chunk.getMinSection());
        compoundTag.putInt("zPos", chunkPos.z);
        compoundTag.putLong("LastUpdate", level.getGameTime());
        compoundTag.putLong("InhabitedTime", chunk.getInhabitedTime());
        compoundTag.putString("Status", BuiltInRegistries.CHUNK_STATUS.getKey(chunk.getPersistedStatus()).toString());
        BlendingData blendingData = chunk.getBlendingData();
        if (blendingData != null) {
            BlendingData.CODEC
                .encodeStart(NbtOps.INSTANCE, blendingData)
                .resultOrPartial(LOGGER::error)
                .ifPresent(data -> compoundTag.put("blending_data", data));
        }

        BelowZeroRetrogen belowZeroRetrogen = chunk.getBelowZeroRetrogen();
        if (belowZeroRetrogen != null) {
            BelowZeroRetrogen.CODEC
                .encodeStart(NbtOps.INSTANCE, belowZeroRetrogen)
                .resultOrPartial(LOGGER::error)
                .ifPresent(belowZeroRetrogenx -> compoundTag.put("below_zero_retrogen", belowZeroRetrogenx));
        }

        UpgradeData upgradeData = chunk.getUpgradeData();
        if (!upgradeData.isEmpty()) {
            compoundTag.put("UpgradeData", upgradeData.write());
        }

        LevelChunkSection[] levelChunkSections = chunk.getSections();
        ListTag listTag = new ListTag();
        LevelLightEngine levelLightEngine = level.getChunkSource().getLightEngine();
        Registry<Biome> registry = level.registryAccess().registryOrThrow(Registries.BIOME);
        Codec<PalettedContainerRO<Holder<Biome>>> codec = makeBiomeCodec(registry);
        boolean bl = chunk.isLightCorrect();

        for (int i = levelLightEngine.getMinLightSection(); i < levelLightEngine.getMaxLightSection(); i++) {
            int j = chunk.getSectionIndexFromSectionY(i);
            boolean bl2 = j >= 0 && j < levelChunkSections.length;
            DataLayer dataLayer = levelLightEngine.getLayerListener(LightLayer.BLOCK).getDataLayerData(SectionPos.of(chunkPos, i));
            DataLayer dataLayer2 = levelLightEngine.getLayerListener(LightLayer.SKY).getDataLayerData(SectionPos.of(chunkPos, i));
            if (bl2 || dataLayer != null || dataLayer2 != null) {
                CompoundTag compoundTag2 = new CompoundTag();
                if (bl2) {
                    LevelChunkSection levelChunkSection = levelChunkSections[j];
                    compoundTag2.put("block_states", BLOCK_STATE_CODEC.encodeStart(NbtOps.INSTANCE, levelChunkSection.getStates()).getOrThrow());
                    compoundTag2.put("biomes", codec.encodeStart(NbtOps.INSTANCE, levelChunkSection.getBiomes()).getOrThrow());
                }

                if (dataLayer != null && !dataLayer.isEmpty()) {
                    compoundTag2.putByteArray("BlockLight", dataLayer.getData());
                }

                if (dataLayer2 != null && !dataLayer2.isEmpty()) {
                    compoundTag2.putByteArray("SkyLight", dataLayer2.getData());
                }

                if (!compoundTag2.isEmpty()) {
                    compoundTag2.putByte("Y", (byte)i);
                    listTag.add(compoundTag2);
                }
            }
        }

        compoundTag.put("sections", listTag);
        if (bl) {
            compoundTag.putBoolean("isLightOn", true);
        }

        ListTag listTag2 = new ListTag();

        for (BlockPos blockPos : chunk.getBlockEntitiesPos()) {
            CompoundTag compoundTag3 = chunk.getBlockEntityNbtForSaving(blockPos, level.registryAccess());
            if (compoundTag3 != null) {
                listTag2.add(compoundTag3);
            }
        }

        compoundTag.put("block_entities", listTag2);
        if (chunk.getPersistedStatus().getChunkType() == ChunkType.PROTOCHUNK) {
            ProtoChunk protoChunk = (ProtoChunk)chunk;
            ListTag listTag3 = new ListTag();
            listTag3.addAll(protoChunk.getEntities());
            compoundTag.put("entities", listTag3);
            CompoundTag compoundTag3 = new CompoundTag();

            for (GenerationStep.Carving carving : GenerationStep.Carving.values()) {
                CarvingMask carvingMask = protoChunk.getCarvingMask(carving);
                if (carvingMask != null) {
                    compoundTag3.putLongArray(carving.toString(), carvingMask.toArray());
                }
            }

            compoundTag.put("CarvingMasks", compoundTag3);
        }

        saveTicks(level, compoundTag, chunk.getTicksForSerialization());
        compoundTag.put("PostProcessing", packOffsets(chunk.getPostProcessing()));
        CompoundTag compoundTag4 = new CompoundTag();

        for (Entry<Heightmap.Types, Heightmap> entry : chunk.getHeightmaps()) {
            if (chunk.getPersistedStatus().heightmapsAfter().contains(entry.getKey())) {
                compoundTag4.put(entry.getKey().getSerializationKey(), new LongArrayTag(entry.getValue().getRawData()));
            }
        }

        compoundTag.put("Heightmaps", compoundTag4);
        compoundTag.put(
            "structures", packStructureData(StructurePieceSerializationContext.fromLevel(level), chunkPos, chunk.getAllStarts(), chunk.getAllReferences())
        );
        return compoundTag;
    }

    private static void saveTicks(ServerLevel level, CompoundTag tag, ChunkAccess.TicksToSave ticksToSave) {
        long l = level.getLevelData().getGameTime();
        tag.put("block_ticks", ticksToSave.blocks().save(l, block -> BuiltInRegistries.BLOCK.getKey(block).toString()));
        tag.put("fluid_ticks", ticksToSave.fluids().save(l, fluid -> BuiltInRegistries.FLUID.getKey(fluid).toString()));
    }

    public static ChunkType getChunkTypeFromTag(@Nullable CompoundTag tag) {
        return tag != null ? ChunkStatus.byName(tag.getString("Status")).getChunkType() : ChunkType.PROTOCHUNK;
    }

    @Nullable
    private static LevelChunk.PostLoadProcessor postLoadChunk(ServerLevel level, CompoundTag tag) {
        ListTag listTag = getListOfCompoundsOrNull(tag, "entities");
        ListTag listTag2 = getListOfCompoundsOrNull(tag, "block_entities");
        return listTag == null && listTag2 == null ? null : chunk -> {
            if (listTag != null) {
                level.addLegacyChunkEntities(EntityType.loadEntitiesRecursive(listTag, level));
            }

            if (listTag2 != null) {
                for (int i = 0; i < listTag2.size(); i++) {
                    CompoundTag compoundTag = listTag2.getCompound(i);
                    boolean bl = compoundTag.getBoolean("keepPacked");
                    if (bl) {
                        chunk.setBlockEntityNbt(compoundTag);
                    } else {
                        BlockPos blockPos = BlockEntity.getPosFromTag(compoundTag);
                        BlockEntity blockEntity = BlockEntity.loadStatic(blockPos, chunk.getBlockState(blockPos), compoundTag, level.registryAccess());
                        if (blockEntity != null) {
                            chunk.setBlockEntity(blockEntity);
                        }
                    }
                }
            }
        };
    }

    @Nullable
    private static ListTag getListOfCompoundsOrNull(CompoundTag tag, String key) {
        ListTag listTag = tag.getList(key, 10);
        return listTag.isEmpty() ? null : listTag;
    }

    private static CompoundTag packStructureData(
        StructurePieceSerializationContext context, ChunkPos pos, Map<Structure, StructureStart> structureMap, Map<Structure, LongSet> referenceMap
    ) {
        CompoundTag compoundTag = new CompoundTag();
        CompoundTag compoundTag2 = new CompoundTag();
        Registry<Structure> registry = context.registryAccess().registryOrThrow(Registries.STRUCTURE);

        for (Entry<Structure, StructureStart> entry : structureMap.entrySet()) {
            ResourceLocation resourceLocation = registry.getKey(entry.getKey());
            compoundTag2.put(resourceLocation.toString(), entry.getValue().createTag(context, pos));
        }

        compoundTag.put("starts", compoundTag2);
        CompoundTag compoundTag3 = new CompoundTag();

        for (Entry<Structure, LongSet> entry2 : referenceMap.entrySet()) {
            if (!entry2.getValue().isEmpty()) {
                ResourceLocation resourceLocation2 = registry.getKey(entry2.getKey());
                compoundTag3.put(resourceLocation2.toString(), new LongArrayTag(entry2.getValue()));
            }
        }

        compoundTag.put("References", compoundTag3);
        return compoundTag;
    }

    private static Map<Structure, StructureStart> unpackStructureStart(StructurePieceSerializationContext context, CompoundTag tag, long seed) {
        Map<Structure, StructureStart> map = Maps.newHashMap();
        Registry<Structure> registry = context.registryAccess().registryOrThrow(Registries.STRUCTURE);
        CompoundTag compoundTag = tag.getCompound("starts");

        for (String string : compoundTag.getAllKeys()) {
            ResourceLocation resourceLocation = ResourceLocation.tryParse(string);
            Structure structure = registry.get(resourceLocation);
            if (structure == null) {
                LOGGER.error("Unknown structure start: {}", resourceLocation);
            } else {
                StructureStart structureStart = StructureStart.loadStaticStart(context, compoundTag.getCompound(string), seed);
                if (structureStart != null) {
                    map.put(structure, structureStart);
                }
            }
        }

        return map;
    }

    private static Map<Structure, LongSet> unpackStructureReferences(RegistryAccess registryAccess, ChunkPos pos, CompoundTag tag) {
        Map<Structure, LongSet> map = Maps.newHashMap();
        Registry<Structure> registry = registryAccess.registryOrThrow(Registries.STRUCTURE);
        CompoundTag compoundTag = tag.getCompound("References");

        for (String string : compoundTag.getAllKeys()) {
            ResourceLocation resourceLocation = ResourceLocation.tryParse(string);
            Structure structure = registry.get(resourceLocation);
            if (structure == null) {
                LOGGER.warn("Found reference to unknown structure '{}' in chunk {}, discarding", resourceLocation, pos);
            } else {
                long[] ls = compoundTag.getLongArray(string);
                if (ls.length != 0) {
                    map.put(structure, new LongOpenHashSet(Arrays.stream(ls).filter(longPosition -> {
                        ChunkPos chunkPos2 = new ChunkPos(longPosition);
                        if (chunkPos2.getChessboardDistance(pos) > 8) {
                            LOGGER.warn("Found invalid structure reference [ {} @ {} ] for chunk {}.", resourceLocation, chunkPos2, pos);
                            return false;
                        } else {
                            return true;
                        }
                    }).toArray()));
                }
            }
        }

        return map;
    }

    public static ListTag packOffsets(ShortList[] list) {
        ListTag listTag = new ListTag();

        for (ShortList shortList : list) {
            ListTag listTag2 = new ListTag();
            if (shortList != null) {
                for (Short short_ : shortList) {
                    listTag2.add(ShortTag.valueOf(short_));
                }
            }

            listTag.add(listTag2);
        }

        return listTag;
    }

    public static class ChunkReadException extends NbtException {
        public ChunkReadException(String message) {
            super(message);
        }
    }
}
