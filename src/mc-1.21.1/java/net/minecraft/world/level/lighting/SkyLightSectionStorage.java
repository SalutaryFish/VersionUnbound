package net.minecraft.world.level.lighting;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunkGetter;

public class SkyLightSectionStorage extends LayerLightSectionStorage<SkyLightSectionStorage.SkyDataLayerStorageMap> {
    protected SkyLightSectionStorage(LightChunkGetter chunkSource) {
        super(
            LightLayer.SKY,
            chunkSource,
            new SkyLightSectionStorage.SkyDataLayerStorageMap(new Long2ObjectOpenHashMap<>(), new Long2IntOpenHashMap(), Integer.MAX_VALUE)
        );
    }

    @Override
    protected int getLightValue(long levelPos) {
        return this.getLightValue(levelPos, false);
    }

    protected int getLightValue(long packedPos, boolean updateAll) {
        long l = SectionPos.blockToSection(packedPos);
        int i = SectionPos.y(l);
        SkyLightSectionStorage.SkyDataLayerStorageMap skyDataLayerStorageMap = updateAll ? this.updatingSectionData : this.visibleSectionData;
        int j = skyDataLayerStorageMap.topSections.get(SectionPos.getZeroNode(l));
        if (j != skyDataLayerStorageMap.currentLowestY && i < j) {
            DataLayer dataLayer = this.getDataLayer(skyDataLayerStorageMap, l);
            if (dataLayer == null) {
                packedPos = BlockPos.getFlatIndex(packedPos);

                while (dataLayer == null) {
                    if (++i >= j) {
                        return 15;
                    }

                    l = SectionPos.offset(l, Direction.UP);
                    dataLayer = this.getDataLayer(skyDataLayerStorageMap, l);
                }
            }

            return dataLayer.get(
                SectionPos.sectionRelative(BlockPos.getX(packedPos)),
                SectionPos.sectionRelative(BlockPos.getY(packedPos)),
                SectionPos.sectionRelative(BlockPos.getZ(packedPos))
            );
        } else {
            return updateAll && !this.lightOnInSection(l) ? 0 : 15;
        }
    }

    @Override
    protected void onNodeAdded(long sectionPos) {
        int i = SectionPos.y(sectionPos);
        if (this.updatingSectionData.currentLowestY > i) {
            this.updatingSectionData.currentLowestY = i;
            this.updatingSectionData.topSections.defaultReturnValue(this.updatingSectionData.currentLowestY);
        }

        long l = SectionPos.getZeroNode(sectionPos);
        int j = this.updatingSectionData.topSections.get(l);
        if (j < i + 1) {
            this.updatingSectionData.topSections.put(l, i + 1);
        }
    }

    @Override
    protected void onNodeRemoved(long sectionPos) {
        long l = SectionPos.getZeroNode(sectionPos);
        int i = SectionPos.y(sectionPos);
        if (this.updatingSectionData.topSections.get(l) == i + 1) {
            long m;
            for (m = sectionPos; !this.storingLightForSection(m) && this.hasLightDataAtOrBelow(i); m = SectionPos.offset(m, Direction.DOWN)) {
                i--;
            }

            if (this.storingLightForSection(m)) {
                this.updatingSectionData.topSections.put(l, i + 1);
            } else {
                this.updatingSectionData.topSections.remove(l);
            }
        }
    }

    @Override
    protected DataLayer createDataLayer(long sectionPos) {
        DataLayer dataLayer = this.queuedSections.get(sectionPos);
        if (dataLayer != null) {
            return dataLayer;
        }

        int i = this.updatingSectionData.topSections.get(SectionPos.getZeroNode(sectionPos));
        if (i != this.updatingSectionData.currentLowestY && SectionPos.y(sectionPos) < i) {
            long l = SectionPos.offset(sectionPos, Direction.UP);

            DataLayer dataLayer2;
            while ((dataLayer2 = this.getDataLayer(l, true)) == null) {
                l = SectionPos.offset(l, Direction.UP);
            }

            return repeatFirstLayer(dataLayer2);
        } else {
            return this.lightOnInSection(sectionPos) ? new DataLayer(15) : new DataLayer();
        }
    }

    private static DataLayer repeatFirstLayer(DataLayer dataLayer) {
        if (dataLayer.isDefinitelyHomogenous()) {
            return dataLayer.copy();
        }

        byte[] bs = dataLayer.getData();
        byte[] cs = new byte[2048];

        for (int i = 0; i < 16; i++) {
            System.arraycopy(bs, 0, cs, i * 128, 128);
        }

        return new DataLayer(cs);
    }

    protected boolean hasLightDataAtOrBelow(int y) {
        return y >= this.updatingSectionData.currentLowestY;
    }

    protected boolean isAboveData(long sectionPos) {
        long l = SectionPos.getZeroNode(sectionPos);
        int i = this.updatingSectionData.topSections.get(l);
        return i == this.updatingSectionData.currentLowestY || SectionPos.y(sectionPos) >= i;
    }

    protected int getTopSectionY(long sectionPos) {
        return this.updatingSectionData.topSections.get(sectionPos);
    }

    protected int getBottomSectionY() {
        return this.updatingSectionData.currentLowestY;
    }

    protected static final class SkyDataLayerStorageMap extends DataLayerStorageMap<SkyLightSectionStorage.SkyDataLayerStorageMap> {
        int currentLowestY;
        final Long2IntOpenHashMap topSections;

        public SkyDataLayerStorageMap(Long2ObjectOpenHashMap<DataLayer> map, Long2IntOpenHashMap topSections, int currentLowestY) {
            super(map);
            this.topSections = topSections;
            topSections.defaultReturnValue(currentLowestY);
            this.currentLowestY = currentLowestY;
        }

        public SkyLightSectionStorage.SkyDataLayerStorageMap copy() {
            return new SkyLightSectionStorage.SkyDataLayerStorageMap(this.map.clone(), this.topSections.clone(), this.currentLowestY);
        }
    }
}
