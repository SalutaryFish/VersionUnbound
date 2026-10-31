package net.minecraft.server.level;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.mojang.datafixers.DataFixer;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.thread.BlockableEventLoop;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.LocalMobCapCalculator;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.ChunkScanAccess;
import net.minecraft.world.level.entity.ChunkStatusUpdateListener;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelStorageSource;

public class ServerChunkCache extends ChunkSource {
    private static final List<ChunkStatus> CHUNK_STATUSES = ChunkStatus.getStatusList();
    private final DistanceManager distanceManager;
    final ServerLevel level;
    final Thread mainThread;
    final ThreadedLevelLightEngine lightEngine;
    private final ServerChunkCache.MainThreadExecutor mainThreadProcessor;
    public final ChunkMap chunkMap;
    private final DimensionDataStorage dataStorage;
    private long lastInhabitedUpdate;
    private boolean spawnEnemies = true;
    private boolean spawnFriendlies = true;
    private static final int CACHE_SIZE = 4;
    private final long[] lastChunkPos = new long[4];
    private final ChunkStatus[] lastChunkStatus = new ChunkStatus[4];
    private final ChunkAccess[] lastChunk = new ChunkAccess[4];
    @Nullable
    @VisibleForDebug
    private NaturalSpawner.SpawnState lastSpawnState;

    public ServerChunkCache(
        ServerLevel level,
        LevelStorageSource.LevelStorageAccess levelStorageAccess,
        DataFixer fixerUpper,
        StructureTemplateManager structureManager,
        Executor dispatcher,
        ChunkGenerator generator,
        int viewDistance,
        int simulationDistance,
        boolean sync,
        ChunkProgressListener progressListener,
        ChunkStatusUpdateListener chunkStatusListener,
        Supplier<DimensionDataStorage> overworldDataStorage
    ) {
        this.level = level;
        this.mainThreadProcessor = new ServerChunkCache.MainThreadExecutor(level);
        this.mainThread = Thread.currentThread();
        File file = levelStorageAccess.getDimensionPath(level.dimension()).resolve("data").toFile();
        file.mkdirs();
        this.dataStorage = new DimensionDataStorage(file, fixerUpper, level.registryAccess());
        this.chunkMap = new ChunkMap(
            level,
            levelStorageAccess,
            fixerUpper,
            structureManager,
            dispatcher,
            this.mainThreadProcessor,
            this,
            generator,
            progressListener,
            chunkStatusListener,
            overworldDataStorage,
            viewDistance,
            sync
        );
        this.lightEngine = this.chunkMap.getLightEngine();
        this.distanceManager = this.chunkMap.getDistanceManager();
        this.distanceManager.updateSimulationDistance(simulationDistance);
        this.clearCache();
    }

    public ThreadedLevelLightEngine getLightEngine() {
        return this.lightEngine;
    }

    @Nullable
    private ChunkHolder getVisibleChunkIfPresent(long chunkPos) {
        return this.chunkMap.getVisibleChunkIfPresent(chunkPos);
    }

    public int getTickingGenerated() {
        return this.chunkMap.getTickingGenerated();
    }

    private void storeInCache(long chunkPos, @Nullable ChunkAccess chunk, ChunkStatus chunkStatus) {
        for (int i = 3; i > 0; i--) {
            this.lastChunkPos[i] = this.lastChunkPos[i - 1];
            this.lastChunkStatus[i] = this.lastChunkStatus[i - 1];
            this.lastChunk[i] = this.lastChunk[i - 1];
        }

        this.lastChunkPos[0] = chunkPos;
        this.lastChunkStatus[0] = chunkStatus;
        this.lastChunk[0] = chunk;
    }

