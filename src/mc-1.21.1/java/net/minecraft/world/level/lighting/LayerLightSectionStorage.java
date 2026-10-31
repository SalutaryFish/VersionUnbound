package net.minecraft.world.level.lighting;

import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunkGetter;

public abstract class LayerLightSectionStorage<M extends DataLayerStorageMap<M>> {
    private final LightLayer layer;
    protected final LightChunkGetter chunkSource;
    protected final Long2ByteMap sectionStates = new Long2ByteOpenHashMap();
    private final LongSet columnsWithSources = new LongOpenHashSet();
    protected volatile M visibleSectionData;
    protected final M updatingSectionData;
    protected final LongSet changedSections = new LongOpenHashSet();
    protected final LongSet sectionsAffectedByLightUpdates = new LongOpenHashSet();
    protected final Long2ObjectMap<DataLayer> queuedSections = Long2ObjectMaps.synchronize(new Long2ObjectOpenHashMap<>());
    /**
     * Section column positions (section positions with Y=0) that need to be kept even if some of their sections could otherwise be removed.
     */
    private final LongSet columnsToRetainQueuedDataFor = new LongOpenHashSet();
    /**
     * Set of section positions that can be removed, because their light won't affect any blocks.
     */
    private final LongSet toRemove = new LongOpenHashSet();
    protected volatile boolean hasInconsistencies;

    protected LayerLightSectionStorage(LightLayer layer, LightChunkGetter chunkSource, M updatingSectionData) {
        this.layer = layer;
        this.chunkSource = chunkSource;
        this.updatingSectionData = updatingSectionData;
        this.visibleSectionData = updatingSectionData.copy();
        this.visibleSectionData.disableCache();
        this.sectionStates.defaultReturnValue((byte)0);
    }

    protected boolean storingLightForSection(long sectionPos) {
        return this.getDataLayer(sectionPos, true) != null;
    }

    @Nullable
    protected DataLayer getDataLayer(long sectionPos, boolean cached) {
        return this.getDataLayer(cached ? this.updatingSectionData : this.visibleSectionData, sectionPos);
    }

    @Nullable
    protected DataLayer getDataLayer(M map, long sectionPos) {
        return map.getLayer(sectionPos);
    }

    @Nullable
    protected DataLayer getDataLayerToWrite(long sectionPos) {
        DataLayer dataLayer = this.updatingSectionData.getLayer(sectionPos);
        if (dataLayer == null) {
            return null;
        }

        if (this.changedSections.add(sectionPos)) {
            dataLayer = dataLayer.copy();
            this.updatingSectionData.setLayer(sectionPos, dataLayer);
            this.updatingSectionData.clearCache();
        }

        return dataLayer;
    }

    @Nullable
    public DataLayer getDataLayerData(long sectionPos) {
        DataLayer dataLayer = this.queuedSections.get(sectionPos);
        return dataLayer != null ? dataLayer : this.getDataLayer(sectionPos, false);
    }

    protected abstract int getLightValue(long var1);

    protected int getStoredLevel(long levelPos) {
        long l = SectionPos.blockToSection(levelPos);
        DataLayer dataLayer = this.getDataLayer(l, true);
        return dataLayer.get(
            SectionPos.sectionRelative(BlockPos.getX(levelPos)),
            SectionPos.sectionRelative(BlockPos.getY(levelPos)),
            SectionPos.sectionRelative(BlockPos.getZ(levelPos))
        );
    }

    protected void setStoredLevel(long levelPos, int lightLevel) {
        long l = SectionPos.blockToSection(levelPos);
        DataLayer dataLayer;
        if (this.changedSections.add(l)) {
            dataLayer = this.updatingSectionData.copyDataLayer(l);
        } else {
            dataLayer = this.getDataLayer(l, true);
        }

        dataLayer.set(
            SectionPos.sectionRelative(BlockPos.getX(levelPos)),
            SectionPos.sectionRelative(BlockPos.getY(levelPos)),
            SectionPos.sectionRelative(BlockPos.getZ(levelPos)),
            lightLevel
        );
        SectionPos.aroundAndAtBlockPos(levelPos, this.sectionsAffectedByLightUpdates::add);
    }

