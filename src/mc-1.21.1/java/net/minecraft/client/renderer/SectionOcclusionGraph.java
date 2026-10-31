package net.minecraft.client.renderer;

import com.google.common.collect.Lists;
import com.google.common.collect.Queues;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.util.Mth;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

public class SectionOcclusionGraph {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final int MINIMUM_ADVANCED_CULLING_DISTANCE = 60;
    private static final double CEILED_SECTION_DIAGONAL = Math.ceil(Math.sqrt(3.0) * 16.0);
    private boolean needsFullUpdate = true;
    @Nullable
    private Future<?> fullUpdateTask;
    @Nullable
    private ViewArea viewArea;
    private final AtomicReference<SectionOcclusionGraph.GraphState> currentGraph = new AtomicReference<>();
    private final AtomicReference<SectionOcclusionGraph.GraphEvents> nextGraphEvents = new AtomicReference<>();
    private final AtomicBoolean needsFrustumUpdate = new AtomicBoolean(false);

    public void waitAndReset(@Nullable ViewArea viewArea) {
        if (this.fullUpdateTask != null) {
            try {
                this.fullUpdateTask.get();
                this.fullUpdateTask = null;
            } catch (Exception exception) {
                LOGGER.warn("Full update failed", exception);
            }
        }

        this.viewArea = viewArea;
        if (viewArea != null) {
            this.currentGraph.set(new SectionOcclusionGraph.GraphState(viewArea.sections.length));
            this.invalidate();
        } else {
            this.currentGraph.set(null);
        }
    }

    public void invalidate() {
        this.needsFullUpdate = true;
    }

    public void addSectionsInFrustum(Frustum frustum, List<SectionRenderDispatcher.RenderSection> sections) {
        for (SectionOcclusionGraph.Node node : this.currentGraph.get().storage().renderSections) {
            if (frustum.isVisible(node.section.getBoundingBox())) {
                sections.add(node.section);
            }
        }
    }

    public boolean consumeFrustumUpdate() {
        return this.needsFrustumUpdate.compareAndSet(true, false);
    }

    public void onChunkLoaded(ChunkPos chunkPos) {
        SectionOcclusionGraph.GraphEvents graphEvents = this.nextGraphEvents.get();
        if (graphEvents != null) {
            this.addNeighbors(graphEvents, chunkPos);
        }

        SectionOcclusionGraph.GraphEvents graphEvents2 = this.currentGraph.get().events;
        if (graphEvents2 != graphEvents) {
            this.addNeighbors(graphEvents2, chunkPos);
        }
    }

    public void onSectionCompiled(SectionRenderDispatcher.RenderSection section) {
        SectionOcclusionGraph.GraphEvents graphEvents = this.nextGraphEvents.get();
        if (graphEvents != null) {
            graphEvents.sectionsToPropagateFrom.add(section);
        }

        SectionOcclusionGraph.GraphEvents graphEvents2 = this.currentGraph.get().events;
        if (graphEvents2 != graphEvents) {
            graphEvents2.sectionsToPropagateFrom.add(section);
        }
    }

    public void update(boolean smartCull, Camera camera, Frustum frustum, List<SectionRenderDispatcher.RenderSection> sections) {
        Vec3 vec3 = camera.getPosition();
        if (this.needsFullUpdate && (this.fullUpdateTask == null || this.fullUpdateTask.isDone())) {
            this.scheduleFullUpdate(smartCull, camera, vec3);
        }

        this.runPartialUpdate(smartCull, frustum, sections, vec3);
    }

    private void scheduleFullUpdate(boolean smartCull, Camera camera, Vec3 cameraPosition) {
        this.needsFullUpdate = false;
        this.fullUpdateTask = Util.backgroundExecutor().submit(() -> {
            SectionOcclusionGraph.GraphState graphState = new SectionOcclusionGraph.GraphState(this.viewArea.sections.length);
            this.nextGraphEvents.set(graphState.events);
            Queue<SectionOcclusionGraph.Node> queue = Queues.newArrayDeque();
            this.initializeQueueForFullUpdate(camera, queue);
            queue.forEach(node -> graphState.storage.sectionToNodeMap.put(node.section, node));
            this.runUpdates(graphState.storage, cameraPosition, queue, smartCull, renderSection -> {});
            this.currentGraph.set(graphState);
            this.nextGraphEvents.set(null);
            this.needsFrustumUpdate.set(true);
        });
    }