    @Nullable
    @Override
    public ChunkAccess getChunk(int x, int z, ChunkStatus chunkStatus, boolean requireChunk) {
        if (Thread.currentThread() != this.mainThread) {
            return CompletableFuture.<ChunkAccess>supplyAsync(() -> this.getChunk(x, z, chunkStatus, requireChunk), this.mainThreadProcessor).join();
        }

        ProfilerFiller profilerFiller = this.level.getProfiler();
        profilerFiller.incrementCounter("getChunk");
        long l = ChunkPos.asLong(x, z);

        for (int i = 0; i < 4; i++) {
            if (l == this.lastChunkPos[i] && chunkStatus == this.lastChunkStatus[i]) {
                ChunkAccess chunkAccess = this.lastChunk[i];
                if (chunkAccess != null || !requireChunk) {
                    return chunkAccess;
                }
            }
        }

        profilerFiller.incrementCounter("getChunkCacheMiss");
        CompletableFuture<ChunkResult<ChunkAccess>> completableFuture = this.getChunkFutureMainThread(x, z, chunkStatus, requireChunk);
        this.mainThreadProcessor.managedBlock(completableFuture::isDone);
        ChunkResult<ChunkAccess> chunkResult = completableFuture.join();
        ChunkAccess chunkAccess2 = chunkResult.orElse(null);
        if (chunkAccess2 == null && requireChunk) {
            throw (IllegalStateException)Util.pauseInIde(new IllegalStateException("Chunk not there when requested: " + chunkResult.getError()));
        }

        this.storeInCache(l, chunkAccess2, chunkStatus);
        return chunkAccess2;
    }

    @Nullable
    @Override
    public LevelChunk getChunkNow(int chunkX, int chunkZ) {
        if (Thread.currentThread() != this.mainThread) {
            return null;
        }

        this.level.getProfiler().incrementCounter("getChunkNow");
        long l = ChunkPos.asLong(chunkX, chunkZ);

        for (int i = 0; i < 4; i++) {
            if (l == this.lastChunkPos[i] && this.lastChunkStatus[i] == ChunkStatus.FULL) {
                ChunkAccess chunkAccess = this.lastChunk[i];
                return chunkAccess instanceof LevelChunk ? (LevelChunk)chunkAccess : null;
            }
        }

        ChunkHolder chunkHolder = this.getVisibleChunkIfPresent(l);
        if (chunkHolder == null) {
            return null;
        }

        ChunkAccess chunkAccess = chunkHolder.getChunkIfPresent(ChunkStatus.FULL);
        if (chunkAccess != null) {
            this.storeInCache(l, chunkAccess, ChunkStatus.FULL);
            if (chunkAccess instanceof LevelChunk) {
                return (LevelChunk)chunkAccess;
            }
        }

        return null;
    }

    private void clearCache() {
        Arrays.fill(this.lastChunkPos, ChunkPos.INVALID_CHUNK_POS);
        Arrays.fill(this.lastChunkStatus, null);
        Arrays.fill(this.lastChunk, null);
    }

    public CompletableFuture<ChunkResult<ChunkAccess>> getChunkFuture(int x, int z, ChunkStatus chunkStatus, boolean requireChunk) {
        boolean bl = Thread.currentThread() == this.mainThread;
        CompletableFuture<ChunkResult<ChunkAccess>> completableFuture;
        if (bl) {
            completableFuture = this.getChunkFutureMainThread(x, z, chunkStatus, requireChunk);
            this.mainThreadProcessor.managedBlock(completableFuture::isDone);
        } else {
            completableFuture = CompletableFuture.<CompletableFuture<ChunkResult<ChunkAccess>>>supplyAsync(
                    () -> this.getChunkFutureMainThread(x, z, chunkStatus, requireChunk), this.mainThreadProcessor
                )
                .thenCompose(completableFuturex -> completableFuturex);
        }

        return completableFuture;
    }

