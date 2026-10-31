package net.minecraft.client.renderer.chunk;

import com.google.common.collect.Lists;
import com.google.common.collect.Queues;
import com.google.common.collect.Sets;
import com.google.common.primitives.Doubles;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexSorting;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.SectionBufferBuilderPool;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.thread.ProcessorMailbox;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class SectionRenderDispatcher {
    private static final int MAX_HIGH_PRIORITY_QUOTA = 2;
    private final PriorityBlockingQueue<SectionRenderDispatcher.RenderSection.CompileTask> toBatchHighPriority = Queues.newPriorityBlockingQueue();
    private final Queue<SectionRenderDispatcher.RenderSection.CompileTask> toBatchLowPriority = Queues.newLinkedBlockingDeque();
    private int highPriorityQuota = 2;
    private final Queue<Runnable> toUpload = Queues.newConcurrentLinkedQueue();
    final SectionBufferBuilderPack fixedBuffers;
    private final SectionBufferBuilderPool bufferPool;
    private volatile int toBatchCount;
    private volatile boolean closed;
    private final ProcessorMailbox<Runnable> mailbox;
    private final Executor executor;
    ClientLevel level;
    final LevelRenderer renderer;
    private Vec3 camera = Vec3.ZERO;
    final SectionCompiler sectionCompiler;

    public SectionRenderDispatcher(
        ClientLevel level,
        LevelRenderer renderer,
        Executor executor,
        RenderBuffers buffers,
        BlockRenderDispatcher blockRenderer,
        BlockEntityRenderDispatcher blockEntityRenderer
    ) {
        this.level = level;
        this.renderer = renderer;
        this.fixedBuffers = buffers.fixedBufferPack();
        this.bufferPool = buffers.sectionBufferPool();
        this.executor = executor;
        this.mailbox = ProcessorMailbox.create(executor, "Section Renderer");
        this.mailbox.tell(this::runTask);
        this.sectionCompiler = new SectionCompiler(blockRenderer, blockEntityRenderer);
    }

    public void setLevel(ClientLevel level) {
        this.level = level;
    }

    private void runTask() {
        if (!this.closed && !this.bufferPool.isEmpty()) {
            SectionRenderDispatcher.RenderSection.CompileTask compileTask = this.pollTask();
            if (compileTask != null) {
                SectionBufferBuilderPack sectionBufferBuilderPack = Objects.requireNonNull(this.bufferPool.acquire());
                this.toBatchCount = this.toBatchHighPriority.size() + this.toBatchLowPriority.size();
                CompletableFuture.supplyAsync(
                        Util.wrapThreadWithTaskName(compileTask.name(), () -> compileTask.doTask(sectionBufferBuilderPack)), this.executor
                    )
                    .thenCompose(completableFuture -> (CompletionStage<SectionRenderDispatcher.SectionTaskResult>)completableFuture)
                    .whenComplete((sectionTaskResult, throwable) -> {
                        if (throwable != null) {
                            Minecraft.getInstance().delayCrash(CrashReport.forThrowable(throwable, "Batching sections"));
                        } else {
                            this.mailbox.tell(() -> {
                                if (sectionTaskResult == SectionRenderDispatcher.SectionTaskResult.SUCCESSFUL) {
                                    sectionBufferBuilderPack.clearAll();
                                } else {
                                    sectionBufferBuilderPack.discardAll();
                                }

                                this.bufferPool.release(sectionBufferBuilderPack);
                                this.runTask();
                            });
                        }
                    });
            }
        }
    }

    @Nullable
    private SectionRenderDispatcher.RenderSection.CompileTask pollTask() {
        if (this.highPriorityQuota <= 0) {
            SectionRenderDispatcher.RenderSection.CompileTask compileTask = this.toBatchLowPriority.poll();
            if (compileTask != null) {
                this.highPriorityQuota = 2;
                return compileTask;
            }
        }

        SectionRenderDispatcher.RenderSection.CompileTask compileTask = this.toBatchHighPriority.poll();
        if (compileTask != null) {
            this.highPriorityQuota--;
            return compileTask;
        } else {
            this.highPriorityQuota = 2;
            return this.toBatchLowPriority.poll();
        }
    }

    public String getStats() {
        return String.format(Locale.ROOT, "pC: %03d, pU: %02d, aB: %02d", this.toBatchCount, this.toUpload.size(), this.bufferPool.getFreeBufferCount());
    }

    public int getToBatchCount() {
        return this.toBatchCount;
    }

    public int getToUpload() {
        return this.toUpload.size();
    }

    public int getFreeBufferCount() {
        return this.bufferPool.getFreeBufferCount();
    }

    public void setCamera(Vec3 camera) {
        this.camera = camera;
    }

    public Vec3 getCameraPosition() {
        return this.camera;
    }

    public void uploadAllPendingUploads() {
        Runnable runnable;
        while ((runnable = this.toUpload.poll()) != null) {
            runnable.run();
        }
    }

    public void rebuildSectionSync(SectionRenderDispatcher.RenderSection section, RenderRegionCache regionCache) {
        section.compileSync(regionCache);
    }

    public void blockUntilClear() {
        this.clearBatchQueue();
    }

    public void schedule(SectionRenderDispatcher.RenderSection.CompileTask task) {
        if (!this.closed) {
            this.mailbox.tell(() -> {
                if (!this.closed) {
                    if (task.isHighPriority) {
                        this.toBatchHighPriority.offer(task);
                    } else {
                        this.toBatchLowPriority.offer(task);
                    }

                    this.toBatchCount = this.toBatchHighPriority.size() + this.toBatchLowPriority.size();
                    this.runTask();
                }
            });
        }
    }

    public CompletableFuture<Void> uploadSectionLayer(MeshData meshData, VertexBuffer vertexBuffer) {
        return this.closed ? CompletableFuture.completedFuture(null) : CompletableFuture.runAsync(() -> {
            if (vertexBuffer.isInvalid()) {
                meshData.close();
            } else {
                vertexBuffer.bind();
                vertexBuffer.upload(meshData);
                VertexBuffer.unbind();
            }
        }, this.toUpload::add);
    }

    public CompletableFuture<Void> uploadSectionIndexBuffer(ByteBufferBuilder.Result result, VertexBuffer vertexBuffer) {
        return this.closed ? CompletableFuture.completedFuture(null) : CompletableFuture.runAsync(() -> {
            if (vertexBuffer.isInvalid()) {
                result.close();
            } else {
                vertexBuffer.bind();
                vertexBuffer.uploadIndexBuffer(result);
                VertexBuffer.unbind();
            }
        }, this.toUpload::add);
    }

    private void clearBatchQueue() {
        while (!this.toBatchHighPriority.isEmpty()) {
            SectionRenderDispatcher.RenderSection.CompileTask compileTask = this.toBatchHighPriority.poll();
            if (compileTask != null) {
                compileTask.cancel();
            }
        }

        while (!this.toBatchLowPriority.isEmpty()) {
            SectionRenderDispatcher.RenderSection.CompileTask compileTask = this.toBatchLowPriority.poll();
            if (compileTask != null) {
                compileTask.cancel();
            }
        }

        this.toBatchCount = 0;
    }

    public boolean isQueueEmpty() {
        return this.toBatchCount == 0 && this.toUpload.isEmpty();
    }

    public void dispose() {
        this.closed = true;
        this.clearBatchQueue();
        this.uploadAllPendingUploads();
    }

    public static class CompiledSection {
        public static final SectionRenderDispatcher.CompiledSection UNCOMPILED = new SectionRenderDispatcher.CompiledSection() {
            @Override
            public boolean facesCanSeeEachother(Direction direction, Direction direction2) {
                return false;
            }
        };
        public static final SectionRenderDispatcher.CompiledSection EMPTY = new SectionRenderDispatcher.CompiledSection() {
            @Override
            public boolean facesCanSeeEachother(Direction direction, Direction direction2) {
                return true;
            }
        };
        final Set<RenderType> hasBlocks = new ObjectArraySet<>(RenderType.chunkBufferLayers().size());
        final List<BlockEntity> renderableBlockEntities = Lists.newArrayList();
        VisibilitySet visibilitySet = new VisibilitySet();
        @Nullable
        MeshData.SortState transparencyState;

        public boolean hasNoRenderableLayers() {
            return this.hasBlocks.isEmpty();
        }

        public boolean isEmpty(RenderType renderType) {
            return !this.hasBlocks.contains(renderType);
        }

        public List<BlockEntity> getRenderableBlockEntities() {
            return this.renderableBlockEntities;
        }

        public boolean facesCanSeeEachother(Direction face1, Direction face2) {
            return this.visibilitySet.visibilityBetween(face1, face2);
        }
    }

    public class RenderSection {
        public static final int SIZE = 16;
        public final int index;
        public final AtomicReference<SectionRenderDispatcher.CompiledSection> compiled = new AtomicReference<>(
            SectionRenderDispatcher.CompiledSection.UNCOMPILED
        );
        private final AtomicInteger initialCompilationCancelCount = new AtomicInteger(0);
        @Nullable
        private SectionRenderDispatcher.RenderSection.RebuildTask lastRebuildTask;
        @Nullable
        private SectionRenderDispatcher.RenderSection.ResortTransparencyTask lastResortTransparencyTask;
        private final Set<BlockEntity> globalBlockEntities = Sets.newHashSet();
        private final Map<RenderType, VertexBuffer> buffers = RenderType.chunkBufferLayers()
            .stream()
            .collect(Collectors.toMap(renderType -> (RenderType)renderType, renderType -> new VertexBuffer(VertexBuffer.Usage.STATIC)));
        private AABB bb;
        private boolean dirty = true;
        final BlockPos.MutableBlockPos origin = new BlockPos.MutableBlockPos(-1, -1, -1);
        private final BlockPos.MutableBlockPos[] relativeOrigins = Util.make(new BlockPos.MutableBlockPos[6], mutableBlockPoss -> {
            for (int i = 0; i < mutableBlockPoss.length; i++) {
                mutableBlockPoss[i] = new BlockPos.MutableBlockPos();
            }
        });
        private boolean playerChanged;

        public RenderSection(final int index, final int originX, final int originY, final int originZ) {
            this.index = index;
            this.setOrigin(originX, originY, originZ);
        }

        private boolean doesChunkExistAt(BlockPos pos) {
            return SectionRenderDispatcher.this.level
                    .getChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()), ChunkStatus.FULL, false)
                != null;
        }

        public boolean hasAllNeighbors() {
            int i = 24;
            return !(this.getDistToPlayerSqr() > 576.0)
                ? true
                : this.doesChunkExistAt(this.relativeOrigins[Direction.WEST.ordinal()])
                    && this.doesChunkExistAt(this.relativeOrigins[Direction.NORTH.ordinal()])
                    && this.doesChunkExistAt(this.relativeOrigins[Direction.EAST.ordinal()])
                    && this.doesChunkExistAt(this.relativeOrigins[Direction.SOUTH.ordinal()]);
        }

        public AABB getBoundingBox() {
            return this.bb;
        }

        public VertexBuffer getBuffer(RenderType renderType) {
            return this.buffers.get(renderType);
        }

        public void setOrigin(int x, int y, int z) {
            this.reset();
            this.origin.set(x, y, z);
            this.bb = new AABB(x, y, z, x + 16, y + 16, z + 16);

            for (Direction direction : Direction.values()) {
                this.relativeOrigins[direction.ordinal()].set(this.origin).move(direction, 16);
            }
        }

        protected double getDistToPlayerSqr() {
            Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
            double d = this.bb.minX + 8.0 - camera.getPosition().x;
            double e = this.bb.minY + 8.0 - camera.getPosition().y;
            double f = this.bb.minZ + 8.0 - camera.getPosition().z;
            return d * d + e * e + f * f;
        }

        public SectionRenderDispatcher.CompiledSection getCompiled() {
            return this.compiled.get();
        }

        private void reset() {
            this.cancelTasks();
            this.compiled.set(SectionRenderDispatcher.CompiledSection.UNCOMPILED);
            this.dirty = true;
        }

        public void releaseBuffers() {
            this.reset();
            this.buffers.values().forEach(VertexBuffer::close);
        }

        public BlockPos getOrigin() {
            return this.origin;
        }

        public void setDirty(boolean playerChanged) {
            boolean bl = this.dirty;
            this.dirty = true;
            this.playerChanged = playerChanged | (bl && this.playerChanged);
        }

        public void setNotDirty() {
            this.dirty = false;
            this.playerChanged = false;
        }

        public boolean isDirty() {
            return this.dirty;
        }

        public boolean isDirtyFromPlayer() {
            return this.dirty && this.playerChanged;
        }

        public BlockPos getRelativeOrigin(Direction direction) {
            return this.relativeOrigins[direction.ordinal()];
        }

        public boolean resortTransparency(RenderType renderType, SectionRenderDispatcher sectionRenderDispatcher) {
            SectionRenderDispatcher.CompiledSection compiledSection = this.getCompiled();
            if (this.lastResortTransparencyTask != null) {
                this.lastResortTransparencyTask.cancel();
            }

            if (!compiledSection.hasBlocks.contains(renderType)) {
                return false;
            }

            this.lastResortTransparencyTask = new SectionRenderDispatcher.RenderSection.ResortTransparencyTask(this.getDistToPlayerSqr(), compiledSection);
            sectionRenderDispatcher.schedule(this.lastResortTransparencyTask);
            return true;
        }

        protected boolean cancelTasks() {
            boolean bl = false;
            if (this.lastRebuildTask != null) {
                this.lastRebuildTask.cancel();
                this.lastRebuildTask = null;
                bl = true;
            }

            if (this.lastResortTransparencyTask != null) {
                this.lastResortTransparencyTask.cancel();
                this.lastResortTransparencyTask = null;
            }

            return bl;
        }

        public SectionRenderDispatcher.RenderSection.CompileTask createCompileTask(RenderRegionCache regionCache) {
            boolean bl = this.cancelTasks();
            RenderChunkRegion renderChunkRegion = regionCache.createRegion(SectionRenderDispatcher.this.level, SectionPos.of(this.origin));
            boolean bl2 = this.compiled.get() == SectionRenderDispatcher.CompiledSection.UNCOMPILED;
            if (bl2 && bl) {
                this.initialCompilationCancelCount.incrementAndGet();
            }

            this.lastRebuildTask = new SectionRenderDispatcher.RenderSection.RebuildTask(
                this.getDistToPlayerSqr(), renderChunkRegion, !bl2 || this.initialCompilationCancelCount.get() > 2
            );
            return this.lastRebuildTask;
        }

        public void rebuildSectionAsync(SectionRenderDispatcher sectionRenderDispatcher, RenderRegionCache regionCache) {
            SectionRenderDispatcher.RenderSection.CompileTask compileTask = this.createCompileTask(regionCache);
            sectionRenderDispatcher.schedule(compileTask);
        }

        void updateGlobalBlockEntities(Collection<BlockEntity> blockEntities) {
            Set<BlockEntity> set = Sets.newHashSet(blockEntities);
            Set<BlockEntity> set2;
            synchronized (this.globalBlockEntities) {
                set2 = Sets.newHashSet(this.globalBlockEntities);
                set.removeAll(this.globalBlockEntities);
                set2.removeAll(blockEntities);
                this.globalBlockEntities.clear();
                this.globalBlockEntities.addAll(blockEntities);
            }

            SectionRenderDispatcher.this.renderer.updateGlobalBlockEntities(set2, set);
        }

        public void compileSync(RenderRegionCache regionCache) {
            SectionRenderDispatcher.RenderSection.CompileTask compileTask = this.createCompileTask(regionCache);
            compileTask.doTask(SectionRenderDispatcher.this.fixedBuffers);
        }

        public boolean isAxisAlignedWith(int x, int y, int z) {
            BlockPos blockPos = this.getOrigin();
            return x == SectionPos.blockToSectionCoord(blockPos.getX())
                || z == SectionPos.blockToSectionCoord(blockPos.getZ())
                || y == SectionPos.blockToSectionCoord(blockPos.getY());
        }

        void setCompiled(SectionRenderDispatcher.CompiledSection compiled) {
            this.compiled.set(compiled);
            this.initialCompilationCancelCount.set(0);
            SectionRenderDispatcher.this.renderer.addRecentlyCompiledSection(this);
        }

        VertexSorting createVertexSorting() {
            Vec3 vec3 = SectionRenderDispatcher.this.getCameraPosition();
            return VertexSorting.byDistance((float)(vec3.x - this.origin.getX()), (float)(vec3.y - this.origin.getY()), (float)(vec3.z - this.origin.getZ()));
        }

        abstract class CompileTask implements Comparable<SectionRenderDispatcher.RenderSection.CompileTask> {
            protected final double distAtCreation;
            protected final AtomicBoolean isCancelled = new AtomicBoolean(false);
            protected final boolean isHighPriority;

            public CompileTask(final double distAtCreation, final boolean isHighPriority) {
                this.distAtCreation = distAtCreation;
                this.isHighPriority = isHighPriority;
            }

            public abstract CompletableFuture<SectionRenderDispatcher.SectionTaskResult> doTask(SectionBufferBuilderPack var1);

            public abstract void cancel();

            protected abstract String name();

            public int compareTo(SectionRenderDispatcher.RenderSection.CompileTask other) {
                return Doubles.compare(this.distAtCreation, other.distAtCreation);
            }
        }

        class RebuildTask extends SectionRenderDispatcher.RenderSection.CompileTask {
            @Nullable
            protected RenderChunkRegion region;

            public RebuildTask(final double distAtCreation, @Nullable final RenderChunkRegion region, final boolean isHighPriority) {
                super(distAtCreation, isHighPriority);
                this.region = region;
            }

            @Override
            protected String name() {
                return "rend_chk_rebuild";
            }

            @Override
            public CompletableFuture<SectionRenderDispatcher.SectionTaskResult> doTask(SectionBufferBuilderPack sectionBufferBuilderPack) {
                if (this.isCancelled.get()) {
                    return CompletableFuture.completedFuture(SectionRenderDispatcher.SectionTaskResult.CANCELLED);
                } else if (!RenderSection.this.hasAllNeighbors()) {
                    this.cancel();
                    return CompletableFuture.completedFuture(SectionRenderDispatcher.SectionTaskResult.CANCELLED);
                } else if (this.isCancelled.get()) {
                    return CompletableFuture.completedFuture(SectionRenderDispatcher.SectionTaskResult.CANCELLED);
                } else {
                    RenderChunkRegion renderChunkRegion = this.region;
                    this.region = null;
                    if (renderChunkRegion == null) {
                        RenderSection.this.setCompiled(SectionRenderDispatcher.CompiledSection.EMPTY);
                        return CompletableFuture.completedFuture(SectionRenderDispatcher.SectionTaskResult.SUCCESSFUL);
                    } else {
                        SectionPos sectionPos = SectionPos.of(RenderSection.this.origin);
                        SectionCompiler.Results results = SectionRenderDispatcher.this.sectionCompiler
                            .compile(sectionPos, renderChunkRegion, RenderSection.this.createVertexSorting(), sectionBufferBuilderPack);
                        RenderSection.this.updateGlobalBlockEntities(results.globalBlockEntities);
                        if (this.isCancelled.get()) {
                            results.release();
                            return CompletableFuture.completedFuture(SectionRenderDispatcher.SectionTaskResult.CANCELLED);
                        } else {
                            SectionRenderDispatcher.CompiledSection compiledSection = new SectionRenderDispatcher.CompiledSection();
                            compiledSection.visibilitySet = results.visibilitySet;
                            compiledSection.renderableBlockEntities.addAll(results.blockEntities);
                            compiledSection.transparencyState = results.transparencyState;
                            List<CompletableFuture<Void>> list = new ArrayList<>(results.renderedLayers.size());
                            results.renderedLayers.forEach((renderType, meshData) -> {
                                list.add(SectionRenderDispatcher.this.uploadSectionLayer(meshData, RenderSection.this.getBuffer(renderType)));
                                compiledSection.hasBlocks.add(renderType);
                            });
                            return Util.sequenceFailFast(list).handle((listx, exception) -> {
                                if (exception != null && !(exception instanceof CancellationException) && !(exception instanceof InterruptedException)) {
                                    Minecraft.getInstance().delayCrash(CrashReport.forThrowable(exception, "Rendering section"));
                                }

                                if (this.isCancelled.get()) {
                                    return SectionRenderDispatcher.SectionTaskResult.CANCELLED;
                                }

                                RenderSection.this.setCompiled(compiledSection);
                                return SectionRenderDispatcher.SectionTaskResult.SUCCESSFUL;
                            });
                        }
                    }
                }
            }

            @Override
            public void cancel() {
                this.region = null;
                if (this.isCancelled.compareAndSet(false, true)) {
                    RenderSection.this.setDirty(false);
                }
            }
        }

        class ResortTransparencyTask extends SectionRenderDispatcher.RenderSection.CompileTask {
            private final SectionRenderDispatcher.CompiledSection compiledSection;

            public ResortTransparencyTask(final double distAtCreation, final SectionRenderDispatcher.CompiledSection compiledSection) {
                super(distAtCreation, true);
                this.compiledSection = compiledSection;
            }

            @Override
            protected String name() {
                return "rend_chk_sort";
            }

            @Override
            public CompletableFuture<SectionRenderDispatcher.SectionTaskResult> doTask(SectionBufferBuilderPack sectionBufferBuilderPack) {
                if (this.isCancelled.get()) {
                    return CompletableFuture.completedFuture(SectionRenderDispatcher.SectionTaskResult.CANCELLED);
                }

                if (!RenderSection.this.hasAllNeighbors()) {
                    this.isCancelled.set(true);
                    return CompletableFuture.completedFuture(SectionRenderDispatcher.SectionTaskResult.CANCELLED);
                }

                if (this.isCancelled.get()) {
                    return CompletableFuture.completedFuture(SectionRenderDispatcher.SectionTaskResult.CANCELLED);
                }

                MeshData.SortState sortState = this.compiledSection.transparencyState;
                if (sortState != null && !this.compiledSection.isEmpty(RenderType.translucent())) {
                    VertexSorting vertexSorting = RenderSection.this.createVertexSorting();
                    ByteBufferBuilder.Result result = sortState.buildSortedIndexBuffer(sectionBufferBuilderPack.buffer(RenderType.translucent()), vertexSorting);
                    if (result == null) {
                        return CompletableFuture.completedFuture(SectionRenderDispatcher.SectionTaskResult.CANCELLED);
                    } else if (this.isCancelled.get()) {
                        result.close();
                        return CompletableFuture.completedFuture(SectionRenderDispatcher.SectionTaskResult.CANCELLED);
                    } else {
                        CompletableFuture<SectionRenderDispatcher.SectionTaskResult> completableFuture = SectionRenderDispatcher.this.uploadSectionIndexBuffer(
                                result, RenderSection.this.getBuffer(RenderType.translucent())
                            )
                            .thenApply(void_ -> SectionRenderDispatcher.SectionTaskResult.CANCELLED);
                        return completableFuture.handle(
                            (resultx, exception) -> {
                                if (exception != null && !(exception instanceof CancellationException) && !(exception instanceof InterruptedException)) {
                                    Minecraft.getInstance().delayCrash(CrashReport.forThrowable(exception, "Rendering section"));
                                }

                                return this.isCancelled.get()
                                    ? SectionRenderDispatcher.SectionTaskResult.CANCELLED
                                    : SectionRenderDispatcher.SectionTaskResult.SUCCESSFUL;
                            }
                        );
                    }
                } else {
                    return CompletableFuture.completedFuture(SectionRenderDispatcher.SectionTaskResult.CANCELLED);
                }
            }

            @Override
            public void cancel() {
                this.isCancelled.set(true);
            }
        }
    }

    enum SectionTaskResult {
        SUCCESSFUL,
        CANCELLED;
    }
}