    private void runPartialUpdate(boolean smartCull, Frustum frustum, List<SectionRenderDispatcher.RenderSection> sections, Vec3 cameraPosition) {
        SectionOcclusionGraph.GraphState graphState = this.currentGraph.get();
        this.queueSectionsWithNewNeighbors(graphState);
        if (!graphState.events.sectionsToPropagateFrom.isEmpty()) {
            Queue<SectionOcclusionGraph.Node> queue = Queues.newArrayDeque();

            while (!graphState.events.sectionsToPropagateFrom.isEmpty()) {
                SectionRenderDispatcher.RenderSection renderSection = graphState.events.sectionsToPropagateFrom.poll();
                SectionOcclusionGraph.Node node = graphState.storage.sectionToNodeMap.get(renderSection);
                if (node != null && node.section == renderSection) {
                    queue.add(node);
                }
            }

            Frustum frustum2 = LevelRenderer.offsetFrustum(frustum);
            Consumer<SectionRenderDispatcher.RenderSection> consumer = renderSection -> {
                if (frustum2.isVisible(renderSection.getBoundingBox())) {
                    sections.add(renderSection);
                }
            };
            this.runUpdates(graphState.storage, cameraPosition, queue, smartCull, consumer);
        }
    }

    private void queueSectionsWithNewNeighbors(SectionOcclusionGraph.GraphState graphState) {
        LongIterator longIterator = graphState.events.chunksWhichReceivedNeighbors.iterator();

        while (longIterator.hasNext()) {
            long l = longIterator.nextLong();
            List<SectionRenderDispatcher.RenderSection> list = graphState.storage.chunksWaitingForNeighbors.get(l);
            if (list != null && list.get(0).hasAllNeighbors()) {
                graphState.events.sectionsToPropagateFrom.addAll(list);
                graphState.storage.chunksWaitingForNeighbors.remove(l);
            }
        }

        graphState.events.chunksWhichReceivedNeighbors.clear();
    }

    private void addNeighbors(SectionOcclusionGraph.GraphEvents graphEvents, ChunkPos chunkPos) {
        graphEvents.chunksWhichReceivedNeighbors.add(ChunkPos.asLong(chunkPos.x - 1, chunkPos.z));
        graphEvents.chunksWhichReceivedNeighbors.add(ChunkPos.asLong(chunkPos.x, chunkPos.z - 1));
        graphEvents.chunksWhichReceivedNeighbors.add(ChunkPos.asLong(chunkPos.x + 1, chunkPos.z));
        graphEvents.chunksWhichReceivedNeighbors.add(ChunkPos.asLong(chunkPos.x, chunkPos.z + 1));
    }

    private void initializeQueueForFullUpdate(Camera camera, Queue<SectionOcclusionGraph.Node> nodeQueue) {
        int i = 16;
        Vec3 vec3 = camera.getPosition();
        BlockPos blockPos = camera.getBlockPosition();
        SectionRenderDispatcher.RenderSection renderSection = this.viewArea.getRenderSectionAt(blockPos);
        if (renderSection == null) {
            LevelHeightAccessor levelHeightAccessor = this.viewArea.getLevelHeightAccessor();
            boolean bl = blockPos.getY() > levelHeightAccessor.getMinBuildHeight();
            int j = bl ? levelHeightAccessor.getMaxBuildHeight() - 8 : levelHeightAccessor.getMinBuildHeight() + 8;
            int k = Mth.floor(vec3.x / 16.0) * 16;
            int l = Mth.floor(vec3.z / 16.0) * 16;
            int m = this.viewArea.getViewDistance();
            List<SectionOcclusionGraph.Node> list = Lists.newArrayList();

            for (int n = -m; n <= m; n++) {
                for (int o = -m; o <= m; o++) {
                    SectionRenderDispatcher.RenderSection renderSection2 = this.viewArea
                        .getRenderSectionAt(new BlockPos(k + SectionPos.sectionToBlockCoord(n, 8), j, l + SectionPos.sectionToBlockCoord(o, 8)));
                    if (renderSection2 != null && this.isInViewDistance(blockPos, renderSection2.getOrigin())) {
                        Direction direction = bl ? Direction.DOWN : Direction.UP;
                        SectionOcclusionGraph.Node node = new SectionOcclusionGraph.Node(renderSection2, direction, 0);
                        node.setDirections(node.directions, direction);
                        if (n > 0) {
                            node.setDirections(node.directions, Direction.EAST);
                        } else if (n < 0) {
                            node.setDirections(node.directions, Direction.WEST);
                        }

                        if (o > 0) {
                            node.setDirections(node.directions, Direction.SOUTH);
                        } else if (o < 0) {
                            node.setDirections(node.directions, Direction.NORTH);
                        }

                        list.add(node);
                    }
                }
            }

            list.sort(Comparator.comparingDouble(nodex -> blockPos.distSqr(nodex.section.getOrigin().offset(8, 8, 8))));
            nodeQueue.addAll(list);
        } else {
            nodeQueue.add(new SectionOcclusionGraph.Node(renderSection, null, 0));
        }
    }

