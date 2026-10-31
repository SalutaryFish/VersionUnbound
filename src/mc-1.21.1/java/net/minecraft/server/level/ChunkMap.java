package net.minecraft.server.level;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Queues;
import com.google.common.collect.Sets;
import com.google.common.collect.ImmutableList.Builder;
import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.Util;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtException;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundChunksBiomesPacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.server.network.ServerPlayerConnection;
import net.minecraft.util.CsvOutput;
import net.minecraft.util.Mth;
import net.minecraft.util.StaticCache2D;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.thread.BlockableEventLoop;
import net.minecraft.util.thread.ProcessorHandle;
import net.minecraft.util.thread.ProcessorMailbox;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.level.chunk.status.ChunkType;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.chunk.storage.ChunkStorage;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.entity.ChunkStatusUpdateListener;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.slf4j.Logger;

public class ChunkMap extends ChunkStorage implements ChunkHolder.PlayerProvider, GeneratingChunkMap {
    private static final ChunkResult<List<ChunkAccess>> UNLOADED_CHUNK_LIST_RESULT = ChunkResult.error("Unloaded chunks found in range");
    private static final CompletableFuture<ChunkResult<List<ChunkAccess>>> UNLOADED_CHUNK_LIST_FUTURE = CompletableFuture.completedFuture(
        UNLOADED_CHUNK_LIST_RESULT
    );
    private static final byte CHUNK_TYPE_REPLACEABLE = -1;
    private static final byte CHUNK_TYPE_UNKNOWN = 0;
    private static final byte CHUNK_TYPE_FULL = 1;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int CHUNK_SAVED_PER_TICK = 200;
    private static final int CHUNK_SAVED_EAGERLY_PER_TICK = 20;
    private static final int EAGER_CHUNK_SAVE_COOLDOWN_IN_MILLIS = 10000;
    public static final int MIN_VIEW_DISTANCE = 2;
    public static final int MAX_VIEW_DISTANCE = 32;
    public static final int FORCED_TICKET_LEVEL = ChunkLevel.byStatus(FullChunkStatus.ENTITY_TICKING);
    /**
     * Chunks in memory. This should only ever be manipulated by the main thread.
     */
    private final Long2ObjectLinkedOpenHashMap<ChunkHolder> updatingChunkMap = new Long2ObjectLinkedOpenHashMap<>();
    /**
     * Same as {@link #loadedChunks}, but immutable for access from other threads. <em>This should never be mutated.</em>
     */
    private volatile Long2ObjectLinkedOpenHashMap<ChunkHolder> visibleChunkMap = this.updatingChunkMap.clone();
    private final Long2ObjectLinkedOpenHashMap<ChunkHolder> pendingUnloads = new Long2ObjectLinkedOpenHashMap<>();
    private final List<ChunkGenerationTask> pendingGenerationTasks = new ArrayList<>();
    final ServerLevel level;
    private final ThreadedLevelLightEngine lightEngine;
    private final BlockableEventLoop<Runnable> mainThreadExecutor;
    private final RandomState randomState;
    private final ChunkGeneratorStructureState chunkGeneratorState;
    private final Supplier<DimensionDataStorage> overworldDataStorage;
    private final PoiManager poiManager;
    /**
     * Chunks that have been requested to be unloaded, but haven't been unloaded yet.
     */
    final LongSet toDrop = new LongOpenHashSet();
    /**
     * True if changes have been made to {@link #loadedChunks} and thus a new copy of the collection has to be made into {@link #immutableLoadedChunks}.
     */
    private boolean modified;
    private final ChunkTaskPriorityQueueSorter queueSorter;
    private final ProcessorHandle<ChunkTaskPriorityQueueSorter.Message<Runnable>> worldgenMailbox;
    private final ProcessorHandle<ChunkTaskPriorityQueueSorter.Message<Runnable>> mainThreadMailbox;
    private final ChunkProgressListener progressListener;
    private final ChunkStatusUpdateListener chunkStatusListener;
    private final ChunkMap.DistanceManager distanceManager;
    private final AtomicInteger tickingGenerated = new AtomicInteger();
    private final String storageName;
    private final PlayerMap playerMap = new PlayerMap();
    private final Int2ObjectMap<ChunkMap.TrackedEntity> entityMap = new Int2ObjectOpenHashMap<>();
    private final Long2ByteMap chunkTypeCache = new Long2ByteOpenHashMap();
    private final Long2LongMap chunkSaveCooldowns = new Long2LongOpenHashMap();
    private final Queue<Runnable> unloadQueue = Queues.newConcurrentLinkedQueue();
    private int serverViewDistance;
    private final WorldGenContext worldGenContext;

    public ChunkMap(
        ServerLevel level,
        LevelStorageSource.LevelStorageAccess levelStorageAccess,
        DataFixer fixerUpper,
        StructureTemplateManager structureManager,
        Executor dispatcher,
        BlockableEventLoop<Runnable> mainThreadExecutor,
        LightChunkGetter lightChunk,
        ChunkGenerator generator,
        ChunkProgressListener progressListener,
        ChunkStatusUpdateListener chunkStatusListener,
        Supplier<DimensionDataStorage> overworldDataStorage,
        int viewDistance,
        boolean sync
    ) {
        super(
            new RegionStorageInfo(levelStorageAccess.getLevelId(), level.dimension(), "chunk"),
            levelStorageAccess.getDimensionPath(level.dimension()).resolve("region"),
            fixerUpper,
            sync
        );
        Path path = levelStorageAccess.getDimensionPath(level.dimension());
        this.storageName = path.getFileName().toString();
        this.level = level;
        RegistryAccess registryAccess = level.registryAccess();
        long l = level.getSeed();
        if (generator instanceof NoiseBasedChunkGenerator noiseBasedChunkGenerator) {
            this.randomState = RandomState.create(noiseBasedChunkGenerator.generatorSettings().value(), registryAccess.lookupOrThrow(Registries.NOISE), l);
        } else {
            this.randomState = RandomState.create(NoiseGeneratorSettings.dummy(), registryAccess.lookupOrThrow(Registries.NOISE), l);
        }

        this.chunkGeneratorState = generator.createState(registryAccess.lookupOrThrow(Registries.STRUCTURE_SET), this.randomState, l);
        this.mainThreadExecutor = mainThreadExecutor;
        ProcessorMailbox<Runnable> processorMailbox = ProcessorMailbox.create(dispatcher, "worldgen");
        ProcessorHandle<Runnable> processorHandle = ProcessorHandle.of("main", mainThreadExecutor::tell);
        this.progressListener = progressListener;
        this.chunkStatusListener = chunkStatusListener;
        ProcessorMailbox<Runnable> processorMailbox2 = ProcessorMailbox.create(dispatcher, "light");
        this.queueSorter = new ChunkTaskPriorityQueueSorter(
            ImmutableList.of(processorMailbox, processorHandle, processorMailbox2), dispatcher, Integer.MAX_VALUE
        );
        this.worldgenMailbox = this.queueSorter.getProcessor(processorMailbox, false);
        this.mainThreadMailbox = this.queueSorter.getProcessor(processorHandle, false);
        this.lightEngine = new ThreadedLevelLightEngine(
            lightChunk, this, this.level.dimensionType().hasSkyLight(), processorMailbox2, this.queueSorter.getProcessor(processorMailbox2, false)
        );
        this.distanceManager = new ChunkMap.DistanceManager(dispatcher, mainThreadExecutor);
        this.overworldDataStorage = overworldDataStorage;
        this.poiManager = new PoiManager(
            new RegionStorageInfo(levelStorageAccess.getLevelId(), level.dimension(), "poi"),
            path.resolve("poi"),
            fixerUpper,
            sync,
            registryAccess,
            level.getServer(),
            level
        );
        this.setServerViewDistance(viewDistance);
        this.worldGenContext = new WorldGenContext(level, generator, structureManager, this.lightEngine, this.mainThreadMailbox);
    }

