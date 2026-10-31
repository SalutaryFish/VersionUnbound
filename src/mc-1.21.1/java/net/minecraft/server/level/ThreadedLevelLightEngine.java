package net.minecraft.server.level;

import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntSupplier;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.thread.ProcessorHandle;
import net.minecraft.util.thread.ProcessorMailbox;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.slf4j.Logger;

public class ThreadedLevelLightEngine extends LevelLightEngine implements AutoCloseable {
    public static final int DEFAULT_BATCH_SIZE = 1000;
    private static final Logger LOGGER = LogUtils.getLogger();
    private final ProcessorMailbox<Runnable> taskMailbox;
    private final ObjectList<Pair<ThreadedLevelLightEngine.TaskType, Runnable>> lightTasks = new ObjectArrayList<>();
    private final ChunkMap chunkMap;
    private final ProcessorHandle<ChunkTaskPriorityQueueSorter.Message<Runnable>> sorterMailbox;
    private final int taskPerBatch = 1000;
    private final AtomicBoolean scheduled = new AtomicBoolean();

    public ThreadedLevelLightEngine(
        LightChunkGetter lightChunk,
        ChunkMap chunkMap,
        boolean skyLight,
        ProcessorMailbox<Runnable> taskMailbox,
        ProcessorHandle<ChunkTaskPriorityQueueSorter.Message<Runnable>> sorterMailbox
    ) {
        super(lightChunk, true, skyLight);
        this.chunkMap = chunkMap;
        this.sorterMailbox = sorterMailbox;
        this.taskMailbox = taskMailbox;
    }

    @Override
    public void close() {
    }

    @Override
    public int runLightUpdates() {
        throw (UnsupportedOperationException)Util.pauseInIde(new UnsupportedOperationException("Ran automatically on a different thread!"));
    }

    @Override
    public void checkBlock(BlockPos pos) {
        BlockPos blockPos = pos.immutable();
        this.addTask(
            SectionPos.blockToSectionCoord(pos.getX()),
            SectionPos.blockToSectionCoord(pos.getZ()),
            ThreadedLevelLightEngine.TaskType.PRE_UPDATE,
            Util.name(() -> super.checkBlock(blockPos), () -> "checkBlock " + blockPos)
        );
    }

    protected void updateChunkStatus(ChunkPos chunkPos) {
        this.addTask(chunkPos.x, chunkPos.z, () -> 0, ThreadedLevelLightEngine.TaskType.PRE_UPDATE, Util.name(() -> {
            super.retainData(chunkPos, false);
            super.setLightEnabled(chunkPos, false);

            for (int i = this.getMinLightSection(); i < this.getMaxLightSection(); i++) {
                super.queueSectionData(LightLayer.BLOCK, SectionPos.of(chunkPos, i), null);
                super.queueSectionData(LightLayer.SKY, SectionPos.of(chunkPos, i), null);
            }

            for (int i = this.levelHeightAccessor.getMinSection(); i < this.levelHeightAccessor.getMaxSection(); i++) {
                super.updateSectionStatus(SectionPos.of(chunkPos, i), true);
            }
        }, () -> "updateChunkStatus " + chunkPos + " true"));
    }

    @Override
    public void updateSectionStatus(SectionPos pos, boolean isEmpty) {
        this.addTask(
            pos.x(),
            pos.z(),
            () -> 0,
            ThreadedLevelLightEngine.TaskType.PRE_UPDATE,
            Util.name(() -> super.updateSectionStatus(pos, isEmpty), () -> "updateSectionStatus " + pos + " " + isEmpty)
        );
    }

    @Override
    public void propagateLightSources(ChunkPos chunkPos) {
        this.addTask(
            chunkPos.x,
            chunkPos.z,
            ThreadedLevelLightEngine.TaskType.PRE_UPDATE,
            Util.name(() -> super.propagateLightSources(chunkPos), () -> "propagateLight " + chunkPos)
        );
    }

    @Override
    public void setLightEnabled(ChunkPos chunkPos, boolean lightEnabled) {
        this.addTask(
            chunkPos.x,
            chunkPos.z,
            ThreadedLevelLightEngine.TaskType.PRE_UPDATE,
            Util.name(() -> super.setLightEnabled(chunkPos, lightEnabled), () -> "enableLight " + chunkPos + " " + lightEnabled)
        );
    }

    @Override
    public void queueSectionData(LightLayer lightLayer, SectionPos sectionPos, @Nullable DataLayer dataLayer) {
        this.addTask(
            sectionPos.x(),
            sectionPos.z(),
            () -> 0,
            ThreadedLevelLightEngine.TaskType.PRE_UPDATE,
            Util.name(() -> super.queueSectionData(lightLayer, sectionPos, dataLayer), () -> "queueData " + sectionPos)
        );
    }

