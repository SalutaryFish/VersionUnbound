package net.minecraft.world.level.lighting;

import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import java.util.function.LongPredicate;
import net.minecraft.util.Mth;

public abstract class DynamicGraphMinFixedPoint {
    public static final long SOURCE = Long.MAX_VALUE;
    private static final int NO_COMPUTED_LEVEL = 255;
    protected final int levelCount;
    private final LeveledPriorityQueue priorityQueue;
    private final Long2ByteMap computedLevels;
    private volatile boolean hasWork;

    protected DynamicGraphMinFixedPoint(int firstQueuedLevel, int width, int height) {
        if (firstQueuedLevel >= 254) {
            throw new IllegalArgumentException("Level count must be < 254.");
        }

        this.levelCount = firstQueuedLevel;
        this.priorityQueue = new LeveledPriorityQueue(firstQueuedLevel, width);
        this.computedLevels = new Long2ByteOpenHashMap(height, 0.5F) {
            @Override
            protected void rehash(int newSize) {
                if (newSize > height) {
                    super.rehash(newSize);
                }
            }
        };
        this.computedLevels.defaultReturnValue((byte)-1);
    }

    protected void removeFromQueue(long position) {
        int i = this.computedLevels.remove(position) & 255;
        if (i != 255) {
            int j = this.getLevel(position);
            int k = this.calculatePriority(j, i);
            this.priorityQueue.dequeue(position, k, this.levelCount);
            this.hasWork = !this.priorityQueue.isEmpty();
        }
    }

    public void removeIf(LongPredicate predicate) {
        LongList longList = new LongArrayList();
        this.computedLevels.keySet().forEach(value -> {
            if (predicate.test(value)) {
                longList.add(value);
            }
        });
        longList.forEach(this::removeFromQueue);
    }

    private int calculatePriority(int oldLevel, int newLevel) {
        return Math.min(Math.min(oldLevel, newLevel), this.levelCount - 1);
    }

    protected void checkNode(long levelPos) {
        this.checkEdge(levelPos, levelPos, this.levelCount - 1, false);
    }

    protected void checkEdge(long fromPos, long toPos, int newLevel, boolean isDecreasing) {
        this.checkEdge(fromPos, toPos, newLevel, this.getLevel(toPos), this.computedLevels.get(toPos) & 255, isDecreasing);
        this.hasWork = !this.priorityQueue.isEmpty();
    }

    private void checkEdge(long fromPos, long toPos, int newLevel, int previousLevel, int propagationLevel, boolean isDecreasing) {
        if (!this.isSource(toPos)) {
            newLevel = Mth.clamp(newLevel, 0, this.levelCount - 1);
            previousLevel = Mth.clamp(previousLevel, 0, this.levelCount - 1);
            boolean bl = propagationLevel == 255;
            if (bl) {
                propagationLevel = previousLevel;
            }

            int i;
            if (isDecreasing) {
                i = Math.min(propagationLevel, newLevel);
            } else {
                i = Mth.clamp(this.getComputedLevel(toPos, fromPos, newLevel), 0, this.levelCount - 1);
            }

            int j = this.calculatePriority(previousLevel, propagationLevel);
            if (previousLevel != i) {
                int k = this.calculatePriority(previousLevel, i);
                if (j != k && !bl) {
                    this.priorityQueue.dequeue(toPos, j, k);
                }

                this.priorityQueue.enqueue(toPos, k);
                this.computedLevels.put(toPos, (byte)i);
            } else if (!bl) {
                this.priorityQueue.dequeue(toPos, j, this.levelCount);
                this.computedLevels.remove(toPos);
            }
        }
    }

    protected final void checkNeighbor(long fromPos, long toPos, int sourceLevel, boolean isDecreasing) {
        int i = this.computedLevels.get(toPos) & 255;
        int j = Mth.clamp(this.computeLevelFromNeighbor(fromPos, toPos, sourceLevel), 0, this.levelCount - 1);
        if (isDecreasing) {
            this.checkEdge(fromPos, toPos, j, this.getLevel(toPos), i, isDecreasing);
        } else {
            boolean bl = i == 255;
            int k;
            if (bl) {
                k = Mth.clamp(this.getLevel(toPos), 0, this.levelCount - 1);
            } else {
                k = i;
            }

            if (j == k) {
                this.checkEdge(fromPos, toPos, this.levelCount - 1, bl ? k : this.getLevel(toPos), i, isDecreasing);
            }
        }
    }

    protected final boolean hasWork() {
        return this.hasWork;
    }

    protected final int runUpdates(int toUpdateCount) {
        if (this.priorityQueue.isEmpty()) {
            return toUpdateCount;
        }

        while (!this.priorityQueue.isEmpty() && toUpdateCount > 0) {
            toUpdateCount--;
            long l = this.priorityQueue.removeFirstLong();
            int i = Mth.clamp(this.getLevel(l), 0, this.levelCount - 1);
            int j = this.computedLevels.remove(l) & 255;
            if (j < i) {
                this.setLevel(l, j);
                this.checkNeighborsAfterUpdate(l, j, true);
            } else if (j > i) {
                this.setLevel(l, this.levelCount - 1);
                if (j != this.levelCount - 1) {
                    this.priorityQueue.enqueue(l, this.calculatePriority(this.levelCount - 1, j));
                    this.computedLevels.put(l, (byte)j);
                }

                this.checkNeighborsAfterUpdate(l, i, false);
            }
        }

        this.hasWork = !this.priorityQueue.isEmpty();
        return toUpdateCount;
    }

    public int getQueueSize() {
        return this.computedLevels.size();
    }

    protected boolean isSource(long pos) {
        return pos == Long.MAX_VALUE;
    }

    /**
     * Computes level propagated from neighbors of specified position with given existing level, excluding the given source position.
     */
    protected abstract int getComputedLevel(long var1, long var3, int var5);

    protected abstract void checkNeighborsAfterUpdate(long var1, int var3, boolean var4);

    protected abstract int getLevel(long var1);

    protected abstract void setLevel(long var1, int var3);

    /**
     * Returns level propagated from start position with specified level to the neighboring end position.
     */
    protected abstract int computeLevelFromNeighbor(long var1, long var3, int var5);
}
