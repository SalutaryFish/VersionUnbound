package net.minecraft.server.level;

import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.ReportedException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.StaticCache2D;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStep;

public abstract class GenerationChunkHolder {
    private static final List<ChunkStatus> CHUNK_STATUSES = ChunkStatus.getStatusList();
    private static final ChunkResult<ChunkAccess> NOT_DONE_YET = ChunkResult.error("Not done yet");
    public static final ChunkResult<ChunkAccess> UNLOADED_CHUNK = ChunkResult.error("Unloaded chunk");
    public static final CompletableFuture<ChunkResult<ChunkAccess>> UNLOADED_CHUNK_FUTURE = CompletableFuture.completedFuture(UNLOADED_CHUNK);
    protected final ChunkPos pos;
    @Nullable
    private volatile ChunkStatus highestAllowedStatus;
    private final AtomicReference<ChunkStatus> startedWork = new AtomicReference<>();
    private final AtomicReferenceArray<CompletableFuture<ChunkResult<ChunkAccess>>> futures = new AtomicReferenceArray<>(CHUNK_STATUSES.size());
    private final AtomicReference<ChunkGenerationTask> task = new AtomicReference<>();
    private final AtomicInteger generationRefCount = new AtomicInteger();

    public GenerationChunkHolder(ChunkPos pos) {
        this.pos = pos;
    }

    public CompletableFuture<ChunkResult<ChunkAccess>> scheduleChunkGenerationTask(ChunkStatus targetStatus, ChunkMap chunkMap) {
        if (this.isStatusDisallowed(targetStatus)) {
            return UNLOADED_CHUNK_FUTURE;
        }

        CompletableFuture<ChunkResult<ChunkAccess>> completableFuture = this.getOrCreateFuture(targetStatus);
        if (completableFuture.isDone()) {
            return completableFuture;
        }

        ChunkGenerationTask chunkGenerationTask = this.task.get();
        if (chunkGenerationTask == null || targetStatus.isAfter(chunkGenerationTask.targetStatus)) {
            this.rescheduleChunkTask(chunkMap, targetStatus);
        }

        return completableFuture;
    }

    CompletableFuture<ChunkResult<ChunkAccess>> applyStep(ChunkStep step, GeneratingChunkMap chunkMap, StaticCache2D<GenerationChunkHolder> cache) {
        if (this.isStatusDisallowed(step.targetStatus())) {
            return UNLOADED_CHUNK_FUTURE;
        } else {
            return this.acquireStatusBump(step.targetStatus()) ? chunkMap.applyStep(this, step, cache).handle((chunkAccess, throwable) -> {
                if (throwable != null) {
                    CrashReport crashReport = CrashReport.forThrowable(throwable, "Exception chunk generation/loading");
                    MinecraftServer.setFatalException(new ReportedException(crashReport));
                } else {
                    this.completeFuture(step.targetStatus(), chunkAccess);
                }

                return ChunkResult.of(chunkAccess);
            }) : this.getOrCreateFuture(step.targetStatus());
        }
    }

    protected void updateHighestAllowedStatus(ChunkMap chunkMap) {
        ChunkStatus chunkStatus = this.highestAllowedStatus;
        ChunkStatus chunkStatus2 = ChunkLevel.generationStatus(this.getTicketLevel());
        this.highestAllowedStatus = chunkStatus2;
        boolean bl = chunkStatus != null && (chunkStatus2 == null || chunkStatus2.isBefore(chunkStatus));
        if (bl) {
            this.failAndClearPendingFuturesBetween(chunkStatus2, chunkStatus);
            if (this.task.get() != null) {
                this.rescheduleChunkTask(chunkMap, this.findHighestStatusWithPendingFuture(chunkStatus2));
            }
        }
    }

