package net.minecraft.server.level;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.lighting.DynamicGraphMinFixedPoint;

public abstract class ChunkTracker extends DynamicGraphMinFixedPoint {
    protected ChunkTracker(int firstQueuedLevel, int width, int height) {
        super(firstQueuedLevel, width, height);
    }

    @Override
    protected boolean isSource(long pos) {
        return pos == ChunkPos.INVALID_CHUNK_POS;
    }

    @Override
    protected void checkNeighborsAfterUpdate(long pos, int level, boolean isDecreasing) {
        if (!isDecreasing || level < this.levelCount - 2) {
            ChunkPos chunkPos = new ChunkPos(pos);
            int i = chunkPos.x;
            int j = chunkPos.z;

            for (int k = -1; k <= 1; k++) {
                for (int l = -1; l <= 1; l++) {
                    long m = ChunkPos.asLong(i + k, j + l);
                    if (m != pos) {
                        this.checkNeighbor(pos, m, level, isDecreasing);
                    }
                }
            }
        }
    }

    /**
     * Computes level propagated from neighbors of specified position with given existing level, excluding the given source position.
     */
    @Override
    protected int getComputedLevel(long pos, long excludedSourcePos, int level) {
        int i = level;
        ChunkPos chunkPos = new ChunkPos(pos);
        int j = chunkPos.x;
        int k = chunkPos.z;

        for (int l = -1; l <= 1; l++) {
            for (int m = -1; m <= 1; m++) {
                long n = ChunkPos.asLong(j + l, k + m);
                if (n == pos) {
                    n = ChunkPos.INVALID_CHUNK_POS;
                }

                if (n != excludedSourcePos) {
                    int o = this.computeLevelFromNeighbor(n, pos, this.getLevel(n));
                    if (i > o) {
                        i = o;
                    }

                    if (i == 0) {
                        return i;
                    }
                }
            }
        }

        return i;
    }

    /**
     * Returns level propagated from start position with specified level to the neighboring end position.
     */
    @Override
    protected int computeLevelFromNeighbor(long startPos, long endPos, int startLevel) {
        return startPos == ChunkPos.INVALID_CHUNK_POS ? this.getLevelFromSource(endPos) : startLevel + 1;
    }

    protected abstract int getLevelFromSource(long var1);

    public void update(long pos, int level, boolean isDecreasing) {
        this.checkEdge(ChunkPos.INVALID_CHUNK_POS, pos, level, isDecreasing);
    }
}