    protected void markSectionAndNeighborsAsAffected(long sectionPos) {
        int i = SectionPos.x(sectionPos);
        int j = SectionPos.y(sectionPos);
        int k = SectionPos.z(sectionPos);

        for (int l = -1; l <= 1; l++) {
            for (int m = -1; m <= 1; m++) {
                for (int n = -1; n <= 1; n++) {
                    this.sectionsAffectedByLightUpdates.add(SectionPos.asLong(i + m, j + n, k + l));
                }
            }
        }
    }

    protected DataLayer createDataLayer(long sectionPos) {
        DataLayer dataLayer = this.queuedSections.get(sectionPos);
        return dataLayer != null ? dataLayer : new DataLayer();
    }

    protected boolean hasInconsistencies() {
        return this.hasInconsistencies;
    }

    protected void markNewInconsistencies(LightEngine<M, ?> lightEngine) {
        if (this.hasInconsistencies) {
            this.hasInconsistencies = false;

            for (long l : this.toRemove) {
                DataLayer dataLayer = this.queuedSections.remove(l);
                DataLayer dataLayer2 = this.updatingSectionData.removeLayer(l);
                if (this.columnsToRetainQueuedDataFor.contains(SectionPos.getZeroNode(l))) {
                    if (dataLayer != null) {
                        this.queuedSections.put(l, dataLayer);
                    } else if (dataLayer2 != null) {
                        this.queuedSections.put(l, dataLayer2);
                    }
                }
            }

            this.updatingSectionData.clearCache();

            for (long l : this.toRemove) {
                this.onNodeRemoved(l);
                this.changedSections.add(l);
            }

            this.toRemove.clear();
            ObjectIterator<Entry<DataLayer>> objectIterator = Long2ObjectMaps.fastIterator(this.queuedSections);

            while (objectIterator.hasNext()) {
                Entry<DataLayer> entry = objectIterator.next();
                long m = entry.getLongKey();
                if (this.storingLightForSection(m)) {
                    DataLayer dataLayer2 = entry.getValue();
                    if (this.updatingSectionData.getLayer(m) != dataLayer2) {
                        this.updatingSectionData.setLayer(m, dataLayer2);
                        this.changedSections.add(m);
                    }

                    objectIterator.remove();
                }
            }

            this.updatingSectionData.clearCache();
        }
    }

    protected void onNodeAdded(long sectionPos) {
    }

    protected void onNodeRemoved(long sectionPos) {
    }

    protected void setLightEnabled(long sectionPos, boolean lightEnabled) {
        if (lightEnabled) {
            this.columnsWithSources.add(sectionPos);
        } else {
            this.columnsWithSources.remove(sectionPos);
        }
    }

    protected boolean lightOnInSection(long sectionPos) {
        long l = SectionPos.getZeroNode(sectionPos);
        return this.columnsWithSources.contains(l);
    }

    public void retainData(long sectionColumnPos, boolean retain) {
        if (retain) {
            this.columnsToRetainQueuedDataFor.add(sectionColumnPos);
        } else {
            this.columnsToRetainQueuedDataFor.remove(sectionColumnPos);
        }
    }

    protected void queueSectionData(long sectionPos, @Nullable DataLayer data) {
        if (data != null) {
            this.queuedSections.put(sectionPos, data);
            this.hasInconsistencies = true;
        } else {
            this.queuedSections.remove(sectionPos);
        }
    }