    private CompletableFuture<ChunkResult<ChunkAccess>> getChunkFutureMainThread(int x, int z, ChunkStatus chunkStatus, boolean requireChunk) {
        ChunkPos chunkPos = new ChunkPos(x, z);
        long l = chunkPos.toLong();
        int i = ChunkLevel.byStatus(chunkStatus);
        ChunkHolder chunkHolder = this.getVisibleChunkIfPresent(l);
        if (requireChunk) {
            this.distanceManager.addTicket(TicketType.UNKNOWN, chunkPos, i, chunkPos);
            if (this.chunkAbsent(chunkHolder, i)) {
                ProfilerFiller profilerFiller = this.level.getProfiler();
                profilerFiller.push("chunkLoad");
                this.runDistanceManagerUpdates();
                chunkHolder = this.getVisibleChunkIfPresent(l);
                profilerFiller.pop();
                if (this.chunkAbsent(chunkHolder, i)) {
                    throw (IllegalStateException)Util.pauseInIde(new IllegalStateException("No chunk holder after ticket has been added"));
                }
            }
        }

        return this.chunkAbsent(chunkHolder, i)
            ? GenerationChunkHolder.UNLOADED_CHUNK_FUTURE
            : chunkHolder.scheduleChunkGenerationTask(chunkStatus, this.chunkMap);
    }

    private boolean chunkAbsent(@Nullable ChunkHolder chunkHolder, int status) {
        return chunkHolder == null || chunkHolder.getTicketLevel() > status;
    }

    /**
     * Checks to see if a chunk exists at x, z
     */
    @Override
    public boolean hasChunk(int x, int z) {
        ChunkHolder chunkHolder = this.getVisibleChunkIfPresent(new ChunkPos(x, z).toLong());
        int i = ChunkLevel.byStatus(ChunkStatus.FULL);
        return !this.chunkAbsent(chunkHolder, i);
    }

    @Nullable
    @Override
    public LightChunk getChunkForLighting(int chunkX, int chunkZ) {
        long l = ChunkPos.asLong(chunkX, chunkZ);
        ChunkHolder chunkHolder = this.getVisibleChunkIfPresent(l);
        return chunkHolder == null ? null : chunkHolder.getChunkIfPresentUnchecked(ChunkStatus.INITIALIZE_LIGHT.getParent());
    }

    public Level getLevel() {
        return this.level;
    }

    public boolean pollTask() {
        return this.mainThreadProcessor.pollTask();
    }

    boolean runDistanceManagerUpdates() {
        boolean bl = this.distanceManager.runAllUpdates(this.chunkMap);
        boolean bl2 = this.chunkMap.promoteChunkMap();
        this.chunkMap.runGenerationTasks();
        if (!bl && !bl2) {
            return false;
        }

        this.clearCache();
        return true;
    }

    public boolean isPositionTicking(long chunkPos) {
        ChunkHolder chunkHolder = this.getVisibleChunkIfPresent(chunkPos);
        if (chunkHolder == null) {
            return false;
        } else {
            return !this.level.shouldTickBlocksAt(chunkPos) ? false : chunkHolder.getTickingChunkFuture().getNow(ChunkHolder.UNLOADED_LEVEL_CHUNK).isSuccess();
        }
    }

    public void save(boolean flush) {
        this.runDistanceManagerUpdates();
        this.chunkMap.saveAllChunks(flush);
    }

    @Override
    public void close() throws IOException {
        this.save(true);
        this.lightEngine.close();
        this.chunkMap.close();
    }

    @Override
    public void tick(BooleanSupplier hasTimeLeft, boolean tickChunks) {
        this.level.getProfiler().push("purge");
        if (this.level.tickRateManager().runsNormally() || !tickChunks) {
            this.distanceManager.purgeStaleTickets();
        }

        this.runDistanceManagerUpdates();
        this.level.getProfiler().popPush("chunks");
        if (tickChunks) {
            this.tickChunks();
            this.chunkMap.tick();
        }

        this.level.getProfiler().popPush("unload");
        this.chunkMap.tick(hasTimeLeft);
        this.level.getProfiler().pop();
        this.clearCache();
    }