    protected ChunkGenerator generator() {
        return this.worldGenContext.generator();
    }

    protected ChunkGeneratorStructureState generatorState() {
        return this.chunkGeneratorState;
    }

    protected RandomState randomState() {
        return this.randomState;
    }

    /**
     * Returns the squared distance to the center of the chunk.
     */
    private static double euclideanDistanceSquared(ChunkPos chunkPos, Entity entity) {
        double d = SectionPos.sectionToBlockCoord(chunkPos.x, 8);
        double e = SectionPos.sectionToBlockCoord(chunkPos.z, 8);
        double f = d - entity.getX();
        double g = e - entity.getZ();
        return f * f + g * g;
    }

    /**
     * Checks if a chunk is within a player's view distance.
     */
    boolean isChunkTracked(ServerPlayer player, int x, int z) {
        return player.getChunkTrackingView().contains(x, z) && !player.connection.chunkSender.isPending(ChunkPos.asLong(x, z));
    }

    /**
     * Checks if a chunk is on the edge of the player's view distance.
     */
    private boolean isChunkOnTrackedBorder(ServerPlayer player, int x, int z) {
        if (!this.isChunkTracked(player, x, z)) {
            return false;
        }

        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                if ((i != 0 || j != 0) && !this.isChunkTracked(player, x + i, z + j)) {
                    return true;
                }
            }
        }

        return false;
    }

    protected ThreadedLevelLightEngine getLightEngine() {
        return this.lightEngine;
    }

    @Nullable
    protected ChunkHolder getUpdatingChunkIfPresent(long chunkPos) {
        return this.updatingChunkMap.get(chunkPos);
    }

    @Nullable
    protected ChunkHolder getVisibleChunkIfPresent(long chunkPos) {
        return this.visibleChunkMap.get(chunkPos);
    }

    protected IntSupplier getChunkQueueLevel(long chunkPos) {
        return () -> {
            ChunkHolder chunkHolder = this.getVisibleChunkIfPresent(chunkPos);
            return chunkHolder == null
                ? ChunkTaskPriorityQueue.PRIORITY_LEVEL_COUNT - 1
                : Math.min(chunkHolder.getQueueLevel(), ChunkTaskPriorityQueue.PRIORITY_LEVEL_COUNT - 1);
        };
    }

    public String getChunkDebugData(ChunkPos pos) {
        ChunkHolder chunkHolder = this.getVisibleChunkIfPresent(pos.toLong());
        if (chunkHolder == null) {
            return "null";
        }

        String string = chunkHolder.getTicketLevel() + "\n";
        ChunkStatus chunkStatus = chunkHolder.getLatestStatus();
        ChunkAccess chunkAccess = chunkHolder.getLatestChunk();
        if (chunkStatus != null) {
            string = string + "St: §" + chunkStatus.getIndex() + chunkStatus + "§r\n";
        }

        if (chunkAccess != null) {
            string = string + "Ch: §" + chunkAccess.getPersistedStatus().getIndex() + chunkAccess.getPersistedStatus() + "§r\n";
        }

        FullChunkStatus fullChunkStatus = chunkHolder.getFullStatus();
        string = string + '§' + fullChunkStatus.ordinal() + fullChunkStatus;
        return string + "§r";
    }

    private CompletableFuture<ChunkResult<List<ChunkAccess>>> getChunkRangeFuture(ChunkHolder chunkHolder, int range, IntFunction<ChunkStatus> statusGetter) {
        if (range == 0) {
            ChunkStatus chunkStatus = statusGetter.apply(0);
            return chunkHolder.scheduleChunkGenerationTask(chunkStatus, this).thenApply(chunkResult -> chunkResult.map(List::of));
        }

        List<CompletableFuture<ChunkResult<ChunkAccess>>> list = new ArrayList<>();
        ChunkPos chunkPos = chunkHolder.getPos();

        for (int i = -range; i <= range; i++) {
            for (int j = -range; j <= range; j++) {
                int k = Math.max(Math.abs(j), Math.abs(i));
                long l = ChunkPos.asLong(chunkPos.x + j, chunkPos.z + i);
                ChunkHolder chunkHolder2 = this.getUpdatingChunkIfPresent(l);
                if (chunkHolder2 == null) {
                    return UNLOADED_CHUNK_LIST_FUTURE;
                }

                ChunkStatus chunkStatus2 = statusGetter.apply(k);
                list.add(chunkHolder2.scheduleChunkGenerationTask(chunkStatus2, this));
            }
        }

        return Util.sequence(list).thenApply(listx -> {
            List<ChunkAccess> list2 = Lists.newArrayList();

            for (ChunkResult<ChunkAccess> chunkResult : listx) {
                if (chunkResult == null) {
                    throw this.debugFuturesAndCreateReportedException(new IllegalStateException("At least one of the chunk futures were null"), "n/a");
                }

                ChunkAccess chunkAccess = chunkResult.orElse(null);
                if (chunkAccess == null) {
                    return UNLOADED_CHUNK_LIST_RESULT;
                }

                list2.add(chunkAccess);
            }

            return ChunkResult.of(list2);
        });
    }

    public ReportedException debugFuturesAndCreateReportedException(IllegalStateException exception, String details) {
        StringBuilder stringBuilder = new StringBuilder();
        Consumer<ChunkHolder> consumer = chunkHolder -> chunkHolder.getAllFutures()
            .forEach(
                pair -> {
                    ChunkStatus chunkStatus = pair.getFirst();
                    CompletableFuture<ChunkResult<ChunkAccess>> completableFuture = pair.getSecond();
                    if (completableFuture != null && completableFuture.isDone() && completableFuture.join() == null) {
                        stringBuilder.append(chunkHolder.getPos())
                            .append(" - status: ")
                            .append(chunkStatus)
                            .append(" future: ")
                            .append(completableFuture)
                            .append(System.lineSeparator());
                    }
                }
            );
        stringBuilder.append("Updating:").append(System.lineSeparator());
        this.updatingChunkMap.values().forEach(consumer);
        stringBuilder.append("Visible:").append(System.lineSeparator());
        this.visibleChunkMap.values().forEach(consumer);
        CrashReport crashReport = CrashReport.forThrowable(exception, "Chunk loading");
        CrashReportCategory crashReportCategory = crashReport.addCategory("Chunk loading");
        crashReportCategory.setDetail("Details", details);
        crashReportCategory.setDetail("Futures", stringBuilder);
        return new ReportedException(crashReport);
    }

    public CompletableFuture<ChunkResult<LevelChunk>> prepareEntityTickingChunk(ChunkHolder chunk) {
        return this.getChunkRangeFuture(chunk, 2, i -> ChunkStatus.FULL)
            .thenApplyAsync(chunkResult -> chunkResult.map(list -> (LevelChunk)list.get(list.size() / 2)), this.mainThreadExecutor);
    }

    /**
     * Sets level and loads/unloads chunk.
     *
     * @param holder The {@link net.minecraft.server.level.ChunkHolder} of the chunk
     *               if it is loaded, and null otherwise.
     */
    @Nullable
    ChunkHolder updateChunkScheduling(long chunkPos, int newLevel, @Nullable ChunkHolder holder, int oldLevel) {
        if (!ChunkLevel.isLoaded(oldLevel) && !ChunkLevel.isLoaded(newLevel)) {
            return holder;
        }

        if (holder != null) {
            holder.setTicketLevel(newLevel);
        }

        if (holder != null) {
            if (!ChunkLevel.isLoaded(newLevel)) {
                this.toDrop.add(chunkPos);
            } else {
                this.toDrop.remove(chunkPos);
            }
        }

        if (ChunkLevel.isLoaded(newLevel) && holder == null) {
            holder = this.pendingUnloads.remove(chunkPos);
            if (holder != null) {
                holder.setTicketLevel(newLevel);
            } else {
                holder = new ChunkHolder(new ChunkPos(chunkPos), newLevel, this.level, this.lightEngine, this.queueSorter, this);
            }

            this.updatingChunkMap.put(chunkPos, holder);
            this.modified = true;
        }

        return holder;
    }

    @Override
    public void close() throws IOException {
        try {
            this.queueSorter.close();
            this.poiManager.close();
        } finally {
            super.close();
        }
    }

    protected void saveAllChunks(boolean flush) {
        if (flush) {
            List<ChunkHolder> list = this.visibleChunkMap
                .values()
                .stream()
                .filter(ChunkHolder::wasAccessibleSinceLastSave)
                .peek(ChunkHolder::refreshAccessibility)
                .toList();
            MutableBoolean mutableBoolean = new MutableBoolean();

            do {
                mutableBoolean.setFalse();
                list.stream()
                    .map(chunkHolder -> {
                        this.mainThreadExecutor.managedBlock(chunkHolder::isReadyForSaving);
                        return chunkHolder.getLatestChunk();
                    })
                    .filter(chunkAccess -> chunkAccess instanceof ImposterProtoChunk || chunkAccess instanceof LevelChunk)
                    .filter(this::save)
                    .forEach(chunkAccess -> mutableBoolean.setTrue());
            } while (mutableBoolean.isTrue());

            this.processUnloads(() -> true);
            this.flushWorker();
        } else {
            this.visibleChunkMap.values().forEach(this::saveChunkIfNeeded);
        }
    }

    protected void tick(BooleanSupplier hasMoreTime) {
        ProfilerFiller profilerFiller = this.level.getProfiler();
        profilerFiller.push("poi");
        this.poiManager.tick(hasMoreTime);
        profilerFiller.popPush("chunk_unload");
        if (!this.level.noSave()) {
            this.processUnloads(hasMoreTime);
        }

        profilerFiller.pop();
    }

    public boolean hasWork() {
        return this.lightEngine.hasLightWork()
            || !this.pendingUnloads.isEmpty()
            || !this.updatingChunkMap.isEmpty()
            || this.poiManager.hasWork()
            || !this.toDrop.isEmpty()
            || !this.unloadQueue.isEmpty()
            || this.queueSorter.hasWork()
            || this.distanceManager.hasTickets();
    }

    private void processUnloads(BooleanSupplier hasMoreTime) {
        LongIterator longIterator = this.toDrop.iterator();
        int i = 0;

        while (longIterator.hasNext() && (hasMoreTime.getAsBoolean() || i < 200 || this.toDrop.size() > 2000)) {
            long l = longIterator.nextLong();
            ChunkHolder chunkHolder = this.updatingChunkMap.get(l);
            if (chunkHolder != null) {
                if (chunkHolder.getGenerationRefCount() != 0) {
                    continue;
                }

                this.updatingChunkMap.remove(l);
                this.pendingUnloads.put(l, chunkHolder);
                this.modified = true;
                i++;
                this.scheduleUnload(l, chunkHolder);
            }

            longIterator.remove();
        }

        int j = Math.max(0, this.unloadQueue.size() - 2000);

        Runnable runnable;
        while ((hasMoreTime.getAsBoolean() || j > 0) && (runnable = this.unloadQueue.poll()) != null) {
            j--;
            runnable.run();
        }

        int k = 0;
        ObjectIterator<ChunkHolder> objectIterator = this.visibleChunkMap.values().iterator();

        while (k < 20 && hasMoreTime.getAsBoolean() && objectIterator.hasNext()) {
            if (this.saveChunkIfNeeded(objectIterator.next())) {
                k++;
            }
        }
    }

    private void scheduleUnload(long chunkPos, ChunkHolder chunkHolder) {
        chunkHolder.getSaveSyncFuture().thenRunAsync(() -> {
            if (!chunkHolder.isReadyForSaving()) {
                this.scheduleUnload(chunkPos, chunkHolder);
            } else {
                ChunkAccess chunkAccess = chunkHolder.getLatestChunk();
                if (this.pendingUnloads.remove(chunkPos, chunkHolder) && chunkAccess != null) {
                    if (chunkAccess instanceof LevelChunk levelChunk) {
                        levelChunk.setLoaded(false);
                    }

                    this.save(chunkAccess);
                    if (chunkAccess instanceof LevelChunk levelChunk) {
                        this.level.unload(levelChunk);
                    }

                    this.lightEngine.updateChunkStatus(chunkAccess.getPos());
                    this.lightEngine.tryScheduleUpdate();
                    this.progressListener.onStatusChange(chunkAccess.getPos(), null);
                    this.chunkSaveCooldowns.remove(chunkAccess.getPos().toLong());
                }
            }
        }, this.unloadQueue::add).whenComplete((void_, throwable) -> {
            if (throwable != null) {
                LOGGER.error("Failed to save chunk {}", chunkHolder.getPos(), throwable);
            }
        });
    }

    protected boolean promoteChunkMap() {
        if (!this.modified) {
            return false;
        }

        this.visibleChunkMap = this.updatingChunkMap.clone();
        this.modified = false;
        return true;
    }

    private CompletableFuture<ChunkAccess> scheduleChunkLoad(ChunkPos chunkPos) {
        return this.readChunk(chunkPos).thenApply(optional -> optional.filter(compoundTag -> {
            boolean bl = isChunkDataValid(compoundTag);
            if (!bl) {
                LOGGER.error("Chunk file at {} is missing level data, skipping", chunkPos);
            }

            return bl;
        })).thenApplyAsync(optional -> {
            this.level.getProfiler().incrementCounter("chunkLoad");
            if (optional.isPresent()) {
                ChunkAccess chunkAccess = ChunkSerializer.read(this.level, this.poiManager, this.storageInfo(), chunkPos, optional.get());
                this.markPosition(chunkPos, chunkAccess.getPersistedStatus().getChunkType());
                return chunkAccess;
            } else {
                return this.createEmptyChunk(chunkPos);
            }
        }, this.mainThreadExecutor).exceptionallyAsync(throwable -> this.handleChunkLoadFailure(throwable, chunkPos), this.mainThreadExecutor);
    }

    private static boolean isChunkDataValid(CompoundTag tag) {
        return tag.contains("Status", 8);
    }

    private ChunkAccess handleChunkLoadFailure(Throwable exception, ChunkPos chunkPos) {
        Throwable throwable = exception instanceof CompletionException completionException ? completionException.getCause() : exception;
        Throwable throwable2 = throwable instanceof ReportedException reportedException ? reportedException.getCause() : throwable;
        boolean bl = throwable2 instanceof Error;
        boolean bl2 = throwable2 instanceof IOException || throwable2 instanceof NbtException;
        if (!bl) {
            if (!bl2) {
            }

            this.level.getServer().reportChunkLoadFailure(throwable2, this.storageInfo(), chunkPos);
            return this.createEmptyChunk(chunkPos);
        } else {
            CrashReport crashReport = CrashReport.forThrowable(exception, "Exception loading chunk");
            CrashReportCategory crashReportCategory = crashReport.addCategory("Chunk being loaded");
            crashReportCategory.setDetail("pos", chunkPos);
            this.markPositionReplaceable(chunkPos);
            throw new ReportedException(crashReport);
        }
    }

    private ChunkAccess createEmptyChunk(ChunkPos chunkPos) {
        this.markPositionReplaceable(chunkPos);
        return new ProtoChunk(chunkPos, UpgradeData.EMPTY, this.level, this.level.registryAccess().registryOrThrow(Registries.BIOME), null);
    }

    private void markPositionReplaceable(ChunkPos chunkPos) {
        this.chunkTypeCache.put(chunkPos.toLong(), (byte)-1);
    }

    private byte markPosition(ChunkPos chunkPos, ChunkType chunkType) {
        return this.chunkTypeCache.put(chunkPos.toLong(), (byte)(chunkType == ChunkType.PROTOCHUNK ? -1 : 1));
    }

    @Override
    public GenerationChunkHolder acquireGeneration(long chunkPos) {
        ChunkHolder chunkHolder = this.updatingChunkMap.get(chunkPos);
        chunkHolder.increaseGenerationRefCount();
        return chunkHolder;
    }

    @Override
    public void releaseGeneration(GenerationChunkHolder chunk) {
        chunk.decreaseGenerationRefCount();
    }

    @Override
    public CompletableFuture<ChunkAccess> applyStep(GenerationChunkHolder chunk, ChunkStep step, StaticCache2D<GenerationChunkHolder> cache) {
        ChunkPos chunkPos = chunk.getPos();
        if (step.targetStatus() == ChunkStatus.EMPTY) {
            return this.scheduleChunkLoad(chunkPos);
        }

        try {
            GenerationChunkHolder generationChunkHolder = cache.get(chunkPos.x, chunkPos.z);
            ChunkAccess chunkAccess = generationChunkHolder.getChunkIfPresentUnchecked(step.targetStatus().getParent());
            if (chunkAccess == null) {
                throw new IllegalStateException("Parent chunk missing");
            }

            CompletableFuture<ChunkAccess> completableFuture = step.apply(this.worldGenContext, cache, chunkAccess);
            this.progressListener.onStatusChange(chunkPos, step.targetStatus());
            return completableFuture;
        } catch (Exception exception) {
            exception.getStackTrace();
            CrashReport crashReport = CrashReport.forThrowable(exception, "Exception generating new chunk");
            CrashReportCategory crashReportCategory = crashReport.addCategory("Chunk to be generated");
            crashReportCategory.setDetail("Status being generated", () -> step.targetStatus().getName());
            crashReportCategory.setDetail("Location", String.format(Locale.ROOT, "%d,%d", chunkPos.x, chunkPos.z));
            crashReportCategory.setDetail("Position hash", ChunkPos.asLong(chunkPos.x, chunkPos.z));
            crashReportCategory.setDetail("Generator", this.generator());
            this.mainThreadExecutor.execute(() -> {
                throw new ReportedException(crashReport);
            });
            throw new ReportedException(crashReport);
        }
    }

    @Override
    public ChunkGenerationTask scheduleGenerationTask(ChunkStatus targetStatus, ChunkPos pos) {
        ChunkGenerationTask chunkGenerationTask = ChunkGenerationTask.create(this, targetStatus, pos);
        this.pendingGenerationTasks.add(chunkGenerationTask);
        return chunkGenerationTask;
    }

    private void runGenerationTask(ChunkGenerationTask task) {
        this.worldgenMailbox.tell(ChunkTaskPriorityQueueSorter.message(task.getCenter(), () -> {
            CompletableFuture<?> completableFuture = task.runUntilWait();
            if (completableFuture != null) {
                completableFuture.thenRun(() -> this.runGenerationTask(task));
            }
        }));
    }

    @Override
    public void runGenerationTasks() {
        this.pendingGenerationTasks.forEach(this::runGenerationTask);
        this.pendingGenerationTasks.clear();
    }

    public CompletableFuture<ChunkResult<LevelChunk>> prepareTickingChunk(ChunkHolder holder) {
        CompletableFuture<ChunkResult<List<ChunkAccess>>> completableFuture = this.getChunkRangeFuture(holder, 1, i -> ChunkStatus.FULL);
        CompletableFuture<ChunkResult<LevelChunk>> completableFuture2 = completableFuture.<ChunkResult<LevelChunk>>thenApplyAsync(
                chunkResult -> chunkResult.map(list -> (LevelChunk)list.get(list.size() / 2)),
                runnable -> this.mainThreadMailbox.tell(ChunkTaskPriorityQueueSorter.message(holder, runnable))
            )
            .thenApplyAsync(chunkResult -> chunkResult.ifSuccess(levelChunk -> {
                levelChunk.postProcessGeneration();
                this.level.startTickingChunk(levelChunk);
                CompletableFuture<?> completableFuturex = holder.getSendSyncFuture();
                if (completableFuturex.isDone()) {
                    this.onChunkReadyToSend(levelChunk);
                } else {
                    completableFuturex.thenAcceptAsync(object -> this.onChunkReadyToSend(levelChunk), this.mainThreadExecutor);
                }
            }), this.mainThreadExecutor);
        completableFuture2.handle((chunkResult, throwable) -> {
            this.tickingGenerated.getAndIncrement();
            return null;
        });
        return completableFuture2;
    }

    private void onChunkReadyToSend(LevelChunk chunk) {
        ChunkPos chunkPos = chunk.getPos();

        for (ServerPlayer serverPlayer : this.playerMap.getAllPlayers()) {
            if (serverPlayer.getChunkTrackingView().contains(chunkPos)) {
                markChunkPendingToSend(serverPlayer, chunk);
            }
        }
    }

    public CompletableFuture<ChunkResult<LevelChunk>> prepareAccessibleChunk(ChunkHolder holder) {
        return this.getChunkRangeFuture(holder, 1, ChunkLevel::getStatusAroundFullChunk)
            .thenApplyAsync(
                chunkResult -> chunkResult.map(list -> (LevelChunk)list.get(list.size() / 2)),
                runnable -> this.mainThreadMailbox.tell(ChunkTaskPriorityQueueSorter.message(holder, runnable))
            );
    }

    public int getTickingGenerated() {
        return this.tickingGenerated.get();
    }

    private boolean saveChunkIfNeeded(ChunkHolder holder) {
        if (holder.wasAccessibleSinceLastSave() && holder.isReadyForSaving()) {
            ChunkAccess chunkAccess = holder.getLatestChunk();
            if (!(chunkAccess instanceof ImposterProtoChunk) && !(chunkAccess instanceof LevelChunk)) {
                return false;
            }

            long l = chunkAccess.getPos().toLong();
            long m = this.chunkSaveCooldowns.getOrDefault(l, -1L);
            long n = System.currentTimeMillis();
            if (n < m) {
                return false;
            }

            boolean bl = this.save(chunkAccess);
            holder.refreshAccessibility();
            if (bl) {
                this.chunkSaveCooldowns.put(l, n + 10000L);
            }

            return bl;
        } else {
            return false;
        }
    }

    private boolean save(ChunkAccess chunk) {
        this.poiManager.flush(chunk.getPos());
        if (!chunk.isUnsaved()) {
            return false;
        }

        chunk.setUnsaved(false);
        ChunkPos chunkPos = chunk.getPos();

        try {
            ChunkStatus chunkStatus = chunk.getPersistedStatus();
            if (chunkStatus.getChunkType() != ChunkType.LEVELCHUNK) {
                if (this.isExistingChunkFull(chunkPos)) {
                    return false;
                }

                if (chunkStatus == ChunkStatus.EMPTY && chunk.getAllStarts().values().stream().noneMatch(StructureStart::isValid)) {
                    return false;
                }
            }

            this.level.getProfiler().incrementCounter("chunkSave");
            CompoundTag compoundTag = ChunkSerializer.write(this.level, chunk);
            this.write(chunkPos, compoundTag).exceptionally(throwable -> {
                this.level.getServer().reportChunkSaveFailure(throwable, this.storageInfo(), chunkPos);
                return null;
            });
            this.markPosition(chunkPos, chunkStatus.getChunkType());
            return true;
        } catch (Exception exception) {
            this.level.getServer().reportChunkSaveFailure(exception, this.storageInfo(), chunkPos);
            return false;
        }
    }

    private boolean isExistingChunkFull(ChunkPos chunkPos) {
        byte b = this.chunkTypeCache.get(chunkPos.toLong());
        if (b != 0) {
            return b == 1;
        }

        CompoundTag compoundTag;
        try {
            compoundTag = this.readChunk(chunkPos).join().orElse(null);
            if (compoundTag == null) {
                this.markPositionReplaceable(chunkPos);
                return false;
            }
        } catch (Exception exception) {
            LOGGER.error("Failed to read chunk {}", chunkPos, exception);
            this.markPositionReplaceable(chunkPos);
            return false;
        }

        ChunkType chunkType = ChunkSerializer.getChunkTypeFromTag(compoundTag);
        return this.markPosition(chunkPos, chunkType) == 1;
    }

    protected void setServerViewDistance(int viewDistance) {
        int i = Mth.clamp(viewDistance, 2, 32);
        if (i != this.serverViewDistance) {
            this.serverViewDistance = i;
            this.distanceManager.updatePlayerTickets(this.serverViewDistance);

            for (ServerPlayer serverPlayer : this.playerMap.getAllPlayers()) {
                this.updateChunkTracking(serverPlayer);
            }
        }
    }

    int getPlayerViewDistance(ServerPlayer player) {
        return Mth.clamp(player.requestedViewDistance(), 2, this.serverViewDistance);
    }

    private void markChunkPendingToSend(ServerPlayer player, ChunkPos chunkPos) {
        LevelChunk levelChunk = this.getChunkToSend(chunkPos.toLong());
        if (levelChunk != null) {
            markChunkPendingToSend(player, levelChunk);
        }
    }

    private static void markChunkPendingToSend(ServerPlayer player, LevelChunk chunk) {
        player.connection.chunkSender.markChunkPendingToSend(chunk);
    }

    private static void dropChunk(ServerPlayer player, ChunkPos chunkPos) {
        player.connection.chunkSender.dropChunk(player, chunkPos);
    }

    @Nullable
    public LevelChunk getChunkToSend(long chunkPos) {
        ChunkHolder chunkHolder = this.getVisibleChunkIfPresent(chunkPos);
        return chunkHolder == null ? null : chunkHolder.getChunkToSend();
    }

    public int size() {
        return this.visibleChunkMap.size();
    }

    public net.minecraft.server.level.DistanceManager getDistanceManager() {
        return this.distanceManager;
    }

    protected Iterable<ChunkHolder> getChunks() {
        return Iterables.unmodifiableIterable(this.visibleChunkMap.values());
    }

    void dumpChunks(Writer writer) throws IOException {
        CsvOutput csvOutput = CsvOutput.builder()
            .addColumn("x")
            .addColumn("z")
            .addColumn("level")
            .addColumn("in_memory")
            .addColumn("status")
            .addColumn("full_status")
            .addColumn("accessible_ready")
            .addColumn("ticking_ready")
            .addColumn("entity_ticking_ready")
            .addColumn("ticket")
            .addColumn("spawning")
            .addColumn("block_entity_count")
            .addColumn("ticking_ticket")
            .addColumn("ticking_level")
            .addColumn("block_ticks")
            .addColumn("fluid_ticks")
            .build(writer);
        TickingTracker tickingTracker = this.distanceManager.tickingTracker();

        for (Entry<ChunkHolder> entry : this.visibleChunkMap.long2ObjectEntrySet()) {
            long l = entry.getLongKey();
            ChunkPos chunkPos = new ChunkPos(l);
            ChunkHolder chunkHolder = entry.getValue();
            Optional<ChunkAccess> optional = Optional.ofNullable(chunkHolder.getLatestChunk());
            Optional<LevelChunk> optional2 = optional.flatMap(
                chunkAccess -> chunkAccess instanceof LevelChunk ? Optional.of((LevelChunk)chunkAccess) : Optional.empty()
            );
            csvOutput.writeRow(
                chunkPos.x,
                chunkPos.z,
                chunkHolder.getTicketLevel(),
                optional.isPresent(),
                optional.map(ChunkAccess::getPersistedStatus).orElse(null),
                optional2.map(LevelChunk::getFullStatus).orElse(null),
                printFuture(chunkHolder.getFullChunkFuture()),
                printFuture(chunkHolder.getTickingChunkFuture()),
                printFuture(chunkHolder.getEntityTickingChunkFuture()),
                this.distanceManager.getTicketDebugString(l),
                this.anyPlayerCloseEnoughForSpawning(chunkPos),
                optional2.<Integer>map(levelChunk -> levelChunk.getBlockEntities().size()).orElse(0),
                tickingTracker.getTicketDebugString(l),
                tickingTracker.getLevel(l),
                optional2.<Integer>map(levelChunk -> levelChunk.getBlockTicks().count()).orElse(0),
                optional2.<Integer>map(levelChunk -> levelChunk.getFluidTicks().count()).orElse(0)
            );
        }
    }

    private static String printFuture(CompletableFuture<ChunkResult<LevelChunk>> future) {
        try {
            ChunkResult<LevelChunk> chunkResult = future.getNow(null);
            if (chunkResult != null) {
                return chunkResult.isSuccess() ? "done" : "unloaded";
            } else {
                return "not completed";
            }
        } catch (CompletionException completionException) {
            return "failed " + completionException.getCause().getMessage();
        } catch (CancellationException cancellationException) {
            return "cancelled";
        }
    }

    private CompletableFuture<Optional<CompoundTag>> readChunk(ChunkPos pos) {
        return this.read(pos).thenApplyAsync(optional -> optional.map(this::upgradeChunkTag), Util.backgroundExecutor());
    }

    private CompoundTag upgradeChunkTag(CompoundTag tag) {
        return this.upgradeChunkTag(this.level.dimension(), this.overworldDataStorage, tag, this.generator().getTypeNameForDataFixer());
    }

    boolean anyPlayerCloseEnoughForSpawning(ChunkPos chunkPos) {
        if (!this.distanceManager.hasPlayersNearby(chunkPos.toLong())) {
            return false;
        }

        for (ServerPlayer serverPlayer : this.playerMap.getAllPlayers()) {
            if (this.playerIsCloseEnoughForSpawning(serverPlayer, chunkPos)) {
                return true;
            }
        }

        return false;
    }

    public List<ServerPlayer> getPlayersCloseForSpawning(ChunkPos chunkPos) {
        long l = chunkPos.toLong();
        if (!this.distanceManager.hasPlayersNearby(l)) {
            return List.of();
        }

        Builder<ServerPlayer> builder = ImmutableList.builder();

        for (ServerPlayer serverPlayer : this.playerMap.getAllPlayers()) {
            if (this.playerIsCloseEnoughForSpawning(serverPlayer, chunkPos)) {
                builder.add(serverPlayer);
            }
        }

        return builder.build();
    }

    private boolean playerIsCloseEnoughForSpawning(ServerPlayer player, ChunkPos chunkPos) {
        if (player.isSpectator()) {
            return false;
        }

        double d = euclideanDistanceSquared(chunkPos, player);
        return d < 16384.0;
    }

    private boolean skipPlayer(ServerPlayer player) {
        return player.isSpectator() && !this.level.getGameRules().getBoolean(GameRules.RULE_SPECTATORSGENERATECHUNKS);
    }

    void updatePlayerStatus(ServerPlayer player, boolean track) {
        boolean bl = this.skipPlayer(player);
        boolean bl2 = this.playerMap.ignoredOrUnknown(player);
        if (track) {
            this.playerMap.addPlayer(player, bl);
            this.updatePlayerPos(player);
            if (!bl) {
                this.distanceManager.addPlayer(SectionPos.of(player), player);
            }

            player.setChunkTrackingView(ChunkTrackingView.EMPTY);
            this.updateChunkTracking(player);
        } else {
            SectionPos sectionPos = player.getLastSectionPos();
            this.playerMap.removePlayer(player);
            if (!bl2) {
                this.distanceManager.removePlayer(sectionPos, player);
            }

            this.applyChunkTrackingView(player, ChunkTrackingView.EMPTY);
        }
    }

    private void updatePlayerPos(ServerPlayer player) {
        SectionPos sectionPos = SectionPos.of(player);
        player.setLastSectionPos(sectionPos);
    }

    public void move(ServerPlayer player) {
        for (ChunkMap.TrackedEntity trackedEntity : this.entityMap.values()) {
            if (trackedEntity.entity == player) {
                trackedEntity.updatePlayers(this.level.players());
            } else {
                trackedEntity.updatePlayer(player);
            }
        }

        SectionPos sectionPos = player.getLastSectionPos();
        SectionPos sectionPos2 = SectionPos.of(player);
        boolean bl = this.playerMap.ignored(player);
        boolean bl2 = this.skipPlayer(player);
        boolean bl3 = sectionPos.asLong() != sectionPos2.asLong();
        if (bl3 || bl != bl2) {
            this.updatePlayerPos(player);
            if (!bl) {
                this.distanceManager.removePlayer(sectionPos, player);
            }

            if (!bl2) {
                this.distanceManager.addPlayer(sectionPos2, player);
            }

            if (!bl && bl2) {
                this.playerMap.ignorePlayer(player);
            }

            if (bl && !bl2) {
                this.playerMap.unIgnorePlayer(player);
            }

            this.updateChunkTracking(player);
        }
    }

    private void updateChunkTracking(ServerPlayer player) {
        ChunkPos chunkPos = player.chunkPosition();
        int i = this.getPlayerViewDistance(player);
        if (!(
            player.getChunkTrackingView() instanceof ChunkTrackingView.Positioned positioned
                && positioned.center().equals(chunkPos)
                && positioned.viewDistance() == i
        )) {
            this.applyChunkTrackingView(player, ChunkTrackingView.of(chunkPos, i));
        }
    }

    private void applyChunkTrackingView(ServerPlayer player, ChunkTrackingView chunkTrackingView) {
        if (player.level() == this.level) {
            ChunkTrackingView chunkTrackingView2 = player.getChunkTrackingView();
            if (chunkTrackingView instanceof ChunkTrackingView.Positioned positioned
                && !(chunkTrackingView2 instanceof ChunkTrackingView.Positioned positioned2 && positioned2.center().equals(positioned.center()))) {
                player.connection.send(new ClientboundSetChunkCacheCenterPacket(positioned.center().x, positioned.center().z));
            }

            ChunkTrackingView.difference(
                chunkTrackingView2, chunkTrackingView, chunkPos -> this.markChunkPendingToSend(player, chunkPos), chunkPos -> dropChunk(player, chunkPos)
            );
            player.setChunkTrackingView(chunkTrackingView);
        }
    }

    /**
     * Returns the players tracking the given chunk.
     */
    @Override
    public List<ServerPlayer> getPlayers(ChunkPos pos, boolean boundaryOnly) {
        Set<ServerPlayer> set = this.playerMap.getAllPlayers();
        Builder<ServerPlayer> builder = ImmutableList.builder();

        for (ServerPlayer serverPlayer : set) {
            if (boundaryOnly && this.isChunkOnTrackedBorder(serverPlayer, pos.x, pos.z) || !boundaryOnly && this.isChunkTracked(serverPlayer, pos.x, pos.z)) {
                builder.add(serverPlayer);
            }
        }

        return builder.build();
    }

    protected void addEntity(Entity entity) {
        if (!(entity instanceof EnderDragonPart)) {
            EntityType<?> entityType = entity.getType();
            int i = entityType.clientTrackingRange() * 16;
            if (i != 0) {
                int j = entityType.updateInterval();
                if (this.entityMap.containsKey(entity.getId())) {
                    throw (IllegalStateException)Util.pauseInIde(new IllegalStateException("Entity is already tracked!"));
                }

                ChunkMap.TrackedEntity trackedEntity = new ChunkMap.TrackedEntity(entity, i, j, entityType.trackDeltas());
                this.entityMap.put(entity.getId(), trackedEntity);
                trackedEntity.updatePlayers(this.level.players());
                if (entity instanceof ServerPlayer serverPlayer) {
                    this.updatePlayerStatus(serverPlayer, true);

                    for (ChunkMap.TrackedEntity trackedEntity2 : this.entityMap.values()) {
                        if (trackedEntity2.entity != serverPlayer) {
                            trackedEntity2.updatePlayer(serverPlayer);
                        }
                    }
                }
            }
        }
    }

    protected void removeEntity(Entity entity) {
        if (entity instanceof ServerPlayer serverPlayer) {
            this.updatePlayerStatus(serverPlayer, false);

            for (ChunkMap.TrackedEntity trackedEntity : this.entityMap.values()) {
                trackedEntity.removePlayer(serverPlayer);
            }
        }

        ChunkMap.TrackedEntity trackedEntity2 = this.entityMap.remove(entity.getId());
        if (trackedEntity2 != null) {
            trackedEntity2.broadcastRemoved();
        }
    }

    protected void tick() {
        for (ServerPlayer serverPlayer : this.playerMap.getAllPlayers()) {
            this.updateChunkTracking(serverPlayer);
        }

        List<ServerPlayer> list = Lists.newArrayList();
        List<ServerPlayer> list2 = this.level.players();

        for (ChunkMap.TrackedEntity trackedEntity : this.entityMap.values()) {
            SectionPos sectionPos = trackedEntity.lastSectionPos;
            SectionPos sectionPos2 = SectionPos.of(trackedEntity.entity);
            boolean bl = !Objects.equals(sectionPos, sectionPos2);
            if (bl) {
                trackedEntity.updatePlayers(list2);
                Entity entity = trackedEntity.entity;
                if (entity instanceof ServerPlayer) {
                    list.add((ServerPlayer)entity);
                }

                trackedEntity.lastSectionPos = sectionPos2;
            }

            if (bl || this.distanceManager.inEntityTickingRange(sectionPos2.chunk().toLong())) {
                trackedEntity.serverEntity.sendChanges();
            }
        }

        if (!list.isEmpty()) {
            for (ChunkMap.TrackedEntity trackedEntity : this.entityMap.values()) {
                trackedEntity.updatePlayers(list);
            }
        }
    }

    public void broadcast(Entity entity, Packet<?> packet) {
        ChunkMap.TrackedEntity trackedEntity = this.entityMap.get(entity.getId());
        if (trackedEntity != null) {
            trackedEntity.broadcast(packet);
        }
    }

    protected void broadcastAndSend(Entity entity, Packet<?> packet) {
        ChunkMap.TrackedEntity trackedEntity = this.entityMap.get(entity.getId());
        if (trackedEntity != null) {
            trackedEntity.broadcastAndSend(packet);
        }
    }

    public void resendBiomesForChunks(List<ChunkAccess> chunks) {
        Map<ServerPlayer, List<LevelChunk>> map = new HashMap<>();

        for (ChunkAccess chunkAccess : chunks) {
            ChunkPos chunkPos = chunkAccess.getPos();
            LevelChunk levelChunk2;
            if (chunkAccess instanceof LevelChunk levelChunk) {
                levelChunk2 = levelChunk;
            } else {
                levelChunk2 = this.level.getChunk(chunkPos.x, chunkPos.z);
            }

            for (ServerPlayer serverPlayer : this.getPlayers(chunkPos, false)) {
                map.computeIfAbsent(serverPlayer, serverPlayerx -> new ArrayList<>()).add(levelChunk2);
            }
        }

        map.forEach((serverPlayerx, list) -> serverPlayerx.connection.send(ClientboundChunksBiomesPacket.forChunks((List<LevelChunk>)list)));
    }

    protected PoiManager getPoiManager() {
        return this.poiManager;
    }

    public String getStorageName() {
        return this.storageName;
    }

    void onFullChunkStatusChange(ChunkPos chunkPos, FullChunkStatus fullChunkStatus) {
        this.chunkStatusListener.onChunkStatusChange(chunkPos, fullChunkStatus);
    }

    public void waitForLightBeforeSending(ChunkPos chunkPos, int range) {
        int i = range + 1;
        ChunkPos.rangeClosed(chunkPos, i).forEach(chunkPosx -> {
            ChunkHolder chunkHolder = this.getVisibleChunkIfPresent(chunkPosx.toLong());
            if (chunkHolder != null) {
                chunkHolder.addSendDependency(this.lightEngine.waitForPendingTasks(chunkPosx.x, chunkPosx.z));
            }
        });
    }

    class DistanceManager extends net.minecraft.server.level.DistanceManager {
        protected DistanceManager(final Executor dispatcher, final Executor mainThreadExecutor) {
            super(dispatcher, mainThreadExecutor);
        }

        @Override
        protected boolean isChunkToRemove(long chunkPos) {
            return ChunkMap.this.toDrop.contains(chunkPos);
        }

        @Nullable
        @Override
        protected ChunkHolder getChunk(long chunkPos) {
            return ChunkMap.this.getUpdatingChunkIfPresent(chunkPos);
        }

        @Nullable
        @Override
        protected ChunkHolder updateChunkScheduling(long chunkPos, int newLevel, @Nullable ChunkHolder holder, int oldLevel) {
            return ChunkMap.this.updateChunkScheduling(chunkPos, newLevel, holder, oldLevel);
        }
    }

    class TrackedEntity {
        final ServerEntity serverEntity;
        final Entity entity;
        private final int range;
        SectionPos lastSectionPos;
        private final Set<ServerPlayerConnection> seenBy = Sets.newIdentityHashSet();

        public TrackedEntity(final Entity entity, final int range, final int updateInterval, final boolean trackDelta) {
            this.serverEntity = new ServerEntity(ChunkMap.this.level, entity, updateInterval, trackDelta, this::broadcast);
            this.entity = entity;
            this.range = range;
            this.lastSectionPos = SectionPos.of(entity);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof ChunkMap.TrackedEntity ? ((ChunkMap.TrackedEntity)other).entity.getId() == this.entity.getId() : false;
        }

        @Override
        public int hashCode() {
            return this.entity.getId();
        }

        public void broadcast(Packet<?> packet) {
            for (ServerPlayerConnection serverPlayerConnection : this.seenBy) {
                serverPlayerConnection.send(packet);
            }
        }

        public void broadcastAndSend(Packet<?> packet) {
            this.broadcast(packet);
            if (this.entity instanceof ServerPlayer) {
                ((ServerPlayer)this.entity).connection.send(packet);
            }
        }

        public void broadcastRemoved() {
            for (ServerPlayerConnection serverPlayerConnection : this.seenBy) {
                this.serverEntity.removePairing(serverPlayerConnection.getPlayer());
            }
        }

        public void removePlayer(ServerPlayer player) {
            if (this.seenBy.remove(player.connection)) {
                this.serverEntity.removePairing(player);
            }
        }

        public void updatePlayer(ServerPlayer player) {
            if (player != this.entity) {
                Vec3 vec3 = player.position().subtract(this.entity.position());
                int i = ChunkMap.this.getPlayerViewDistance(player);
                double d = Math.min(this.getEffectiveRange(), i * 16);
                double e = vec3.x * vec3.x + vec3.z * vec3.z;
                double f = d * d;
                boolean bl = e <= f
                    && this.entity.broadcastToPlayer(player)
                    && ChunkMap.this.isChunkTracked(player, this.entity.chunkPosition().x, this.entity.chunkPosition().z);
                if (bl) {
                    if (this.seenBy.add(player.connection)) {
                        this.serverEntity.addPairing(player);
                    }
                } else if (this.seenBy.remove(player.connection)) {
                    this.serverEntity.removePairing(player);
                }
            }
        }

        private int scaledRange(int trackingDistance) {
            return ChunkMap.this.level.getServer().getScaledTrackingDistance(trackingDistance);
        }

        private int getEffectiveRange() {
            int i = this.range;

            for (Entity entity : this.entity.getIndirectPassengers()) {
                int j = entity.getType().clientTrackingRange() * 16;
                if (j > i) {
                    i = j;
                }
            }

            return this.scaledRange(i);
        }

        public void updatePlayers(List<ServerPlayer> playersList) {
            for (ServerPlayer serverPlayer : playersList) {
                this.updatePlayer(serverPlayer);
            }
        }
    }
}
