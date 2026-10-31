package net.minecraft.client.color.block;

import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import java.util.Arrays;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.ToIntFunction;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;

public class BlockTintCache {
    private static final int MAX_CACHE_ENTRIES = 256;
    private final ThreadLocal<BlockTintCache.LatestCacheInfo> latestChunkOnThread = ThreadLocal.withInitial(BlockTintCache.LatestCacheInfo::new);
    private final Long2ObjectLinkedOpenHashMap<BlockTintCache.CacheData> cache = new Long2ObjectLinkedOpenHashMap<>(256, 0.25F);
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final ToIntFunction<BlockPos> source;

    public BlockTintCache(ToIntFunction<BlockPos> source) {
        this.source = source;
    }

    public int getColor(BlockPos pos) {
        int i = SectionPos.blockToSectionCoord(pos.getX());
        int j = SectionPos.blockToSectionCoord(pos.getZ());
        BlockTintCache.LatestCacheInfo latestCacheInfo = this.latestChunkOnThread.get();
        if (latestCacheInfo.x != i || latestCacheInfo.z != j || latestCacheInfo.cache == null || latestCacheInfo.cache.isInvalidated()) {
            latestCacheInfo.x = i;
            latestCacheInfo.z = j;
            latestCacheInfo.cache = this.findOrCreateChunkCache(i, j);
        }

        int[] is = latestCacheInfo.cache.getLayer(pos.getY());
        int k = pos.getX() & 15;
        int l = pos.getZ() & 15;
        int m = l << 4 | k;
        int n = is[m];
        if (n != -1) {
            return n;
        }

        int o = this.source.applyAsInt(pos);
        is[m] = o;
        return o;
    }

    public void invalidateForChunk(int chunkX, int chunkZ) {
        try {
            this.lock.writeLock().lock();

            for (int i = -1; i <= 1; i++) {
                for (int j = -1; j <= 1; j++) {
                    long l = ChunkPos.asLong(chunkX + i, chunkZ + j);
                    BlockTintCache.CacheData cacheData = this.cache.remove(l);
                    if (cacheData != null) {
                        cacheData.invalidate();
                    }
                }
            }
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    public void invalidateAll() {
        try {
            this.lock.writeLock().lock();
            this.cache.values().forEach(BlockTintCache.CacheData::invalidate);
            this.cache.clear();
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    private BlockTintCache.CacheData findOrCreateChunkCache(int chunkX, int chunkZ) {
        long l = ChunkPos.asLong(chunkX, chunkZ);
        this.lock.readLock().lock();

        try {
            BlockTintCache.CacheData cacheData = this.cache.get(l);
            if (cacheData != null) {
                return cacheData;
            }
        } finally {
            this.lock.readLock().unlock();
        }

        this.lock.writeLock().lock();

        try {
            BlockTintCache.CacheData cacheData = this.cache.get(l);
            if (cacheData != null) {
                return cacheData;
            }

            BlockTintCache.CacheData cacheData2 = new BlockTintCache.CacheData();
            if (this.cache.size() >= 256) {
                BlockTintCache.CacheData cacheData3 = this.cache.removeFirst();
                if (cacheData3 != null) {
                    cacheData3.invalidate();
                }
            }

            this.cache.put(l, cacheData2);
            return cacheData2;
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    static class CacheData {
        private final Int2ObjectArrayMap<int[]> cache = new Int2ObjectArrayMap<>(16);
        private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        private static final int BLOCKS_PER_LAYER = Mth.square(16);
        private volatile boolean invalidated;

        public int[] getLayer(int height) {
            this.lock.readLock().lock();

            try {
                int[] is = this.cache.get(height);
                if (is != null) {
                    return is;
                }
            } finally {
                this.lock.readLock().unlock();
            }

            this.lock.writeLock().lock();

            try {
                return this.cache.computeIfAbsent(height, newHeight -> this.allocateLayer());
            } finally {
                this.lock.writeLock().unlock();
            }
        }

        private int[] allocateLayer() {
            int[] is = new int[BLOCKS_PER_LAYER];
            Arrays.fill(is, -1);
            return is;
        }

        public boolean isInvalidated() {
            return this.invalidated;
        }

        public void invalidate() {
            this.invalidated = true;
        }
    }

    static class LatestCacheInfo {
        public int x = Integer.MIN_VALUE;
        public int z = Integer.MIN_VALUE;
        @Nullable
        BlockTintCache.CacheData cache;

        private LatestCacheInfo() {
        }
    }
}
