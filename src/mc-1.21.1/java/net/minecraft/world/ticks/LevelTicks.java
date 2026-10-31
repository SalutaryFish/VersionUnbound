package net.minecraft.world.ticks;

import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongMaps;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.LongPredicate;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public class LevelTicks<T> implements LevelTickAccess<T> {
    private static final Comparator<LevelChunkTicks<?>> CONTAINER_DRAIN_ORDER = (levelChunkTicks, levelChunkTicks2) -> ScheduledTick.INTRA_TICK_DRAIN_ORDER
        .compare(levelChunkTicks.peek(), levelChunkTicks2.peek());
    private final LongPredicate tickCheck;
    private final Supplier<ProfilerFiller> profiler;
    private final Long2ObjectMap<LevelChunkTicks<T>> allContainers = new Long2ObjectOpenHashMap<>();
    private final Long2LongMap nextTickForContainer = Util.make(
        new Long2LongOpenHashMap(), long2LongOpenHashMap -> long2LongOpenHashMap.defaultReturnValue(Long.MAX_VALUE)
    );
    private final Queue<LevelChunkTicks<T>> containersToTick = new PriorityQueue<>(CONTAINER_DRAIN_ORDER);
    private final Queue<ScheduledTick<T>> toRunThisTick = new ArrayDeque<>();
    private final List<ScheduledTick<T>> alreadyRunThisTick = new ArrayList<>();
    private final Set<ScheduledTick<?>> toRunThisTickSet = new ObjectOpenCustomHashSet<>(ScheduledTick.UNIQUE_TICK_HASH);
    private final BiConsumer<LevelChunkTicks<T>, ScheduledTick<T>> chunkScheduleUpdater = (levelChunkTicks, scheduledTick) -> {
        if (scheduledTick.equals(levelChunkTicks.peek())) {
            this.updateContainerScheduling(scheduledTick);
        }
    };

    public LevelTicks(LongPredicate tickCheck, Supplier<ProfilerFiller> profiler) {
        this.tickCheck = tickCheck;
        this.profiler = profiler;
    }

    public void addContainer(ChunkPos chunkPos, LevelChunkTicks<T> chunkTicks) {
        long l = chunkPos.toLong();
        this.allContainers.put(l, chunkTicks);
        ScheduledTick<T> scheduledTick = chunkTicks.peek();
        if (scheduledTick != null) {
            this.nextTickForContainer.put(l, scheduledTick.triggerTick());
        }

        chunkTicks.setOnTickAdded(this.chunkScheduleUpdater);
    }

    public void removeContainer(ChunkPos chunkPos) {
        long l = chunkPos.toLong();
        LevelChunkTicks<T> levelChunkTicks = this.allContainers.remove(l);
        this.nextTickForContainer.remove(l);
        if (levelChunkTicks != null) {
            levelChunkTicks.setOnTickAdded(null);
        }
    }

    @Override
    public void schedule(ScheduledTick<T> tick) {
        long l = ChunkPos.asLong(tick.pos());
        LevelChunkTicks<T> levelChunkTicks = this.allContainers.get(l);
        if (levelChunkTicks == null) {
            Util.pauseInIde(new IllegalStateException("Trying to schedule tick in not loaded position " + tick.pos()));
        } else {
            levelChunkTicks.schedule(tick);
        }
    }

    public void tick(long gameTime, int maxAllowedTicks, BiConsumer<BlockPos, T> ticker) {
        ProfilerFiller profilerFiller = this.profiler.get();
        profilerFiller.push("collect");
        this.collectTicks(gameTime, maxAllowedTicks, profilerFiller);
        profilerFiller.popPush("run");
        profilerFiller.incrementCounter("ticksToRun", this.toRunThisTick.size());
        this.runCollectedTicks(ticker);
        profilerFiller.popPush("cleanup");
        this.cleanupAfterTick();
        profilerFiller.pop();
    }

    private void collectTicks(long gameTime, int maxAllowedTicks, ProfilerFiller profiler) {
        this.sortContainersToTick(gameTime);
        profiler.incrementCounter("containersToTick", this.containersToTick.size());
        this.drainContainers(gameTime, maxAllowedTicks);
        this.rescheduleLeftoverContainers();
    }

    private void sortContainersToTick(long gameTime) {
        ObjectIterator<Entry> objectIterator = Long2LongMaps.fastIterator(this.nextTickForContainer);

        while (objectIterator.hasNext()) {
            Entry entry = objectIterator.next();
            long l = entry.getLongKey();
            long m = entry.getLongValue();
            if (m <= gameTime) {
                LevelChunkTicks<T> levelChunkTicks = this.allContainers.get(l);
                if (levelChunkTicks == null) {
                    objectIterator.remove();
                } else {
                    ScheduledTick<T> scheduledTick = levelChunkTicks.peek();
                    if (scheduledTick == null) {
                        objectIterator.remove();
                    } else if (scheduledTick.triggerTick() > gameTime) {
                        entry.setValue(scheduledTick.triggerTick());
                    } else if (this.tickCheck.test(l)) {
                        objectIterator.remove();
                        this.containersToTick.add(levelChunkTicks);
                    }
                }
            }
        }
    }

    private void drainContainers(long gameTime, int maxAllowedTicks) {
        LevelChunkTicks<T> levelChunkTicks;
        while (this.canScheduleMoreTicks(maxAllowedTicks) && (levelChunkTicks = this.containersToTick.poll()) != null) {
            ScheduledTick<T> scheduledTick = levelChunkTicks.poll();
            this.scheduleForThisTick(scheduledTick);
            this.drainFromCurrentContainer(this.containersToTick, levelChunkTicks, gameTime, maxAllowedTicks);
            ScheduledTick<T> scheduledTick2 = levelChunkTicks.peek();
            if (scheduledTick2 != null) {
                if (scheduledTick2.triggerTick() <= gameTime && this.canScheduleMoreTicks(maxAllowedTicks)) {
                    this.containersToTick.add(levelChunkTicks);
                } else {
                    this.updateContainerScheduling(scheduledTick2);
                }
            }
        }
    }

    private void rescheduleLeftoverContainers() {
        for (LevelChunkTicks<T> levelChunkTicks : this.containersToTick) {
            this.updateContainerScheduling(levelChunkTicks.peek());
        }
    }

    private void updateContainerScheduling(ScheduledTick<T> tick) {
        this.nextTickForContainer.put(ChunkPos.asLong(tick.pos()), tick.triggerTick());
    }

    private void drainFromCurrentContainer(Queue<LevelChunkTicks<T>> containersToTick, LevelChunkTicks<T> levelChunkTicks, long gameTime, int maxAllowedTicks) {
        if (this.canScheduleMoreTicks(maxAllowedTicks)) {
            LevelChunkTicks<T> levelChunkTicks2 = containersToTick.peek();
            ScheduledTick<T> scheduledTick = levelChunkTicks2 != null ? levelChunkTicks2.peek() : null;

            while (this.canScheduleMoreTicks(maxAllowedTicks)) {
                ScheduledTick<T> scheduledTick2 = levelChunkTicks.peek();
                if (scheduledTick2 == null
                    || scheduledTick2.triggerTick() > gameTime
                    || scheduledTick != null && ScheduledTick.INTRA_TICK_DRAIN_ORDER.compare(scheduledTick2, scheduledTick) > 0) {
                    break;
                }

                levelChunkTicks.poll();
                this.scheduleForThisTick(scheduledTick2);
            }
        }
    }

    private void scheduleForThisTick(ScheduledTick<T> tick) {
        this.toRunThisTick.add(tick);
    }

    private boolean canScheduleMoreTicks(int maxAllowedTicks) {
        return this.toRunThisTick.size() < maxAllowedTicks;
    }

    private void runCollectedTicks(BiConsumer<BlockPos, T> ticker) {
        while (!this.toRunThisTick.isEmpty()) {
            ScheduledTick<T> scheduledTick = this.toRunThisTick.poll();
            if (!this.toRunThisTickSet.isEmpty()) {
                this.toRunThisTickSet.remove(scheduledTick);
            }

            this.alreadyRunThisTick.add(scheduledTick);
            ticker.accept(scheduledTick.pos(), scheduledTick.type());
        }
    }

    private void cleanupAfterTick() {
        this.toRunThisTick.clear();
        this.containersToTick.clear();
        this.alreadyRunThisTick.clear();
        this.toRunThisTickSet.clear();
    }

    @Override
    public boolean hasScheduledTick(BlockPos pos, T type) {
        LevelChunkTicks<T> levelChunkTicks = this.allContainers.get(ChunkPos.asLong(pos));
        return levelChunkTicks != null && levelChunkTicks.hasScheduledTick(pos, type);
    }

    @Override
    public boolean willTickThisTick(BlockPos pos, T type) {
        this.calculateTickSetIfNeeded();
        return this.toRunThisTickSet.contains(ScheduledTick.probe(type, pos));
    }

    private void calculateTickSetIfNeeded() {
        if (this.toRunThisTickSet.isEmpty() && !this.toRunThisTick.isEmpty()) {
            this.toRunThisTickSet.addAll(this.toRunThisTick);
        }
    }

    private void forContainersInArea(BoundingBox area, LevelTicks.PosAndContainerConsumer<T> action) {
        int i = SectionPos.posToSectionCoord(area.minX());
        int j = SectionPos.posToSectionCoord(area.minZ());
        int k = SectionPos.posToSectionCoord(area.maxX());
        int l = SectionPos.posToSectionCoord(area.maxZ());

        for (int m = i; m <= k; m++) {
            for (int n = j; n <= l; n++) {
                long o = ChunkPos.asLong(m, n);
                LevelChunkTicks<T> levelChunkTicks = this.allContainers.get(o);
                if (levelChunkTicks != null) {
                    action.accept(o, levelChunkTicks);
                }
            }
        }
    }

    public void clearArea(BoundingBox area) {
        Predicate<ScheduledTick<T>> predicate = scheduledTick -> area.isInside(scheduledTick.pos());
        this.forContainersInArea(area, (l, levelChunkTicks) -> {
            ScheduledTick<T> scheduledTick = levelChunkTicks.peek();
            levelChunkTicks.removeIf(predicate);
            ScheduledTick<T> scheduledTick2 = levelChunkTicks.peek();
            if (scheduledTick2 != scheduledTick) {
                if (scheduledTick2 != null) {
                    this.updateContainerScheduling(scheduledTick2);
                } else {
                    this.nextTickForContainer.remove(l);
                }
            }
        });
        this.alreadyRunThisTick.removeIf(predicate);
        this.toRunThisTick.removeIf(predicate);
    }

    public void copyArea(BoundingBox area, Vec3i offset) {
        this.copyAreaFrom(this, area, offset);
    }

    public void copyAreaFrom(LevelTicks<T> levelTicks, BoundingBox area, Vec3i offset) {
        List<ScheduledTick<T>> list = new ArrayList<>();
        Predicate<ScheduledTick<T>> predicate = scheduledTick -> area.isInside(scheduledTick.pos());
        levelTicks.alreadyRunThisTick.stream().filter(predicate).forEach(list::add);
        levelTicks.toRunThisTick.stream().filter(predicate).forEach(list::add);
        levelTicks.forContainersInArea(area, (lx, levelChunkTicks) -> levelChunkTicks.getAll().filter(predicate).forEach(list::add));
        LongSummaryStatistics longSummaryStatistics = list.stream().mapToLong(ScheduledTick::subTickOrder).summaryStatistics();
        long l = longSummaryStatistics.getMin();
        long m = longSummaryStatistics.getMax();
        list.forEach(
            scheduledTick -> this.schedule(
                new ScheduledTick<>(
                    scheduledTick.type(),
                    scheduledTick.pos().offset(offset),
                    scheduledTick.triggerTick(),
                    scheduledTick.priority(),
                    scheduledTick.subTickOrder() - l + m + 1L
                )
            )
        );
    }

    @Override
    public int count() {
        return this.allContainers.values().stream().mapToInt(TickAccess::count).sum();
    }

    @FunctionalInterface
    interface PosAndContainerConsumer<T> {
        void accept(long var1, LevelChunkTicks<T> var3);
    }
}
