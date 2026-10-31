package net.minecraft.world.level.levelgen.structure;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.DimensionDataStorage;

public class LegacyStructureDataHandler {
    private static final Map<String, String> CURRENT_TO_LEGACY_MAP = Util.make(Maps.newHashMap(), map -> {
        map.put("Village", "Village");
        map.put("Mineshaft", "Mineshaft");
        map.put("Mansion", "Mansion");
        map.put("Igloo", "Temple");
        map.put("Desert_Pyramid", "Temple");
        map.put("Jungle_Pyramid", "Temple");
        map.put("Swamp_Hut", "Temple");
        map.put("Stronghold", "Stronghold");
        map.put("Monument", "Monument");
        map.put("Fortress", "Fortress");
        map.put("EndCity", "EndCity");
    });
    private static final Map<String, String> LEGACY_TO_CURRENT_MAP = Util.make(Maps.newHashMap(), map -> {
        map.put("Iglu", "Igloo");
        map.put("TeDP", "Desert_Pyramid");
        map.put("TeJP", "Jungle_Pyramid");
        map.put("TeSH", "Swamp_Hut");
    });
    private static final Set<String> OLD_STRUCTURE_REGISTRY_KEYS = Set.of(
        "pillager_outpost",
        "mineshaft",
        "mansion",
        "jungle_pyramid",
        "desert_pyramid",
        "igloo",
        "ruined_portal",
        "shipwreck",
        "swamp_hut",
        "stronghold",
        "monument",
        "ocean_ruin",
        "fortress",
        "endcity",
        "buried_treasure",
        "village",
        "nether_fossil",
        "bastion_remnant"
    );
    private final boolean hasLegacyData;
    private final Map<String, Long2ObjectMap<CompoundTag>> dataMap = Maps.newHashMap();
    private final Map<String, StructureFeatureIndexSavedData> indexMap = Maps.newHashMap();
    private final List<String> legacyKeys;
    private final List<String> currentKeys;

    public LegacyStructureDataHandler(@Nullable DimensionDataStorage storage, List<String> legacyKeys, List<String> currentKeys) {
        this.legacyKeys = legacyKeys;
        this.currentKeys = currentKeys;
        this.populateCaches(storage);
        boolean bl = false;

        for (String string : this.currentKeys) {
            bl |= this.dataMap.get(string) != null;
        }

        this.hasLegacyData = bl;
    }

    public void removeIndex(long packedChunkPos) {
        for (String string : this.legacyKeys) {
            StructureFeatureIndexSavedData structureFeatureIndexSavedData = this.indexMap.get(string);
            if (structureFeatureIndexSavedData != null && structureFeatureIndexSavedData.hasUnhandledIndex(packedChunkPos)) {
                structureFeatureIndexSavedData.removeIndex(packedChunkPos);
                structureFeatureIndexSavedData.setDirty();
            }
        }
    }

    public CompoundTag updateFromLegacy(CompoundTag tag) {
        CompoundTag compoundTag = tag.getCompound("Level");
        ChunkPos chunkPos = new ChunkPos(compoundTag.getInt("xPos"), compoundTag.getInt("zPos"));
        if (this.isUnhandledStructureStart(chunkPos.x, chunkPos.z)) {
            tag = this.updateStructureStart(tag, chunkPos);
        }

        CompoundTag compoundTag2 = compoundTag.getCompound("Structures");
        CompoundTag compoundTag3 = compoundTag2.getCompound("References");

        for (String string : this.currentKeys) {
            boolean bl = OLD_STRUCTURE_REGISTRY_KEYS.contains(string.toLowerCase(Locale.ROOT));
            if (!compoundTag3.contains(string, 12) && bl) {
                int i = 8;
                LongList longList = new LongArrayList();

                for (int j = chunkPos.x - 8; j <= chunkPos.x + 8; j++) {
                    for (int k = chunkPos.z - 8; k <= chunkPos.z + 8; k++) {
                        if (this.hasLegacyStart(j, k, string)) {
                            longList.add(ChunkPos.asLong(j, k));
                        }
                    }
                }

                compoundTag3.putLongArray(string, longList);
            }
        }

        compoundTag2.put("References", compoundTag3);
        compoundTag.put("Structures", compoundTag2);
        tag.put("Level", compoundTag);
        return tag;
    }

    private boolean hasLegacyStart(int chunkX, int chunkZ, String key) {
        return !this.hasLegacyData
            ? false
            : this.dataMap.get(key) != null && this.indexMap.get(CURRENT_TO_LEGACY_MAP.get(key)).hasStartIndex(ChunkPos.asLong(chunkX, chunkZ));
    }