    public void replaceProtoChunk(ImposterProtoChunk chunk) {
        CompletableFuture<ChunkResult<ChunkAccess>> completableFuture = CompletableFuture.completedFuture(ChunkResult.of(chunk));

        for (int i = 0; i < this.futures.length() - 1; i++) {
            CompletableFuture<ChunkResult<ChunkAccess>> completableFuture2 = this.futures.get(i);
            Objects.requireNonNull(completableFuture2);
            ChunkAccess chunkAccess = completableFuture2.getNow(NOT_DONE_YET).orElse(null);
            if (!(chunkAccess instanceof ProtoChunk)) {
                throw new IllegalStateException("Trying to replace a ProtoChunk, but found " + chunkAccess);
            }

            if (!this.futures.compareAndSet(i, completableFuture2, completableFuture)) {
                throw new IllegalStateException("Future changed by other thread while trying to replace it");
            }
        }
    }

    void removeTask(ChunkGenerationTask task) {
        this.task.compareAndSet(task, null);
    }

    private void rescheduleChunkTask(ChunkMap chunkMap, @Nullable ChunkStatus targetStatus) {
        ChunkGenerationTask chunkGenerationTask;
        if (targetStatus != null) {
            chunkGenerationTask = chunkMap.scheduleGenerationTask(targetStatus, this.getPos());
        } else {
            chunkGenerationTask = null;
        }

        ChunkGenerationTask chunkGenerationTask2 = this.task.getAndSet(chunkGenerationTask);
        if (chunkGenerationTask2 != null) {
            chunkGenerationTask2.markForCancellation();
        }
    }

    private CompletableFuture<ChunkResult<ChunkAccess>> getOrCreateFuture(ChunkStatus targetStatus) {
        if (this.isStatusDisallowed(targetStatus)) {
            return UNLOADED_CHUNK_FUTURE;
        }

        int i = targetStatus.getIndex();
        CompletableFuture<ChunkResult<ChunkAccess>> completableFuture = this.futures.get(i);

        while (completableFuture == null) {
            CompletableFuture<ChunkResult<ChunkAccess>> completableFuture2 = new CompletableFuture<>();
            completableFuture = this.futures.compareAndExchange(i, null, completableFuture2);
            if (completableFuture == null) {
                if (this.isStatusDisallowed(targetStatus)) {
                    this.failAndClearPendingFuture(i, completableFuture2);
                    return UNLOADED_CHUNK_FUTURE;
                }

                return completableFuture2;
            }
        }

        return completableFuture;
    }

    private void failAndClearPendingFuturesBetween(@Nullable ChunkStatus highestAllowableStatus, ChunkStatus currentStatus) {
        int i = highestAllowableStatus == null ? 0 : highestAllowableStatus.getIndex() + 1;
        int j = currentStatus.getIndex();

        for (int k = i; k <= j; k++) {
            CompletableFuture<ChunkResult<ChunkAccess>> completableFuture = this.futures.get(k);
            if (completableFuture != null) {
                this.failAndClearPendingFuture(k, completableFuture);
            }
        }
    }

    private void failAndClearPendingFuture(int status, CompletableFuture<ChunkResult<ChunkAccess>> future) {
        if (future.complete(UNLOADED_CHUNK) && !this.futures.compareAndSet(status, future, null)) {
            throw new IllegalStateException("Nothing else should replace the future here");
        }
    }

    private void completeFuture(ChunkStatus targetStatus, ChunkAccess chunkAccess) {
        ChunkResult<ChunkAccess> chunkResult = ChunkResult.of(chunkAccess);
        int i = targetStatus.getIndex();

        while (true) {
            CompletableFuture<ChunkResult<ChunkAccess>> completableFuture = this.futures.get(i);
            if (completableFuture == null) {
                if (this.futures.compareAndSet(i, null, CompletableFuture.completedFuture(chunkResult))) {
                    return;
                }
            } else {
                if (completableFuture.complete(chunkResult)) {
                    return;
                }

                if (completableFuture.getNow(NOT_DONE_YET).isSuccess()) {
                    throw new IllegalStateException("Trying to complete a future but found it to be completed successfully already");
                }

                Thread.yield();
            }
        }
    }