    protected void updateSectionStatus(long sectionPos, boolean isEmpty) {
        byte b = this.sectionStates.get(sectionPos);
        byte c = LayerLightSectionStorage.SectionState.hasData(b, !isEmpty);
        if (b != c) {
            this.putSectionState(sectionPos, c);
            int i = isEmpty ? -1 : 1;

            for (int j = -1; j <= 1; j++) {
                for (int k = -1; k <= 1; k++) {
                    for (int l = -1; l <= 1; l++) {
                        if (j != 0 || k != 0 || l != 0) {
                            long m = SectionPos.offset(sectionPos, j, k, l);
                            byte d = this.sectionStates.get(m);
                            this.putSectionState(
                                m, LayerLightSectionStorage.SectionState.neighborCount(d, LayerLightSectionStorage.SectionState.neighborCount(d) + i)
                            );
                        }
                    }
                }
            }
        }
    }

    protected void putSectionState(long sectionPos, byte sectionState) {
        if (sectionState != 0) {
            if (this.sectionStates.put(sectionPos, sectionState) == 0) {
                this.initializeSection(sectionPos);
            }
        } else if (this.sectionStates.remove(sectionPos) != 0) {
            this.removeSection(sectionPos);
        }
    }

    private void initializeSection(long sectionPos) {
        if (!this.toRemove.remove(sectionPos)) {
            this.updatingSectionData.setLayer(sectionPos, this.createDataLayer(sectionPos));
            this.changedSections.add(sectionPos);
            this.onNodeAdded(sectionPos);
            this.markSectionAndNeighborsAsAffected(sectionPos);
            this.hasInconsistencies = true;
        }
    }

    private void removeSection(long sectionPos) {
        this.toRemove.add(sectionPos);
        this.hasInconsistencies = true;
    }

    protected void swapSectionMap() {
        if (!this.changedSections.isEmpty()) {
            M dataLayerStorageMap = this.updatingSectionData.copy();
            dataLayerStorageMap.disableCache();
            this.visibleSectionData = dataLayerStorageMap;
            this.changedSections.clear();
        }

        if (!this.sectionsAffectedByLightUpdates.isEmpty()) {
            LongIterator longIterator = this.sectionsAffectedByLightUpdates.iterator();

            while (longIterator.hasNext()) {
                long l = longIterator.nextLong();
                this.chunkSource.onLightUpdate(this.layer, SectionPos.of(l));
            }

            this.sectionsAffectedByLightUpdates.clear();
        }
    }

    public LayerLightSectionStorage.SectionType getDebugSectionType(long sectionPos) {
        return LayerLightSectionStorage.SectionState.type(this.sectionStates.get(sectionPos));
    }

    protected static class SectionState {
        public static final byte EMPTY = 0;
        private static final int MIN_NEIGHBORS = 0;
        private static final int MAX_NEIGHBORS = 26;
        private static final byte HAS_DATA_BIT = 32;
        private static final byte NEIGHBOR_COUNT_BITS = 31;

        public static byte hasData(byte sectionState, boolean hasData) {
            return (byte)(hasData ? sectionState | 32 : sectionState & -33);
        }

        public static byte neighborCount(byte sectionState, int neighborCount) {
            if (neighborCount >= 0 && neighborCount <= 26) {
                return (byte)(sectionState & -32 | neighborCount & 31);
            } else {
                throw new IllegalArgumentException("Neighbor count was not within range [0; 26]");
            }
        }

        public static boolean hasData(byte sectionState) {
            return (sectionState & 32) != 0;
        }

        public static int neighborCount(byte sectionState) {
            return sectionState & 31;
        }

        public static LayerLightSectionStorage.SectionType type(byte sectionState) {
            if (sectionState == 0) {
                return LayerLightSectionStorage.SectionType.EMPTY;
            } else {
                return hasData(sectionState) ? LayerLightSectionStorage.SectionType.LIGHT_AND_DATA : LayerLightSectionStorage.SectionType.LIGHT_ONLY;
            }
        }
    }

    public enum SectionType {
        EMPTY("2"),
        LIGHT_ONLY("1"),
        LIGHT_AND_DATA("0");

        private final String display;

        SectionType(final String display) {
            this.display = display;
        }

        public String display() {
            return this.display;
        }
    }
}
