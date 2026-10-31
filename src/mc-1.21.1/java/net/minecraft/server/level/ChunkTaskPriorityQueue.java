package net.minecraft.server.level;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Either;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.world.level.ChunkPos;

public class ChunkTaskPriorityQueue<T> {
    public static final int PRIORITY_LEVEL_COUNT = ChunkLevel.MAX_LEVEL + 2;
    private final List<Long2ObjectLinkedOpenHashMap<List<Optional<T>>>> taskQueue = IntStream.range(0, PRIORITY_LEVEL_COUNT)
        .mapToObj(i -> new Long2ObjectLinkedOpenHashMap())
        .collect(Collectors.toList());
    private volatile int firstQueue = PRIORITY_LEVEL_COUNT;
    private final String name;
    private final LongSet acquired = new LongOpenHashSet();
    private final int maxTasks;

    public ChunkTaskPriorityQueue(String name, int maxTasks) {
        this.name = name;
        this.maxTasks = maxTasks;
    }

    protected void resortChunkTasks(int queueLevel, ChunkPos chunkPos, int ticketLevel) {
        if (queueLevel < PRIORITY_LEVEL_COUNT) {
            Long2ObjectLinkedOpenHashMap<List<Optional<T>>> long2ObjectLinkedOpenHashMap = this.taskQueue.get(queueLevel);
            List<Optional<T>> list = long2ObjectLinkedOpenHashMap.remove(chunkPos.toLong());
            if (queueLevel == this.firstQueue) {
                while (this.hasWork() && this.taskQueue.get(this.firstQueue).isEmpty()) {
                    this.firstQueue++;
                }
            }

            if (list != null && !list.isEmpty()) {
                this.taskQueue.get(ticketLevel).computeIfAbsent(chunkPos.toLong(), chunkPosx -> Lists.newArrayList()).addAll(list);
                this.firstQueue = Math.min(this.firstQueue, ticketLevel);
            }
        }
    }

    protected void submit(Optional<T> task, long chunkPos, int chunkLevel) {
        this.taskQueue.get(chunkLevel).computeIfAbsent(chunkPos, chunkPosx -> Lists.newArrayList()).add(task);
        this.firstQueue = Math.min(this.firstQueue, chunkLevel);
    }

    protected void release(long chunkPos, boolean fullClear) {
        for (Long2ObjectLinkedOpenHashMap<List<Optional<T>>> long2ObjectLinkedOpenHashMap : this.taskQueue) {
            List<Optional<T>> list = long2ObjectLinkedOpenHashMap.get(chunkPos);
            if (list != null) {
                if (fullClear) {
                    list.clear();
                } else {
                    list.removeIf(queues -> queues.isEmpty());
                }

                if (list.isEmpty()) {
                    long2ObjectLinkedOpenHashMap.remove(chunkPos);
                }
            }
        }

        while (this.hasWork() && this.taskQueue.get(this.firstQueue).isEmpty()) {
            this.firstQueue++;
        }

        this.acquired.remove(chunkPos);
    }

    private Runnable acquire(long chunkPos) {
        return () -> this.acquired.add(chunkPos);
    }

    @Nullable
    public Stream<Either<T, Runnable>> pop() {
        if (this.acquired.size() >= this.maxTasks) {
            return null;
        }

        if (!this.hasWork()) {
            return null;
        }

        int i = this.firstQueue;
        Long2ObjectLinkedOpenHashMap<List<Optional<T>>> long2ObjectLinkedOpenHashMap = this.taskQueue.get(i);
        long l = long2ObjectLinkedOpenHashMap.firstLongKey();
        List<Optional<T>> list = long2ObjectLinkedOpenHashMap.removeFirst();

        while (this.hasWork() && this.taskQueue.get(this.firstQueue).isEmpty()) {
            this.firstQueue++;
        }

        return list.stream().map(optional -> optional.map(Either::left).orElseGet(() -> Either.right(this.acquire(l))));
    }

    public boolean hasWork() {
        return this.firstQueue < PRIORITY_LEVEL_COUNT;
    }

    @Override
    public String toString() {
        return this.name + " " + this.firstQueue + "...";
    }

    @VisibleForTesting
    LongSet getAcquired() {
        return new LongOpenHashSet(this.acquired);
    }
}