    private void addTask(int chunkX, int chunkZ, ThreadedLevelLightEngine.TaskType type, Runnable task) {
        this.addTask(chunkX, chunkZ, this.chunkMap.getChunkQueueLevel(ChunkPos.asLong(chunkX, chunkZ)), type, task);
    }

    private void addTask(int chunkX, int chunkZ, IntSupplier queueLevelSupplier, ThreadedLevelLightEngine.TaskType type, Runnable task) {
        this.sorterMailbox.tell(ChunkTaskPriorityQueueSorter.message(() -> {
            this.lightTasks.add(Pair.of(type, task));
            if (this.lightTasks.size() >= 1000) {
                this.runUpdate();
            }
        }, ChunkPos.asLong(chunkX, chunkZ), queueLevelSupplier));
    }

    @Override
    public void retainData(ChunkPos pos, boolean retain) {
        this.addTask(
            pos.x, pos.z, () -> 0, ThreadedLevelLightEngine.TaskType.PRE_UPDATE, Util.name(() -> super.retainData(pos, retain), () -> "retainData " + pos)
        );
    }

    public CompletableFuture<ChunkAccess> initializeLight(ChunkAccess chunk, boolean lightEnabled) {
        ChunkPos chunkPos = chunk.getPos();
        this.addTask(chunkPos.x, chunkPos.z, ThreadedLevelLightEngine.TaskType.PRE_UPDATE, Util.name(() -> {
            LevelChunkSection[] levelChunkSections = chunk.getSections();

            for (int i = 0; i < chunk.getSectionsCount(); i++) {
                LevelChunkSection levelChunkSection = levelChunkSections[i];
                if (!levelChunkSection.hasOnlyAir()) {
                    int j = this.levelHeightAccessor.getSectionYFromSectionIndex(i);
                    super.updateSectionStatus(SectionPos.of(chunkPos, j), false);
                }
            }
        }, () -> "initializeLight: " + chunkPos));
        return CompletableFuture.supplyAsync(() -> {
            super.setLightEnabled(chunkPos, lightEnabled);
            super.retainData(chunkPos, false);
            return chunk;
        }, task -> this.addTask(chunkPos.x, chunkPos.z, ThreadedLevelLightEngine.TaskType.POST_UPDATE, task));
    }

    public CompletableFuture<ChunkAccess> lightChunk(ChunkAccess chunk, boolean isLighted) {
        ChunkPos chunkPos = chunk.getPos();
        chunk.setLightCorrect(false);
        this.addTask(chunkPos.x, chunkPos.z, ThreadedLevelLightEngine.TaskType.PRE_UPDATE, Util.name(() -> {
            if (!isLighted) {
                super.propagateLightSources(chunkPos);
            }
        }, () -> "lightChunk " + chunkPos + " " + isLighted));
        return CompletableFuture.supplyAsync(() -> {
            chunk.setLightCorrect(true);
            return chunk;
        }, task -> this.addTask(chunkPos.x, chunkPos.z, ThreadedLevelLightEngine.TaskType.POST_UPDATE, task));
    }

    public void tryScheduleUpdate() {
        if ((!this.lightTasks.isEmpty() || super.hasLightWork()) && this.scheduled.compareAndSet(false, true)) {
            this.taskMailbox.tell(() -> {
                this.runUpdate();
                this.scheduled.set(false);
            });
        }
    }

    private void runUpdate() {
        int i = Math.min(this.lightTasks.size(), 1000);
        ObjectListIterator<Pair<ThreadedLevelLightEngine.TaskType, Runnable>> objectListIterator = this.lightTasks.iterator();

        int j;
        for (j = 0; objectListIterator.hasNext() && j < i; j++) {
            Pair<ThreadedLevelLightEngine.TaskType, Runnable> pair = objectListIterator.next();
            if (pair.getFirst() == ThreadedLevelLightEngine.TaskType.PRE_UPDATE) {
                pair.getSecond().run();
            }
        }

        objectListIterator.back(j);
        super.runLightUpdates();

        for (int var5 = 0; objectListIterator.hasNext() && var5 < i; var5++) {
            Pair<ThreadedLevelLightEngine.TaskType, Runnable> pair = objectListIterator.next();
            if (pair.getFirst() == ThreadedLevelLightEngine.TaskType.POST_UPDATE) {
                pair.getSecond().run();
            }

            objectListIterator.remove();
        }
    }

    public CompletableFuture<?> waitForPendingTasks(int x, int z) {
        return CompletableFuture.runAsync(() -> {}, task -> this.addTask(x, z, ThreadedLevelLightEngine.TaskType.POST_UPDATE, task));
    }

    enum TaskType {
        PRE_UPDATE,
        POST_UPDATE;
    }
}