    private void runUpdates(
        SectionOcclusionGraph.GraphStorage graphStorage,
        Vec3 cameraPosition,
        Queue<SectionOcclusionGraph.Node> nodeQueue,
        boolean smartCull,
        Consumer<SectionRenderDispatcher.RenderSection> sections
    ) {
        int i = 16;
        BlockPos blockPos = new BlockPos(
            Mth.floor(cameraPosition.x / 16.0) * 16, Mth.floor(cameraPosition.y / 16.0) * 16, Mth.floor(cameraPosition.z / 16.0) * 16
        );
        BlockPos blockPos2 = blockPos.offset(8, 8, 8);

        while (!nodeQueue.isEmpty()) {
            SectionOcclusionGraph.Node node = nodeQueue.poll();
            SectionRenderDispatcher.RenderSection renderSection = node.section;
            if (graphStorage.renderSections.add(node)) {
                sections.accept(node.section);
            }

            boolean bl = Math.abs(renderSection.getOrigin().getX() - blockPos.getX()) > 60
                || Math.abs(renderSection.getOrigin().getY() - blockPos.getY()) > 60
                || Math.abs(renderSection.getOrigin().getZ() - blockPos.getZ()) > 60;

            for (Direction direction : DIRECTIONS) {
                SectionRenderDispatcher.RenderSection renderSection2 = this.getRelativeFrom(blockPos, renderSection, direction);
                if (renderSection2 != null && (!smartCull || !node.hasDirection(direction.getOpposite()))) {
                    if (smartCull && node.hasSourceDirections()) {
                        SectionRenderDispatcher.CompiledSection compiledSection = renderSection.getCompiled();
                        boolean bl2 = false;

                        for (int j = 0; j < DIRECTIONS.length; j++) {
                            if (node.hasSourceDirection(j) && compiledSection.facesCanSeeEachother(DIRECTIONS[j].getOpposite(), direction)) {
                                bl2 = true;
                                break;
                            }
                        }

                        if (!bl2) {
                            continue;
                        }
                    }

                    if (smartCull && bl) {
                        BlockPos blockPos3 = renderSection2.getOrigin();
                        BlockPos blockPos4 = blockPos3.offset(
                            (direction.getAxis() == Direction.Axis.X ? blockPos2.getX() <= blockPos3.getX() : blockPos2.getX() >= blockPos3.getX()) ? 0 : 16,
                            (direction.getAxis() == Direction.Axis.Y ? blockPos2.getY() <= blockPos3.getY() : blockPos2.getY() >= blockPos3.getY()) ? 0 : 16,
                            (direction.getAxis() == Direction.Axis.Z ? blockPos2.getZ() <= blockPos3.getZ() : blockPos2.getZ() >= blockPos3.getZ()) ? 0 : 16
                        );
                        Vec3 vec3 = new Vec3(blockPos4.getX(), blockPos4.getY(), blockPos4.getZ());
                        Vec3 vec32 = cameraPosition.subtract(vec3).normalize().scale(CEILED_SECTION_DIAGONAL);
                        boolean bl3 = true;

                        while (cameraPosition.subtract(vec3).lengthSqr() > 3600.0) {
                            vec3 = vec3.add(vec32);
                            LevelHeightAccessor levelHeightAccessor = this.viewArea.getLevelHeightAccessor();
                            if (vec3.y > levelHeightAccessor.getMaxBuildHeight() || vec3.y < levelHeightAccessor.getMinBuildHeight()) {
                                break;
                            }

                            SectionRenderDispatcher.RenderSection renderSection3 = this.viewArea
                                .getRenderSectionAt(BlockPos.containing(vec3.x, vec3.y, vec3.z));
                            if (renderSection3 == null || graphStorage.sectionToNodeMap.get(renderSection3) == null) {
                                bl3 = false;
                                break;
                            }
                        }

                        if (!bl3) {
                            continue;
                        }
                    }

                    SectionOcclusionGraph.Node node2 = graphStorage.sectionToNodeMap.get(renderSection2);
                    if (node2 != null) {
                        node2.addSourceDirection(direction);
                    } else {
                        SectionOcclusionGraph.Node node3 = new SectionOcclusionGraph.Node(renderSection2, direction, node.step + 1);
                        node3.setDirections(node.directions, direction);
                        if (renderSection2.hasAllNeighbors()) {
                            nodeQueue.add(node3);
                            graphStorage.sectionToNodeMap.put(renderSection2, node3);
                        } else if (this.isInViewDistance(blockPos, renderSection2.getOrigin())) {
                            graphStorage.sectionToNodeMap.put(renderSection2, node3);
                            graphStorage.chunksWaitingForNeighbors
                                .computeIfAbsent(ChunkPos.asLong(renderSection2.getOrigin()), l -> new ArrayList<>())
                                .add(renderSection2);
                        }
                    }
                }
            }
        }
    }