    private boolean isUnhandledStructureStart(int chunkX, int chunkZ) {
        if (!this.hasLegacyData) {
            return false;
        }

        for (String string : this.currentKeys) {
            if (this.dataMap.get(string) != null && this.indexMap.get(CURRENT_TO_LEGACY_MAP.get(string)).hasUnhandledIndex(ChunkPos.asLong(chunkX, chunkZ))) {
                return true;
            }
        }

        return false;
    }

    private CompoundTag updateStructureStart(CompoundTag tag, ChunkPos chunkPos) {
        CompoundTag compoundTag = tag.getCompound("Level");
        CompoundTag compoundTag2 = compoundTag.getCompound("Structures");
        CompoundTag compoundTag3 = compoundTag2.getCompound("Starts");

        for (String string : this.currentKeys) {
            Long2ObjectMap<CompoundTag> long2ObjectMap = this.dataMap.get(string);
            if (long2ObjectMap != null) {
                long l = chunkPos.toLong();
                if (this.indexMap.get(CURRENT_TO_LEGACY_MAP.get(string)).hasUnhandledIndex(l)) {
                    CompoundTag compoundTag4 = long2ObjectMap.get(l);
                    if (compoundTag4 != null) {
                        compoundTag3.put(string, compoundTag4);
                    }
                }
            }
        }

        compoundTag2.put("Starts", compoundTag3);
        compoundTag.put("Structures", compoundTag2);
        tag.put("Level", compoundTag);
        return tag;
    }

    private void populateCaches(@Nullable DimensionDataStorage storage) {
        if (storage != null) {
            for (String string : this.legacyKeys) {
                CompoundTag compoundTag = new CompoundTag();

                try {
                    compoundTag = storage.readTagFromDisk(string, DataFixTypes.SAVED_DATA_STRUCTURE_FEATURE_INDICES, 1493)
                        .getCompound("data")
                        .getCompound("Features");
                    if (compoundTag.isEmpty()) {
                        continue;
                    }
                } catch (IOException var13) {
                }

                for (String string2 : compoundTag.getAllKeys()) {
                    CompoundTag compoundTag2 = compoundTag.getCompound(string2);
                    long l = ChunkPos.asLong(compoundTag2.getInt("ChunkX"), compoundTag2.getInt("ChunkZ"));
                    ListTag listTag = compoundTag2.getList("Children", 10);
                    if (!listTag.isEmpty()) {
                        String string3 = listTag.getCompound(0).getString("id");
                        String string4 = LEGACY_TO_CURRENT_MAP.get(string3);
                        if (string4 != null) {
                            compoundTag2.putString("id", string4);
                        }
                    }

                    String string3 = compoundTag2.getString("id");
                    this.dataMap.computeIfAbsent(string3, stringx -> new Long2ObjectOpenHashMap<>()).put(l, compoundTag2);
                }

                String string5 = string + "_index";
                StructureFeatureIndexSavedData structureFeatureIndexSavedData = storage.computeIfAbsent(StructureFeatureIndexSavedData.factory(), string5);
                if (!structureFeatureIndexSavedData.getAll().isEmpty()) {
                    this.indexMap.put(string, structureFeatureIndexSavedData);
                } else {
                    StructureFeatureIndexSavedData structureFeatureIndexSavedData2 = new StructureFeatureIndexSavedData();
                    this.indexMap.put(string, structureFeatureIndexSavedData2);

                    for (String string6 : compoundTag.getAllKeys()) {
                        CompoundTag compoundTag3 = compoundTag.getCompound(string6);
                        structureFeatureIndexSavedData2.addIndex(ChunkPos.asLong(compoundTag3.getInt("ChunkX"), compoundTag3.getInt("ChunkZ")));
                    }

                    structureFeatureIndexSavedData2.setDirty();
                }
            }
        }
    }

    public static LegacyStructureDataHandler getLegacyStructureHandler(ResourceKey<Level> level, @Nullable DimensionDataStorage storage) {
        if (level == Level.OVERWORLD) {
            return new LegacyStructureDataHandler(
                storage,
                ImmutableList.of("Monument", "Stronghold", "Village", "Mineshaft", "Temple", "Mansion"),
                ImmutableList.of("Village", "Mineshaft", "Mansion", "Igloo", "Desert_Pyramid", "Jungle_Pyramid", "Swamp_Hut", "Stronghold", "Monument")
            );
        } else if (level == Level.NETHER) {
            List<String> list = ImmutableList.of("Fortress");
            return new LegacyStructureDataHandler(storage, list, list);
        } else if (level == Level.END) {
            List<String> list = ImmutableList.of("EndCity");
            return new LegacyStructureDataHandler(storage, list, list);
        } else {
            throw new RuntimeException(String.format(Locale.ROOT, "Unknown dimension type : %s", level));
        }
    }
}