    private void tickChunks() {
        long l = this.level.getGameTime();
        long m = l - this.lastInhabitedUpdate;
        this.lastInhabitedUpdate = l;
        if (!this.level.isDebug()) {
            ProfilerFiller profilerFiller = this.level.getProfiler();
            profilerFiller.push("pollingChunks");
            profilerFiller.push("filteringLoadedChunks");
            List<ServerChunkCache.ChunkAndHolder> list = Lists.newArrayListWithCapacity(this.chunkMap.size());

            for (ChunkHolder chunkHolder : this.chunkMap.getChunks()) {
                LevelChunk levelChunk = chunkHolder.getTickingChunk();
                if (levelChunk != null) {
                    list.add(new ServerChunkCache.ChunkAndHolder(levelChunk, chunkHolder));
                }
            }

            if (this.level.tickRateManager().runsNormally()) {
                profilerFiller.popPush("naturalSpawnCount");
                int i = this.distanceManager.getNaturalSpawnChunkCount();
                NaturalSpawner.SpawnState spawnState = NaturalSpawner.createState(
                    i, this.level.getAllEntities(), this::getFullChunk, new LocalMobCapCalculator(this.chunkMap)
                );
                this.lastSpawnState = spawnState;
                profilerFiller.popPush("spawnAndTick");
                boolean bl = this.level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING);
                Util.shuffle(list, this.level.random);
                int j = this.level.getGameRules().getInt(GameRules.RULE_RANDOMTICKING);
                boolean bl2 = this.level.getLevelData().getGameTime() % 400L == 0L;

                for (ServerChunkCache.ChunkAndHolder chunkAndHolder : list) {
                    LevelChunk levelChunk2 = chunkAndHolder.chunk;
                    ChunkPos chunkPos = levelChunk2.getPos();
                    if (this.level.isNaturalSpawningAllowed(chunkPos) && this.chunkMap.anyPlayerCloseEnoughForSpawning(chunkPos)) {
                        levelChunk2.incrementInhabitedTime(m);
                        if (bl && (this.spawnEnemies || this.spawnFriendlies) && this.level.getWorldBorder().isWithinBounds(chunkPos)) {
                            NaturalSpawner.spawnForChunk(this.level, levelChunk2, spawnState, this.spawnFriendlies, this.spawnEnemies, bl2);
                        }

                        if (this.level.shouldTickBlocksAt(chunkPos.toLong())) {
                            this.level.tickChunk(levelChunk2, j);
                        }
                    }
                }

                profilerFiller.popPush("customSpawners");
                if (bl) {
                    this.level.tickCustomSpawners(this.spawnEnemies, this.spawnFriendlies);
                }
            }

            profilerFiller.popPush("broadcast");
            list.forEach(chunkAndHolderx -> chunkAndHolderx.holder.broadcastChanges(chunkAndHolderx.chunk));
            profilerFiller.pop();
            profilerFiller.pop();
        }
    }

    private void getFullChunk(long chunkPos, Consumer<LevelChunk> fullChunkGetter) {
        ChunkHolder chunkHolder = this.getVisibleChunkIfPresent(chunkPos);
        if (chunkHolder != null) {
            chunkHolder.getFullChunkFuture().getNow(ChunkHolder.UNLOADED_LEVEL_CHUNK).ifSuccess(fullChunkGetter);
        }
    }

    @Override
    public String gatherStats() {
        return Integer.toString(this.getLoadedChunksCount());
    }

    @VisibleForTesting
    public int getPendingTasksCount() {
        return this.mainThreadProcessor.getPendingTasksCount();
    }

    public ChunkGenerator getGenerator() {
        return this.chunkMap.generator();
    }

    public ChunkGeneratorStructureState getGeneratorState() {
        return this.chunkMap.generatorState();
    }

    public RandomState randomState() {
        return this.chunkMap.randomState();
    }

    @Override
    public int getLoadedChunksCount() {
        return this.chunkMap.size();
    }

    public void blockChanged(BlockPos pos) {
        int i = SectionPos.blockToSectionCoord(pos.getX());
        int j = SectionPos.blockToSectionCoord(pos.getZ());
        ChunkHolder chunkHolder = this.getVisibleChunkIfPresent(ChunkPos.asLong(i, j));
        if (chunkHolder != null) {
            chunkHolder.blockChanged(pos);
        }
    }

    @Override
    public void onLightUpdate(LightLayer type, SectionPos pos) {
        this.mainThreadProcessor.execute(() -> {
            ChunkHolder chunkHolder = this.getVisibleChunkIfPresent(pos.chunk().toLong());
            if (chunkHolder != null) {
                chunkHolder.sectionLightChanged(type, pos.y());
            }
        });
    }

    public <T> void addRegionTicket(TicketType<T> type, ChunkPos pos, int distance, T value) {
        this.distanceManager.addRegionTicket(type, pos, distance, value);
    }

    public <T> void removeRegionTicket(TicketType<T> type, ChunkPos pos, int distance, T value) {
        this.distanceManager.removeRegionTicket(type, pos, distance, value);
    }

    @Override
    public void updateChunkForced(ChunkPos pos, boolean add) {
        this.distanceManager.updateChunkForced(pos, add);
    }

    public void move(ServerPlayer player) {
        if (!player.isRemoved()) {
            this.chunkMap.move(player);
        }
    }

    public void removeEntity(Entity entity) {
        this.chunkMap.removeEntity(entity);
    }

    public void addEntity(Entity entity) {
        this.chunkMap.addEntity(entity);
    }

    public void broadcastAndSend(Entity entity, Packet<?> packet) {
        this.chunkMap.broadcastAndSend(entity, packet);
    }

    public void broadcast(Entity entity, Packet<?> packet) {
        this.chunkMap.broadcast(entity, packet);
    }

    public void setViewDistance(int viewDistance) {
        this.chunkMap.setServerViewDistance(viewDistance);
    }

    public void setSimulationDistance(int simulationDistance) {
        this.distanceManager.updateSimulationDistance(simulationDistance);
    }

    @Override
    public void setSpawnSettings(boolean hostile, boolean peaceful) {
        this.spawnEnemies = hostile;
        this.spawnFriendlies = peaceful;
    }

    public String getChunkDebugData(ChunkPos chunkPos) {
        return this.chunkMap.getChunkDebugData(chunkPos);
    }

    public DimensionDataStorage getDataStorage() {
        return this.dataStorage;
    }

    public PoiManager getPoiManager() {
        return this.chunkMap.getPoiManager();
    }

    public ChunkScanAccess chunkScanner() {
        return this.chunkMap.chunkScanner();
    }

    @Nullable
    @VisibleForDebug
    public NaturalSpawner.SpawnState getLastSpawnState() {
        return this.lastSpawnState;
    }

    public void removeTicketsOnClosing() {
        this.distanceManager.removeTicketsOnClosing();
    }

    record ChunkAndHolder(LevelChunk chunk, ChunkHolder holder) {
    }

    final class MainThreadExecutor extends BlockableEventLoop<Runnable> {
        MainThreadExecutor(final Level level) {
            super("Chunk source main thread executor for " + level.dimension().location());
        }

        /**
         * Drive the executor until the given BooleanSupplier returns true
         */
        @Override
        public void managedBlock(BooleanSupplier isDone) {
            super.managedBlock(() -> MinecraftServer.throwIfFatalException() && isDone.getAsBoolean());
        }

        @Override
        protected Runnable wrapRunnable(Runnable runnable) {
            return runnable;
        }

        @Override
        protected boolean shouldRun(Runnable runnable) {
            return true;
        }

        @Override
        protected boolean scheduleExecutables() {
            return true;
        }

        @Override
        protected Thread getRunningThread() {
            return ServerChunkCache.this.mainThread;
        }

        @Override
        protected void doRunTask(Runnable task) {
            ServerChunkCache.this.level.getProfiler().incrementCounter("runTask");
            super.doRunTask(task);
        }

        @Override
        protected boolean pollTask() {
            if (ServerChunkCache.this.runDistanceManagerUpdates()) {
                return true;
            }

            ServerChunkCache.this.lightEngine.tryScheduleUpdate();
            return super.pollTask();
        }
    }
}