    @Nullable
    private ChunkStatus findHighestStatusWithPendingFuture(@Nullable ChunkStatus generationStatus) {
        if (generationStatus == null) {
            return null;
        }

        ChunkStatus chunkStatus = generationStatus;

        for (ChunkStatus chunkStatus2 = this.startedWork.get();
            chunkStatus2 == null || chunkStatus.isAfter(chunkStatus2);
            chunkStatus = chunkStatus.getParent()
        ) {
            if (this.futures.get(chunkStatus.getIndex()) != null) {
                return chunkStatus;
            }

            if (chunkStatus == ChunkStatus.EMPTY) {
                break;
            }
        }

        return null;
    }

    private boolean acquireStatusBump(ChunkStatus status) {
        ChunkStatus chunkStatus = status == ChunkStatus.EMPTY ? null : status.getParent();
        ChunkStatus chunkStatus2 = this.startedWork.compareAndExchange(chunkStatus, status);
        if (chunkStatus2 == chunkStatus) {
            return true;
        } else if (chunkStatus2 != null && !status.isAfter(chunkStatus2)) {
            return false;
        } else {
            throw new IllegalStateException("Unexpected last startedWork status: " + chunkStatus2 + " while trying to start: " + status);
        }
    }

    private boolean isStatusDisallowed(ChunkStatus status) {
        ChunkStatus chunkStatus = this.highestAllowedStatus;
        return chunkStatus == null || status.isAfter(chunkStatus);
    }

    public void increaseGenerationRefCount() {
        this.generationRefCount.incrementAndGet();
    }

    public void decreaseGenerationRefCount() {
        int i = this.generationRefCount.decrementAndGet();
        if (i < 0) {
            throw new IllegalStateException("More releases than claims. Count: " + i);
        }
    }

    public int getGenerationRefCount() {
        return this.generationRefCount.get();
    }

    @Nullable
    public ChunkAccess getChunkIfPresentUnchecked(ChunkStatus status) {
        CompletableFuture<ChunkResult<ChunkAccess>> completableFuture = this.futures.get(status.getIndex());
        return completableFuture == null ? null : completableFuture.getNow(NOT_DONE_YET).orElse(null);
    }

    @Nullable
    public ChunkAccess getChunkIfPresent(ChunkStatus status) {
        return this.isStatusDisallowed(status) ? null : this.getChunkIfPresentUnchecked(status);
    }

    @Nullable
    public ChunkAccess getLatestChunk() {
        ChunkStatus chunkStatus = this.startedWork.get();
        if (chunkStatus == null) {
            return null;
        }

        ChunkAccess chunkAccess = this.getChunkIfPresentUnchecked(chunkStatus);
        return chunkAccess != null ? chunkAccess : this.getChunkIfPresentUnchecked(chunkStatus.getParent());
    }

    @Nullable
    public ChunkStatus getPersistedStatus() {
        CompletableFuture<ChunkResult<ChunkAccess>> completableFuture = this.futures.get(ChunkStatus.EMPTY.getIndex());
        ChunkAccess chunkAccess = completableFuture == null ? null : completableFuture.getNow(NOT_DONE_YET).orElse(null);
        return chunkAccess == null ? null : chunkAccess.getPersistedStatus();
    }

    public ChunkPos getPos() {
        return this.pos;
    }

    public FullChunkStatus getFullStatus() {
        return ChunkLevel.fullStatus(this.getTicketLevel());
    }

    public abstract int getTicketLevel();

    public abstract int getQueueLevel();

    @VisibleForDebug
    public List<Pair<ChunkStatus, CompletableFuture<ChunkResult<ChunkAccess>>>> getAllFutures() {
        List<Pair<ChunkStatus, CompletableFuture<ChunkResult<ChunkAccess>>>> list = new ArrayList<>();

        for (int i = 0; i < CHUNK_STATUSES.size(); i++) {
            list.add(Pair.of(CHUNK_STATUSES.get(i), this.futures.get(i)));
        }

        return list;
    }

    @Nullable
    @VisibleForDebug
    public ChunkStatus getLatestStatus() {
        for (int i = CHUNK_STATUSES.size() - 1; i >= 0; i--) {
            ChunkStatus chunkStatus = CHUNK_STATUSES.get(i);
            ChunkAccess chunkAccess = this.getChunkIfPresentUnchecked(chunkStatus);
            if (chunkAccess != null) {
                return chunkStatus;
            }
        }

        return null;
    }
}