    private boolean isInViewDistance(BlockPos pos, BlockPos origin) {
        int i = SectionPos.blockToSectionCoord(pos.getX());
        int j = SectionPos.blockToSectionCoord(pos.getZ());
        int k = SectionPos.blockToSectionCoord(origin.getX());
        int l = SectionPos.blockToSectionCoord(origin.getZ());
        return ChunkTrackingView.isInViewDistance(i, j, this.viewArea.getViewDistance(), k, l);
    }

    @Nullable
    private SectionRenderDispatcher.RenderSection getRelativeFrom(BlockPos pos, SectionRenderDispatcher.RenderSection section, Direction direction) {
        BlockPos blockPos = section.getRelativeOrigin(direction);
        if (!this.isInViewDistance(pos, blockPos)) {
            return null;
        } else {
            return Mth.abs(pos.getY() - blockPos.getY()) > this.viewArea.getViewDistance() * 16 ? null : this.viewArea.getRenderSectionAt(blockPos);
        }
    }

    @Nullable
    @VisibleForDebug
    protected SectionOcclusionGraph.Node getNode(SectionRenderDispatcher.RenderSection section) {
        return this.currentGraph.get().storage.sectionToNodeMap.get(section);
    }

    record GraphEvents(LongSet chunksWhichReceivedNeighbors, BlockingQueue<SectionRenderDispatcher.RenderSection> sectionsToPropagateFrom) {
        public GraphEvents() {
            this(new LongOpenHashSet(), new LinkedBlockingQueue<>());
        }
    }

    record GraphState(SectionOcclusionGraph.GraphStorage storage, SectionOcclusionGraph.GraphEvents events) {
        public GraphState(int size) {
            this(new SectionOcclusionGraph.GraphStorage(size), new SectionOcclusionGraph.GraphEvents());
        }
    }

    static class GraphStorage {
        public final SectionOcclusionGraph.SectionToNodeMap sectionToNodeMap;
        public final LinkedHashSet<SectionOcclusionGraph.Node> renderSections;
        public final Long2ObjectMap<List<SectionRenderDispatcher.RenderSection>> chunksWaitingForNeighbors;

        public GraphStorage(int size) {
            this.sectionToNodeMap = new SectionOcclusionGraph.SectionToNodeMap(size);
            this.renderSections = new LinkedHashSet<>(size);
            this.chunksWaitingForNeighbors = new Long2ObjectOpenHashMap<>();
        }
    }

    @VisibleForDebug
    protected static class Node {
        @VisibleForDebug
        protected final SectionRenderDispatcher.RenderSection section;
        private byte sourceDirections;
        byte directions;
        @VisibleForDebug
        protected final int step;

        Node(SectionRenderDispatcher.RenderSection section, @Nullable Direction sourceDirection, int step) {
            this.section = section;
            if (sourceDirection != null) {
                this.addSourceDirection(sourceDirection);
            }

            this.step = step;
        }

        void setDirections(byte currentValue, Direction direction) {
            this.directions = (byte)(this.directions | currentValue | 1 << direction.ordinal());
        }

        boolean hasDirection(Direction direction) {
            return (this.directions & 1 << direction.ordinal()) > 0;
        }

        void addSourceDirection(Direction sourceDirection) {
            this.sourceDirections = (byte)(this.sourceDirections | this.sourceDirections | 1 << sourceDirection.ordinal());
        }

        @VisibleForDebug
        protected boolean hasSourceDirection(int direction) {
            return (this.sourceDirections & 1 << direction) > 0;
        }

        boolean hasSourceDirections() {
            return this.sourceDirections != 0;
        }

        @Override
        public int hashCode() {
            return this.section.getOrigin().hashCode();
        }

        @Override
        public boolean equals(Object other) {
            return !(other instanceof SectionOcclusionGraph.Node node) ? false : this.section.getOrigin().equals(node.section.getOrigin());
        }
    }

    static class SectionToNodeMap {
        private final SectionOcclusionGraph.Node[] nodes;

        SectionToNodeMap(int size) {
            this.nodes = new SectionOcclusionGraph.Node[size];
        }

        public void put(SectionRenderDispatcher.RenderSection section, SectionOcclusionGraph.Node node) {
            this.nodes[section.index] = node;
        }

        @Nullable
        public SectionOcclusionGraph.Node get(SectionRenderDispatcher.RenderSection section) {
            int i = section.index;
            return i >= 0 && i < this.nodes.length ? this.nodes[i] : null;
        }
    }
}
