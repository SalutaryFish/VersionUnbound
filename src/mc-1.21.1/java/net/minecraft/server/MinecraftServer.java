package net.minecraft.server;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.GameProfileRepository;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Writer;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.net.Proxy;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.FileUtil;
import net.minecraft.ReportType;
import net.minecraft.ReportedException;
import net.minecraft.SharedConstants;
import net.minecraft.SystemReport;
import net.minecraft.Util;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.MiscOverworldFeatures;
import net.minecraft.gametest.framework.GameTestTicker;
import net.minecraft.network.chat.ChatDecorator;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundChangeDifficultyPacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.network.protocol.status.ServerStatus;
import net.minecraft.obfuscate.DontObfuscate;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.bossevents.CustomBossEvents;
import net.minecraft.server.level.DemoMode;
import net.minecraft.server.level.PlayerRespawnLogic;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.server.level.progress.ChunkProgressListenerFactory;
import net.minecraft.server.network.ServerConnectionListener;
import net.minecraft.server.network.TextFilter;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.CloseableResourceManager;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.server.players.UserWhiteList;
import net.minecraft.util.Crypt;
import net.minecraft.util.CryptException;
import net.minecraft.util.ModCheck;
import net.minecraft.util.Mth;
import net.minecraft.util.NativeModuleLister;
import net.minecraft.util.RandomSource;
import net.minecraft.util.SignatureValidator;
import net.minecraft.util.TimeUtil;
import net.minecraft.util.debugchart.RemoteDebugSampleType;
import net.minecraft.util.debugchart.SampleLogger;
import net.minecraft.util.debugchart.TpsDebugDimensions;
import net.minecraft.util.profiling.EmptyProfileResults;
import net.minecraft.util.profiling.ProfileResults;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.profiling.ResultField;
import net.minecraft.util.profiling.SingleTickProfiler;
import net.minecraft.util.profiling.jfr.JvmProfiler;
import net.minecraft.util.profiling.jfr.callback.ProfiledDuration;
import net.minecraft.util.profiling.metrics.profiling.ActiveMetricsRecorder;
import net.minecraft.util.profiling.metrics.profiling.InactiveMetricsRecorder;
import net.minecraft.util.profiling.metrics.profiling.MetricsRecorder;
import net.minecraft.util.profiling.metrics.profiling.ServerMetricsSamplersProvider;
import net.minecraft.util.profiling.metrics.storage.MetricsPersister;
import net.minecraft.util.thread.ReentrantBlockableEventLoop;
import net.minecraft.world.Difficulty;
import net.minecraft.world.RandomSequences;
import net.minecraft.world.entity.ai.village.VillageSiege;
import net.minecraft.world.entity.npc.CatSpawner;
import net.minecraft.world.entity.npc.WanderingTraderSpawner;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.level.ForcedChunksSavedData;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.border.BorderChangeListener;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.storage.ChunkIOErrorReporter;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.PatrolSpawner;
import net.minecraft.world.level.levelgen.PhantomSpawner;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.CommandStorage;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PlayerDataStorage;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.storage.WorldData;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

public abstract class MinecraftServer extends ReentrantBlockableEventLoop<TickTask> implements ServerInfo, ChunkIOErrorReporter, CommandSource, AutoCloseable {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String VANILLA_BRAND = "vanilla";
    private static final float AVERAGE_TICK_TIME_SMOOTHING = 0.8F;
    private static final int TICK_STATS_SPAN = 100;
    private static final long OVERLOADED_THRESHOLD_NANOS = 20L * TimeUtil.NANOSECONDS_PER_SECOND / 20L;
    private static final int OVERLOADED_TICKS_THRESHOLD = 20;
    private static final long OVERLOADED_WARNING_INTERVAL_NANOS = 10L * TimeUtil.NANOSECONDS_PER_SECOND;
    private static final int OVERLOADED_TICKS_WARNING_INTERVAL = 100;
    private static final long STATUS_EXPIRE_TIME_NANOS = 5L * TimeUtil.NANOSECONDS_PER_SECOND;
    private static final long PREPARE_LEVELS_DEFAULT_DELAY_NANOS = 10L * TimeUtil.NANOSECONDS_PER_MILLISECOND;
    private static final int MAX_STATUS_PLAYER_SAMPLE = 12;
    private static final int SPAWN_POSITION_SEARCH_RADIUS = 5;
    private static final int AUTOSAVE_INTERVAL = 6000;
    private static final int MIMINUM_AUTOSAVE_TICKS = 100;
    private static final int MAX_TICK_LATENCY = 3;
    public static final int ABSOLUTE_MAX_WORLD_SIZE = 29999984;
    public static final LevelSettings DEMO_SETTINGS = new LevelSettings(
        "Demo World", GameType.SURVIVAL, false, Difficulty.NORMAL, false, new GameRules(), WorldDataConfiguration.DEFAULT
    );
    public static final GameProfile ANONYMOUS_PLAYER_PROFILE = new GameProfile(Util.NIL_UUID, "Anonymous Player");
    protected final LevelStorageSource.LevelStorageAccess storageSource;
    protected final PlayerDataStorage playerDataStorage;
    private final List<Runnable> tickables = Lists.newArrayList();
    private MetricsRecorder metricsRecorder = InactiveMetricsRecorder.INSTANCE;
    private ProfilerFiller profiler = this.metricsRecorder.getProfiler();
    private Consumer<ProfileResults> onMetricsRecordingStopped = results -> this.stopRecordingMetrics();
    private Consumer<Path> onMetricsRecordingFinished = path -> {};
    private boolean willStartRecordingMetrics;
    @Nullable
    private MinecraftServer.TimeProfiler debugCommandProfiler;
    private boolean debugCommandProfilerDelayStart;
    private final ServerConnectionListener connection;
    private final ChunkProgressListenerFactory progressListenerFactory;
    @Nullable
    private ServerStatus status;
    @Nullable
    private ServerStatus.Favicon statusIcon;
    private final RandomSource random = RandomSource.create();
    private final DataFixer fixerUpper;
    private String localIp;
    private int port = -1;
    private final LayeredRegistryAccess<RegistryLayer> registries;
    private final Map<ResourceKey<Level>, ServerLevel> levels = Maps.newLinkedHashMap();
    private PlayerList playerList;
    private volatile boolean running = true;
    private boolean stopped;
    private int tickCount;
    private int ticksUntilAutosave = 6000;
    protected final Proxy proxy;
    private boolean onlineMode;
    private boolean preventProxyConnections;
    private boolean pvp;
    private boolean allowFlight;
    @Nullable
    private String motd;
    private int playerIdleTimeout;
    private final long[] tickTimesNanos = new long[100];
    private long aggregatedTickTimesNanos = 0L;
    @Nullable
    private KeyPair keyPair;
    @Nullable
    private GameProfile singleplayerProfile;
    private boolean isDemo;
    private volatile boolean isReady;
    private long lastOverloadWarningNanos;
    protected final Services services;
    private long lastServerStatus;
    private final Thread serverThread;
    private long lastTickNanos = Util.getNanos();
    private long taskExecutionStartNanos = Util.getNanos();
    private long idleTimeNanos;
    private long nextTickTimeNanos = Util.getNanos();
    private long delayedTasksMaxNextTickTimeNanos;
    private boolean mayHaveDelayedTasks;
    private final PackRepository packRepository;
    private final ServerScoreboard scoreboard = new ServerScoreboard(this);
    @Nullable
    private CommandStorage commandStorage;
    private final CustomBossEvents customBossEvents = new CustomBossEvents();
    private final ServerFunctionManager functionManager;
    private boolean enforceWhitelist;
    private float smoothedTickTimeMillis;
    private final Executor executor;
    @Nullable
    private String serverId;
    private MinecraftServer.ReloadableResources resources;
    private final StructureTemplateManager structureTemplateManager;
    private final ServerTickRateManager tickRateManager;
    protected final WorldData worldData;
    private final PotionBrewing potionBrewing;
    private volatile boolean isSaving;
    private static final AtomicReference<RuntimeException> fatalException = new AtomicReference<>();

    public static <S extends MinecraftServer> S spin(Function<Thread, S> threadFunction) {
        AtomicReference<S> atomicReference = new AtomicReference<>();
        Thread thread = new Thread(() -> atomicReference.get().runServer(), "Server thread");
        thread.setUncaughtExceptionHandler((threadx, exception) -> LOGGER.error("Uncaught exception in server thread", exception));
        if (Runtime.getRuntime().availableProcessors() > 4) {
            thread.setPriority(8);
        }

        S minecraftServer = (S)threadFunction.apply(thread);
        atomicReference.set(minecraftServer);
        thread.start();
        return minecraftServer;
    }

    public MinecraftServer(
        Thread serverThread,
        LevelStorageSource.LevelStorageAccess storageSource,
        PackRepository packRepository,
        WorldStem worldStem,
        Proxy proxy,
        DataFixer fixerUpper,
        Services services,
        ChunkProgressListenerFactory progressListenerFactory
    ) {
        super("Server");
        this.registries = worldStem.registries();
        this.worldData = worldStem.worldData();
        if (!this.registries.compositeAccess().registryOrThrow(Registries.LEVEL_STEM).containsKey(LevelStem.OVERWORLD)) {
            throw new IllegalStateException("Missing Overworld dimension data");
        }

        this.proxy = proxy;
        this.packRepository = packRepository;
        this.resources = new MinecraftServer.ReloadableResources(worldStem.resourceManager(), worldStem.dataPackResources());
        this.services = services;
        if (services.profileCache() != null) {
            services.profileCache().setExecutor(this);
        }

        this.connection = new ServerConnectionListener(this);
        this.tickRateManager = new ServerTickRateManager(this);
        this.progressListenerFactory = progressListenerFactory;
        this.storageSource = storageSource;
        this.playerDataStorage = storageSource.createPlayerStorage();
        this.fixerUpper = fixerUpper;
        this.functionManager = new ServerFunctionManager(this, this.resources.managers.getFunctionLibrary());
        HolderGetter<Block> holderGetter = this.registries
            .compositeAccess()
            .registryOrThrow(Registries.BLOCK)
            .asLookup()
            .filterFeatures(this.worldData.enabledFeatures());
        this.structureTemplateManager = new StructureTemplateManager(worldStem.resourceManager(), storageSource, fixerUpper, holderGetter);
        this.serverThread = serverThread;
        this.executor = Util.backgroundExecutor();
        this.potionBrewing = PotionBrewing.bootstrap(this.worldData.enabledFeatures());
    }

    private void readScoreboard(DimensionDataStorage dataStorage) {
        dataStorage.computeIfAbsent(this.getScoreboard().dataFactory(), "scoreboard");
    }

    protected abstract boolean initServer() throws IOException;

    protected void loadLevel() {
        if (!JvmProfiler.INSTANCE.isRunning()) {
        }

        boolean bl = false;
        ProfiledDuration profiledDuration = JvmProfiler.INSTANCE.onWorldLoadedStarted();
        this.worldData.setModdedInfo(this.getServerModName(), this.getModdedStatus().shouldReportAsModified());
        ChunkProgressListener chunkProgressListener = this.progressListenerFactory
            .create(this.worldData.getGameRules().getInt(GameRules.RULE_SPAWN_CHUNK_RADIUS));
        this.createLevels(chunkProgressListener);
        this.forceDifficulty();
        this.prepareLevels(chunkProgressListener);
        if (profiledDuration != null) {
            profiledDuration.finish();
        }

        if (bl) {
            try {
                JvmProfiler.INSTANCE.stop();
            } catch (Throwable throwable) {
                LOGGER.warn("Failed to stop JFR profiling", throwable);
            }
        }
    }

    protected void forceDifficulty() {
    }

    protected void createLevels(ChunkProgressListener listener) {
        ServerLevelData serverLevelData = this.worldData.overworldData();
        boolean bl = this.worldData.isDebugWorld();
        Registry<LevelStem> registry = this.registries.compositeAccess().registryOrThrow(Registries.LEVEL_STEM);
        WorldOptions worldOptions = this.worldData.worldGenOptions();
        long l = worldOptions.seed();
        long m = BiomeManager.obfuscateSeed(l);
        List<CustomSpawner> list = ImmutableList.of(
            new PhantomSpawner(), new PatrolSpawner(), new CatSpawner(), new VillageSiege(), new WanderingTraderSpawner(serverLevelData)
        );
        LevelStem levelStem = registry.get(LevelStem.OVERWORLD);
        ServerLevel serverLevel = new ServerLevel(
            this, this.executor, this.storageSource, serverLevelData, Level.OVERWORLD, levelStem, listener, bl, m, list, true, null
        );
        this.levels.put(Level.OVERWORLD, serverLevel);
        DimensionDataStorage dimensionDataStorage = serverLevel.getDataStorage();
        this.readScoreboard(dimensionDataStorage);
        this.commandStorage = new CommandStorage(dimensionDataStorage);
        WorldBorder worldBorder = serverLevel.getWorldBorder();
        if (!serverLevelData.isInitialized()) {
            try {
                setInitialSpawn(serverLevel, serverLevelData, worldOptions.generateBonusChest(), bl);
                serverLevelData.setInitialized(true);
                if (bl) {
                    this.setupDebugLevel(this.worldData);
                }
            } catch (Throwable throwable) {
                CrashReport crashReport = CrashReport.forThrowable(throwable, "Exception initializing level");

                try {
                    serverLevel.fillReportDetails(crashReport);
                } catch (Throwable var22) {
                }

                throw new ReportedException(crashReport);
            }

            serverLevelData.setInitialized(true);
        }

        this.getPlayerList().addWorldborderListener(serverLevel);
        if (this.worldData.getCustomBossEvents() != null) {
            this.getCustomBossEvents().load(this.worldData.getCustomBossEvents(), this.registryAccess());
        }

        RandomSequences randomSequences = serverLevel.getRandomSequences();

        for (Entry<ResourceKey<LevelStem>, LevelStem> entry : registry.entrySet()) {
            ResourceKey<LevelStem> resourceKey = entry.getKey();
            if (resourceKey != LevelStem.OVERWORLD) {
                ResourceKey<Level> resourceKey2 = ResourceKey.create(Registries.DIMENSION, resourceKey.location());
                DerivedLevelData derivedLevelData = new DerivedLevelData(this.worldData, serverLevelData);
                ServerLevel serverLevel2 = new ServerLevel(
                    this,
                    this.executor,
                    this.storageSource,
                    derivedLevelData,
                    resourceKey2,
                    entry.getValue(),
                    listener,
                    bl,
                    m,
                    ImmutableList.of(),
                    false,
                    randomSequences
                );
                worldBorder.addListener(new BorderChangeListener.DelegateBorderChangeListener(serverLevel2.getWorldBorder()));
                this.levels.put(resourceKey2, serverLevel2);
            }
        }

        worldBorder.applySettings(serverLevelData.getWorldBorder());
    }

    private static void setInitialSpawn(ServerLevel level, ServerLevelData levelData, boolean generateBonusChest, boolean debug) {
        if (debug) {
            levelData.setSpawn(BlockPos.ZERO.above(80), 0.0F);
        } else {
            ServerChunkCache serverChunkCache = level.getChunkSource();
            ChunkPos chunkPos = new ChunkPos(serverChunkCache.randomState().sampler().findSpawnPosition());
            int i = serverChunkCache.getGenerator().getSpawnHeight(level);
            if (i < level.getMinBuildHeight()) {
                BlockPos blockPos = chunkPos.getWorldPosition();
                i = level.getHeight(Heightmap.Types.WORLD_SURFACE, blockPos.getX() + 8, blockPos.getZ() + 8);
            }

            levelData.setSpawn(chunkPos.getWorldPosition().offset(8, i, 8), 0.0F);
            int j = 0;
            int k = 0;
            int l = 0;
            int m = -1;

            for (int n = 0; n < Mth.square(11); n++) {
                if (j >= -5 && j <= 5 && k >= -5 && k <= 5) {
                    BlockPos blockPos2 = PlayerRespawnLogic.getSpawnPosInChunk(level, new ChunkPos(chunkPos.x + j, chunkPos.z + k));
                    if (blockPos2 != null) {
                        levelData.setSpawn(blockPos2, 0.0F);
                        break;
                    }
                }

                if (j == k || j < 0 && j == -k || j > 0 && j == 1 - k) {
                    int o = l;
                    l = -m;
                    m = o;
                }

                j += l;
                k += m;
            }

            if (generateBonusChest) {
                level.registryAccess()
                    .registry(Registries.CONFIGURED_FEATURE)
                    .flatMap(registry -> registry.getHolder(MiscOverworldFeatures.BONUS_CHEST))
                    .ifPresent(holder -> holder.value().place(level, serverChunkCache.getGenerator(), level.random, levelData.getSpawnPos()));
            }
        }
    }

    private void setupDebugLevel(WorldData worldData) {
        worldData.setDifficulty(Difficulty.PEACEFUL);
        worldData.setDifficultyLocked(true);
        ServerLevelData serverLevelData = worldData.overworldData();
        serverLevelData.setRaining(false);
        serverLevelData.setThundering(false);
        serverLevelData.setClearWeatherTime(1000000000);
        serverLevelData.setDayTime(6000L);
        serverLevelData.setGameType(GameType.SPECTATOR);
    }

    /**
     * Loads the spawn chunks and any forced chunks
     */
    private void prepareLevels(ChunkProgressListener listener) {
        ServerLevel serverLevel = this.overworld();
        LOGGER.info("Preparing start region for dimension {}", serverLevel.dimension().location());
        BlockPos blockPos = serverLevel.getSharedSpawnPos();
        listener.updateSpawnPos(new ChunkPos(blockPos));
        ServerChunkCache serverChunkCache = serverLevel.getChunkSource();
        this.nextTickTimeNanos = Util.getNanos();
        serverLevel.setDefaultSpawnPos(blockPos, serverLevel.getSharedSpawnAngle());
        int i = this.getGameRules().getInt(GameRules.RULE_SPAWN_CHUNK_RADIUS);
        int j = i > 0 ? Mth.square(ChunkProgressListener.calculateDiameter(i)) : 0;

        while (serverChunkCache.getTickingGenerated() < j) {
            this.nextTickTimeNanos = Util.getNanos() + PREPARE_LEVELS_DEFAULT_DELAY_NANOS;
            this.waitUntilNextTick();
        }

        this.nextTickTimeNanos = Util.getNanos() + PREPARE_LEVELS_DEFAULT_DELAY_NANOS;
        this.waitUntilNextTick();

        for (ServerLevel serverLevel2 : this.levels.values()) {
            ForcedChunksSavedData forcedChunksSavedData = serverLevel2.getDataStorage().get(ForcedChunksSavedData.factory(), "chunks");
            if (forcedChunksSavedData != null) {
                LongIterator longIterator = forcedChunksSavedData.getChunks().iterator();

                while (longIterator.hasNext()) {
                    long l = longIterator.nextLong();
                    ChunkPos chunkPos = new ChunkPos(l);
                    serverLevel2.getChunkSource().updateChunkForced(chunkPos, true);
                }
            }
        }

        this.nextTickTimeNanos = Util.getNanos() + PREPARE_LEVELS_DEFAULT_DELAY_NANOS;
        this.waitUntilNextTick();
        listener.stop();
        this.updateMobSpawningFlags();
    }

    public GameType getDefaultGameType() {
        return this.worldData.getGameType();
    }

    public boolean isHardcore() {
        return this.worldData.isHardcore();
    }

    public abstract int getOperatorUserPermissionLevel();

    public abstract int getFunctionCompilationLevel();

    public abstract boolean shouldRconBroadcast();

    public boolean saveAllChunks(boolean suppressLog, boolean flush, boolean forced) {
        boolean bl = false;

        for (ServerLevel serverLevel : this.getAllLevels()) {
            if (!suppressLog) {
                LOGGER.info("Saving chunks for level '{}'/{}", serverLevel, serverLevel.dimension().location());
            }

            serverLevel.save(null, flush, serverLevel.noSave && !forced);
            bl = true;
        }

        ServerLevel serverLevel2 = this.overworld();
        ServerLevelData serverLevelData = this.worldData.overworldData();
        serverLevelData.setWorldBorder(serverLevel2.getWorldBorder().createSettings());
        this.worldData.setCustomBossEvents(this.getCustomBossEvents().save(this.registryAccess()));
        this.storageSource.saveDataTag(this.registryAccess(), this.worldData, this.getPlayerList().getSingleplayerData());
        if (flush) {
            for (ServerLevel serverLevel3 : this.getAllLevels()) {
                LOGGER.info("ThreadedAnvilChunkStorage ({}): All chunks are saved", serverLevel3.getChunkSource().chunkMap.getStorageName());
            }

            LOGGER.info("ThreadedAnvilChunkStorage: All dimensions are saved");
        }

        return bl;
    }

    public boolean saveEverything(boolean suppressLog, boolean flush, boolean forced) {
        try {
            this.isSaving = true;
            this.getPlayerList().saveAll();
            return this.saveAllChunks(suppressLog, flush, forced);
        } finally {
            this.isSaving = false;
        }
    }

    @Override
    public void close() {
        this.stopServer();
    }

    public void stopServer() {
        if (this.metricsRecorder.isRecording()) {
            this.cancelRecordingMetrics();
        }

        LOGGER.info("Stopping server");
        this.getConnection().stop();
        this.isSaving = true;
        if (this.playerList != null) {
            LOGGER.info("Saving players");
            this.playerList.saveAll();
            this.playerList.removeAll();
        }

        LOGGER.info("Saving worlds");

        for (ServerLevel serverLevel : this.getAllLevels()) {
            if (serverLevel != null) {
                serverLevel.noSave = false;
            }
        }

        while (this.levels.values().stream().anyMatch(level -> level.getChunkSource().chunkMap.hasWork())) {
            this.nextTickTimeNanos = Util.getNanos() + TimeUtil.NANOSECONDS_PER_MILLISECOND;

            for (ServerLevel serverLevel : this.getAllLevels()) {
                serverLevel.getChunkSource().removeTicketsOnClosing();
                serverLevel.getChunkSource().tick(() -> true, false);
            }

            this.waitUntilNextTick();
        }

        this.saveAllChunks(false, true, false);

        for (ServerLevel serverLevel : this.getAllLevels()) {
            if (serverLevel != null) {
                try {
                    serverLevel.close();
                } catch (IOException iOException) {
                    LOGGER.error("Exception closing the level", iOException);
                }
            }
        }

        this.isSaving = false;
        this.resources.close();

        try {
            this.storageSource.close();
        } catch (IOException iOException2) {
            LOGGER.error("Failed to unlock level {}", this.storageSource.getLevelId(), iOException2);
        }
    }

    public String getLocalIp() {
        return this.localIp;
    }

    public void setLocalIp(String localIp) {
        this.localIp = localIp;
    }

    public boolean isRunning() {
        return this.running;
    }

    /**
     * Sets the serverRunning variable to false, in order to get the server to shut down.
     */
    public void halt(boolean waitForServer) {
        this.running = false;
        if (waitForServer) {
            try {
                this.serverThread.join();
            } catch (InterruptedException interruptedException) {
                LOGGER.error("Error while shutting down", interruptedException);
            }
        }
    }

    protected void runServer() {
        try {
            if (!this.initServer()) {
                throw new IllegalStateException("Failed to initialize server");
            }

            this.nextTickTimeNanos = Util.getNanos();
            this.statusIcon = this.loadStatusIcon().orElse(null);
            this.status = this.buildServerStatus();

            while (this.running) {
                long l;
                if (!this.isPaused() && this.tickRateManager.isSprinting() && this.tickRateManager.checkShouldSprintThisTick()) {
                    l = 0L;
                    this.nextTickTimeNanos = Util.getNanos();
                    this.lastOverloadWarningNanos = this.nextTickTimeNanos;
                } else {
                    l = this.tickRateManager.nanosecondsPerTick();
                    long m = Util.getNanos() - this.nextTickTimeNanos;
                    if (m > OVERLOADED_THRESHOLD_NANOS + 20L * l
                        && this.nextTickTimeNanos - this.lastOverloadWarningNanos >= OVERLOADED_WARNING_INTERVAL_NANOS + 100L * l) {
                        long n = m / l;
                        LOGGER.warn("Can't keep up! Is the server overloaded? Running {}ms or {} ticks behind", m / TimeUtil.NANOSECONDS_PER_MILLISECOND, n);
                        this.nextTickTimeNanos += n * l;
                        this.lastOverloadWarningNanos = this.nextTickTimeNanos;
                    }
                }

                boolean bl = l == 0L;
                if (this.debugCommandProfilerDelayStart) {
                    this.debugCommandProfilerDelayStart = false;
                    this.debugCommandProfiler = new MinecraftServer.TimeProfiler(Util.getNanos(), this.tickCount);
                }

                this.nextTickTimeNanos += l;
                this.startMetricsRecordingTick();
                this.profiler.push("tick");
                this.tickServer(bl ? () -> false : this::haveTime);
                this.profiler.popPush("nextTickWait");
                this.mayHaveDelayedTasks = true;
                this.delayedTasksMaxNextTickTimeNanos = Math.max(Util.getNanos() + l, this.nextTickTimeNanos);
                this.startMeasuringTaskExecutionTime();
                this.waitUntilNextTick();
                this.finishMeasuringTaskExecutionTime();
                if (bl) {
                    this.tickRateManager.endTickWork();
                }

                this.profiler.pop();
                this.logFullTickTime();
                this.endMetricsRecordingTick();
                this.isReady = true;
                JvmProfiler.INSTANCE.onServerTick(this.smoothedTickTimeMillis);
            }
        } catch (Throwable throwable) {
            LOGGER.error("Encountered an unexpected exception", throwable);
            CrashReport crashReport = constructOrExtractCrashReport(throwable);
            this.fillSystemReport(crashReport.getSystemReport());
            Path path = this.getServerDirectory().resolve("crash-reports").resolve("crash-" + Util.getFilenameFormattedDateTime() + "-server.txt");
            if (crashReport.saveToFile(path, ReportType.CRASH)) {
                LOGGER.error("This crash report has been saved to: {}", path.toAbsolutePath());
            } else {
                LOGGER.error("We were unable to save this crash report to disk.");
            }

            this.onServerCrash(crashReport);
        } finally {
            try {
                this.stopped = true;
                this.stopServer();
            } catch (Throwable throwable2) {
                LOGGER.error("Exception stopping the server", throwable2);
            } finally {
                if (this.services.profileCache() != null) {
                    this.services.profileCache().clearExecutor();
                }

                this.onServerExit();
            }
        }
    }

    private void logFullTickTime() {
        long l = Util.getNanos();
        if (this.isTickTimeLoggingEnabled()) {
            this.getTickTimeLogger().logSample(l - this.lastTickNanos);
        }

        this.lastTickNanos = l;
    }

    private void startMeasuringTaskExecutionTime() {
        if (this.isTickTimeLoggingEnabled()) {
            this.taskExecutionStartNanos = Util.getNanos();
            this.idleTimeNanos = 0L;
        }
    }

    private void finishMeasuringTaskExecutionTime() {
        if (this.isTickTimeLoggingEnabled()) {
            SampleLogger sampleLogger = this.getTickTimeLogger();
            sampleLogger.logPartialSample(Util.getNanos() - this.taskExecutionStartNanos - this.idleTimeNanos, TpsDebugDimensions.SCHEDULED_TASKS.ordinal());
            sampleLogger.logPartialSample(this.idleTimeNanos, TpsDebugDimensions.IDLE.ordinal());
        }
    }

    private static CrashReport constructOrExtractCrashReport(Throwable cause) {
        ReportedException reportedException = null;

        for (Throwable throwable = cause; throwable != null; throwable = throwable.getCause()) {
            if (throwable instanceof ReportedException reportedException2) {
                reportedException = reportedException2;
            }
        }

        CrashReport crashReport;
        if (reportedException != null) {
            crashReport = reportedException.getReport();
            if (reportedException != cause) {
                crashReport.addCategory("Wrapped in").setDetailError("Wrapping exception", cause);
            }
        } else {
            crashReport = new CrashReport("Exception in server tick loop", cause);
        }

        return crashReport;
    }

    private boolean haveTime() {
        return this.runningTask() || Util.getNanos() < (this.mayHaveDelayedTasks ? this.delayedTasksMaxNextTickTimeNanos : this.nextTickTimeNanos);
    }

    public static boolean throwIfFatalException() {
        RuntimeException runtimeException = fatalException.get();
        if (runtimeException != null) {
            throw runtimeException;
        } else {
            return true;
        }
    }

    public static void setFatalException(RuntimeException fatalException) {
        MinecraftServer.fatalException.compareAndSet(null, fatalException);
    }

    /**
     * Drive the executor until the given BooleanSupplier returns true
     */
    @Override
    public void managedBlock(BooleanSupplier isDone) {
        super.managedBlock(() -> throwIfFatalException() && isDone.getAsBoolean());
    }

    protected void waitUntilNextTick() {
        this.runAllTasks();
        this.managedBlock(() -> !this.haveTime());
    }

    @Override
    public void waitForTasks() {
        boolean bl = this.isTickTimeLoggingEnabled();
        long l = bl ? Util.getNanos() : 0L;
        super.waitForTasks();
        if (bl) {
            this.idleTimeNanos = this.idleTimeNanos + (Util.getNanos() - l);
        }
    }

    protected TickTask wrapRunnable(Runnable runnable) {
        return new TickTask(this.tickCount, runnable);
    }

    protected boolean shouldRun(TickTask runnable) {
        return runnable.getTick() + 3 < this.tickCount || this.haveTime();
    }

    @Override
    public boolean pollTask() {
        boolean bl = this.pollTaskInternal();
        this.mayHaveDelayedTasks = bl;
        return bl;
    }

    private boolean pollTaskInternal() {
        if (super.pollTask()) {
            return true;
        }

        if (this.tickRateManager.isSprinting() || this.haveTime()) {
            for (ServerLevel serverLevel : this.getAllLevels()) {
                if (serverLevel.getChunkSource().pollTask()) {
                    return true;
                }
            }
        }

        return false;
    }

    protected void doRunTask(TickTask task) {
        this.getProfiler().incrementCounter("runTask");
        super.doRunTask(task);
    }

    private Optional<ServerStatus.Favicon> loadStatusIcon() {
        Optional<Path> optional = Optional.of(this.getFile("server-icon.png"))
            .filter(path -> Files.isRegularFile(path))
            .or(() -> this.storageSource.getIconFile().filter(path -> Files.isRegularFile(path)));
        return optional.flatMap(path -> {
            try {
                BufferedImage bufferedImage = ImageIO.read(path.toFile());
                Preconditions.checkState(bufferedImage.getWidth() == 64, "Must be 64 pixels wide");
                Preconditions.checkState(bufferedImage.getHeight() == 64, "Must be 64 pixels high");
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                ImageIO.write(bufferedImage, "PNG", byteArrayOutputStream);
                return Optional.of(new ServerStatus.Favicon(byteArrayOutputStream.toByteArray()));
            } catch (Exception exception) {
                LOGGER.error("Couldn't load server icon", exception);
                return Optional.empty();
            }
        });
    }

    public Optional<Path> getWorldScreenshotFile() {
        return this.storageSource.getIconFile();
    }

    public Path getServerDirectory() {
        return Path.of("");
    }

    /**
     * Called on exit from the main run() loop.
     */
    public void onServerCrash(CrashReport report) {
    }

    public void onServerExit() {
    }

    public boolean isPaused() {
        return false;
    }

    /**
     * Main function called by run() every loop.
     */
    public void tickServer(BooleanSupplier hasTimeLeft) {
        long l = Util.getNanos();
        this.tickCount++;
        this.tickRateManager.tick();
        this.tickChildren(hasTimeLeft);
        if (l - this.lastServerStatus >= STATUS_EXPIRE_TIME_NANOS) {
            this.lastServerStatus = l;
            this.status = this.buildServerStatus();
        }

        this.ticksUntilAutosave--;
        if (this.ticksUntilAutosave <= 0) {
            this.ticksUntilAutosave = this.computeNextAutosaveInterval();
            LOGGER.debug("Autosave started");
            this.profiler.push("save");
            this.saveEverything(true, false, false);
            this.profiler.pop();
            LOGGER.debug("Autosave finished");
        }

        this.profiler.push("tallying");
        long m = Util.getNanos() - l;
        int i = this.tickCount % 100;
        this.aggregatedTickTimesNanos = this.aggregatedTickTimesNanos - this.tickTimesNanos[i];
        this.aggregatedTickTimesNanos += m;
        this.tickTimesNanos[i] = m;
        this.smoothedTickTimeMillis = this.smoothedTickTimeMillis * 0.8F + (float)m / (float)TimeUtil.NANOSECONDS_PER_MILLISECOND * 0.19999999F;
        this.logTickMethodTime(l);
        this.profiler.pop();
    }

    private void logTickMethodTime(long startTime) {
        if (this.isTickTimeLoggingEnabled()) {
            this.getTickTimeLogger().logPartialSample(Util.getNanos() - startTime, TpsDebugDimensions.TICK_SERVER_METHOD.ordinal());
        }
    }

    private int computeNextAutosaveInterval() {
        float f;
        if (this.tickRateManager.isSprinting()) {
            long l = this.getAverageTickTimeNanos() + 1L;
            f = (float)TimeUtil.NANOSECONDS_PER_SECOND / (float)l;
        } else {
            f = this.tickRateManager.tickrate();
        }

        int i = 300;
        return Math.max(100, (int)(f * 300.0F));
    }

    public void onTickRateChanged() {
        int i = this.computeNextAutosaveInterval();
        if (i < this.ticksUntilAutosave) {
            this.ticksUntilAutosave = i;
        }
    }

    protected abstract SampleLogger getTickTimeLogger();

    public abstract boolean isTickTimeLoggingEnabled();

    private ServerStatus buildServerStatus() {
        ServerStatus.Players players = this.buildPlayerStatus();
        return new ServerStatus(
            Component.nullToEmpty(this.motd),
            Optional.of(players),
            Optional.of(ServerStatus.Version.current()),
            Optional.ofNullable(this.statusIcon),
            this.enforceSecureProfile()
        );
    }

    private ServerStatus.Players buildPlayerStatus() {
        List<ServerPlayer> list = this.playerList.getPlayers();
        int i = this.getMaxPlayers();
        if (this.hidesOnlinePlayers()) {
            return new ServerStatus.Players(i, list.size(), List.of());
        }

        int j = Math.min(list.size(), 12);
        ObjectArrayList<GameProfile> objectArrayList = new ObjectArrayList<>(j);
        int k = Mth.nextInt(this.random, 0, list.size() - j);

        for (int l = 0; l < j; l++) {
            ServerPlayer serverPlayer = list.get(k + l);
            objectArrayList.add(serverPlayer.allowsListing() ? serverPlayer.getGameProfile() : ANONYMOUS_PLAYER_PROFILE);
        }

        Util.shuffle(objectArrayList, this.random);
        return new ServerStatus.Players(i, list.size(), objectArrayList);
    }

    public void tickChildren(BooleanSupplier hasTimeLeft) {
        this.getPlayerList().getPlayers().forEach(serverPlayerx -> serverPlayerx.connection.suspendFlushing());
        this.profiler.push("commandFunctions");
        this.getFunctions().tick();
        this.profiler.popPush("levels");

        for (ServerLevel serverLevel : this.getAllLevels()) {
            this.profiler.push(() -> serverLevel + " " + serverLevel.dimension().location());
            if (this.tickCount % 20 == 0) {
                this.profiler.push("timeSync");
                this.synchronizeTime(serverLevel);
                this.profiler.pop();
            }

            this.profiler.push("tick");

            try {
                serverLevel.tick(hasTimeLeft);
            } catch (Throwable throwable) {
                CrashReport crashReport = CrashReport.forThrowable(throwable, "Exception ticking world");
                serverLevel.fillReportDetails(crashReport);
                throw new ReportedException(crashReport);
            }

            this.profiler.pop();
            this.profiler.pop();
        }

        this.profiler.popPush("connection");
        this.getConnection().tick();
        this.profiler.popPush("players");
        this.playerList.tick();
        if (SharedConstants.IS_RUNNING_IN_IDE && this.tickRateManager.runsNormally()) {
            GameTestTicker.SINGLETON.tick();
        }

        this.profiler.popPush("server gui refresh");

        for (int i = 0; i < this.tickables.size(); i++) {
            this.tickables.get(i).run();
        }

        this.profiler.popPush("send chunks");

        for (ServerPlayer serverPlayer : this.playerList.getPlayers()) {
            serverPlayer.connection.chunkSender.sendNextChunks(serverPlayer);
            serverPlayer.connection.resumeFlushing();
        }

        this.profiler.pop();
    }

    private void synchronizeTime(ServerLevel level) {
        this.playerList
            .broadcastAll(
                new ClientboundSetTimePacket(level.getGameTime(), level.getDayTime(), level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)),
                level.dimension()
            );
    }

    public void forceTimeSynchronization() {
        this.profiler.push("timeSync");

        for (ServerLevel serverLevel : this.getAllLevels()) {
            this.synchronizeTime(serverLevel);
        }

        this.profiler.pop();
    }

    public boolean isLevelEnabled(Level level) {
        return true;
    }

    public void addTickable(Runnable tickable) {
        this.tickables.add(tickable);
    }

    protected void setId(String serverId) {
        this.serverId = serverId;
    }

    public boolean isShutdown() {
        return !this.serverThread.isAlive();
    }

    public Path getFile(String path) {
        return this.getServerDirectory().resolve(path);
    }

    public final ServerLevel overworld() {
        return this.levels.get(Level.OVERWORLD);
    }

    /**
     * Gets the worldServer by the given dimension.
     */
    @Nullable
    public ServerLevel getLevel(ResourceKey<Level> dimension) {
        return this.levels.get(dimension);
    }

    public Set<ResourceKey<Level>> levelKeys() {
        return this.levels.keySet();
    }

    public Iterable<ServerLevel> getAllLevels() {
        return this.levels.values();
    }

    @Override
    public String getServerVersion() {
        return SharedConstants.getCurrentVersion().getName();
    }

    @Override
    public int getPlayerCount() {
        return this.playerList.getPlayerCount();
    }

    @Override
    public int getMaxPlayers() {
        return this.playerList.getMaxPlayers();
    }

    public String[] getPlayerNames() {
        return this.playerList.getPlayerNamesArray();
    }

    @DontObfuscate
    public String getServerModName() {
        return "vanilla";
    }

    public SystemReport fillSystemReport(SystemReport systemReport) {
        systemReport.setDetail("Server Running", () -> Boolean.toString(this.running));
        if (this.playerList != null) {
            systemReport.setDetail(
                "Player Count", () -> this.playerList.getPlayerCount() + " / " + this.playerList.getMaxPlayers() + "; " + this.playerList.getPlayers()
            );
        }

        systemReport.setDetail("Active Data Packs", () -> PackRepository.displayPackList(this.packRepository.getSelectedPacks()));
        systemReport.setDetail("Available Data Packs", () -> PackRepository.displayPackList(this.packRepository.getAvailablePacks()));
        systemReport.setDetail(
            "Enabled Feature Flags",
            () -> FeatureFlags.REGISTRY.toNames(this.worldData.enabledFeatures()).stream().map(ResourceLocation::toString).collect(Collectors.joining(", "))
        );
        systemReport.setDetail("World Generation", () -> this.worldData.worldGenSettingsLifecycle().toString());
        systemReport.setDetail("World Seed", () -> String.valueOf(this.worldData.worldGenOptions().seed()));
        if (this.serverId != null) {
            systemReport.setDetail("Server Id", () -> this.serverId);
        }

        return this.fillServerSystemReport(systemReport);
    }

    public abstract SystemReport fillServerSystemReport(SystemReport var1);

    public ModCheck getModdedStatus() {
        return ModCheck.identify("vanilla", this::getServerModName, "Server", MinecraftServer.class);
    }

    @Override
    public void sendSystemMessage(Component component) {
        LOGGER.info(component.getString());
    }

    public KeyPair getKeyPair() {
        return this.keyPair;
    }

    public int getPort() {
        return this.port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    @Nullable
    public GameProfile getSingleplayerProfile() {
        return this.singleplayerProfile;
    }

    public void setSingleplayerProfile(@Nullable GameProfile singleplayerProfile) {
        this.singleplayerProfile = singleplayerProfile;
    }

    public boolean isSingleplayer() {
        return this.singleplayerProfile != null;
    }

    protected void initializeKeyPair() {
        LOGGER.info("Generating keypair");

        try {
            this.keyPair = Crypt.generateKeyPair();
        } catch (CryptException cryptException) {
            throw new IllegalStateException("Failed to generate key pair", cryptException);
        }
    }

    public void setDifficulty(Difficulty difficulty, boolean forced) {
        if (forced || !this.worldData.isDifficultyLocked()) {
            this.worldData.setDifficulty(this.worldData.isHardcore() ? Difficulty.HARD : difficulty);
            this.updateMobSpawningFlags();
            this.getPlayerList().getPlayers().forEach(this::sendDifficultyUpdate);
        }
    }

    public int getScaledTrackingDistance(int trackingDistance) {
        return trackingDistance;
    }

    private void updateMobSpawningFlags() {
        for (ServerLevel serverLevel : this.getAllLevels()) {
            serverLevel.setSpawnSettings(this.isSpawningMonsters(), this.isSpawningAnimals());
        }
    }

    public void setDifficultyLocked(boolean locked) {
        this.worldData.setDifficultyLocked(locked);
        this.getPlayerList().getPlayers().forEach(this::sendDifficultyUpdate);
    }

    private void sendDifficultyUpdate(ServerPlayer player) {
        LevelData levelData = player.level().getLevelData();
        player.connection.send(new ClientboundChangeDifficultyPacket(levelData.getDifficulty(), levelData.isDifficultyLocked()));
    }

    public boolean isSpawningMonsters() {
        return this.worldData.getDifficulty() != Difficulty.PEACEFUL;
    }

    public boolean isDemo() {
        return this.isDemo;
    }

    /**
     * Sets whether this is a demo or not.
     */
    public void setDemo(boolean demo) {
        this.isDemo = demo;
    }

    public Optional<MinecraftServer.ServerResourcePackInfo> getServerResourcePack() {
        return Optional.empty();
    }

    public boolean isResourcePackRequired() {
        return this.getServerResourcePack().filter(MinecraftServer.ServerResourcePackInfo::isRequired).isPresent();
    }

    public abstract boolean isDedicatedServer();

    public abstract int getRateLimitPacketsPerSecond();

    public boolean usesAuthentication() {
        return this.onlineMode;
    }

    public void setUsesAuthentication(boolean online) {
        this.onlineMode = online;
    }

    public boolean getPreventProxyConnections() {
        return this.preventProxyConnections;
    }

    public void setPreventProxyConnections(boolean preventProxyConnections) {
        this.preventProxyConnections = preventProxyConnections;
    }

    public boolean isSpawningAnimals() {
        return true;
    }

    public boolean areNpcsEnabled() {
        return true;
    }

    public abstract boolean isEpollEnabled();

    public boolean isPvpAllowed() {
        return this.pvp;
    }

    public void setPvpAllowed(boolean allowPvp) {
        this.pvp = allowPvp;
    }

    public boolean isFlightAllowed() {
        return this.allowFlight;
    }

    public void setFlightAllowed(boolean allow) {
        this.allowFlight = allow;
    }

    public abstract boolean isCommandBlockEnabled();

    @Override
    public String getMotd() {
        return this.motd;
    }

    public void setMotd(String motd) {
        this.motd = motd;
    }

    public boolean isStopped() {
        return this.stopped;
    }

    public PlayerList getPlayerList() {
        return this.playerList;
    }

    public void setPlayerList(PlayerList list) {
        this.playerList = list;
    }

    public abstract boolean isPublished();

    /**
     * Sets the game type for all worlds.
     */
    public void setDefaultGameType(GameType gameMode) {
        this.worldData.setGameType(gameMode);
    }

    public ServerConnectionListener getConnection() {
        return this.connection;
    }

    public boolean isReady() {
        return this.isReady;
    }

    public boolean hasGui() {
        return false;
    }

    public boolean publishServer(@Nullable GameType gameMode, boolean commands, int port) {
        return false;
    }

    public int getTickCount() {
        return this.tickCount;
    }

    public int getSpawnProtectionRadius() {
        return 16;
    }

    public boolean isUnderSpawnProtection(ServerLevel level, BlockPos pos, Player player) {
        return false;
    }

    public boolean repliesToStatus() {
        return true;
    }

    public boolean hidesOnlinePlayers() {
        return false;
    }

    public Proxy getProxy() {
        return this.proxy;
    }

    public int getPlayerIdleTimeout() {
        return this.playerIdleTimeout;
    }

    public void setPlayerIdleTimeout(int idleTimeout) {
        this.playerIdleTimeout = idleTimeout;
    }

    public MinecraftSessionService getSessionService() {
        return this.services.sessionService();
    }

    @Nullable
    public SignatureValidator getProfileKeySignatureValidator() {
        return this.services.profileKeySignatureValidator();
    }

    public GameProfileRepository getProfileRepository() {
        return this.services.profileRepository();
    }

    @Nullable
    public GameProfileCache getProfileCache() {
        return this.services.profileCache();
    }

    @Nullable
    public ServerStatus getStatus() {
        return this.status;
    }

    public void invalidateStatus() {
        this.lastServerStatus = 0L;
    }

    public int getAbsoluteMaxWorldSize() {
        return 29999984;
    }

    @Override
    public boolean scheduleExecutables() {
        return super.scheduleExecutables() && !this.isStopped();
    }

    @Override
    public void executeIfPossible(Runnable task) {
        if (this.isStopped()) {
            throw new RejectedExecutionException("Server already shutting down");
        }

        super.executeIfPossible(task);
    }

    @Override
    public Thread getRunningThread() {
        return this.serverThread;
    }

    public int getCompressionThreshold() {
        return 256;
    }

    public boolean enforceSecureProfile() {
        return false;
    }

    public long getNextTickTime() {
        return this.nextTickTimeNanos;
    }

    public DataFixer getFixerUpper() {
        return this.fixerUpper;
    }

    public int getSpawnRadius(@Nullable ServerLevel level) {
        return level != null ? level.getGameRules().getInt(GameRules.RULE_SPAWN_RADIUS) : 10;
    }

    public ServerAdvancementManager getAdvancements() {
        return this.resources.managers.getAdvancements();
    }

    public ServerFunctionManager getFunctions() {
        return this.functionManager;
    }

    /**
     * Replaces currently selected list of datapacks, reloads them, and sends new data to players.
     */
    public CompletableFuture<Void> reloadResources(Collection<String> selectedIds) {
        CompletableFuture<Void> completableFuture = CompletableFuture.<ImmutableList>supplyAsync(
                () -> selectedIds.stream().map(this.packRepository::getPack).filter(Objects::nonNull).map(Pack::open).collect(ImmutableList.toImmutableList()),
                this
            )
            .thenCompose(
                immutableList -> {
                    CloseableResourceManager closeableResourceManager = new MultiPackResourceManager(PackType.SERVER_DATA, immutableList);
                    return ReloadableServerResources.loadResources(
                            closeableResourceManager,
                            this.registries,
                            this.worldData.enabledFeatures(),
                            this.isDedicatedServer() ? Commands.CommandSelection.DEDICATED : Commands.CommandSelection.INTEGRATED,
                            this.getFunctionCompilationLevel(),
                            this.executor,
                            this
                        )
                        .whenComplete((reloadableServerResources, throwable) -> {
                            if (throwable != null) {
                                closeableResourceManager.close();
                            }
                        })
                        .thenApply(reloadableServerResources -> new MinecraftServer.ReloadableResources(closeableResourceManager, reloadableServerResources));
                }
            )
            .thenAcceptAsync(
                reloadableResources -> {
                    this.resources.close();
                    this.resources = reloadableResources;
                    this.packRepository.setSelected(selectedIds);
                    WorldDataConfiguration worldDataConfiguration = new WorldDataConfiguration(
                        getSelectedPacks(this.packRepository, true), this.worldData.enabledFeatures()
                    );
                    this.worldData.setDataConfiguration(worldDataConfiguration);
                    this.resources.managers.updateRegistryTags();
                    this.getPlayerList().saveAll();
                    this.getPlayerList().reloadResources();
                    this.functionManager.replaceLibrary(this.resources.managers.getFunctionLibrary());
                    this.structureTemplateManager.onResourceManagerReload(this.resources.resourceManager);
                },
                this
            );
        if (this.isSameThread()) {
            this.managedBlock(completableFuture::isDone);
        }

        return completableFuture;
    }

    public static WorldDataConfiguration configurePackRepository(
        PackRepository packRepository, WorldDataConfiguration initialDataConfig, boolean initMode, boolean safeMode
    ) {
        DataPackConfig dataPackConfig = initialDataConfig.dataPacks();
        FeatureFlagSet featureFlagSet = initMode ? FeatureFlagSet.of() : initialDataConfig.enabledFeatures();
        FeatureFlagSet featureFlagSet2 = initMode ? FeatureFlags.REGISTRY.allFlags() : initialDataConfig.enabledFeatures();
        packRepository.reload();
        if (safeMode) {
            return configureRepositoryWithSelection(packRepository, List.of("vanilla"), featureFlagSet, false);
        }

        Set<String> set = Sets.newLinkedHashSet();

        for (String string : dataPackConfig.getEnabled()) {
            if (packRepository.isAvailable(string)) {
                set.add(string);
            } else {
                LOGGER.warn("Missing data pack {}", string);
            }
        }

        for (Pack pack : packRepository.getAvailablePacks()) {
            String string2 = pack.getId();
            if (!dataPackConfig.getDisabled().contains(string2)) {
                FeatureFlagSet featureFlagSet3 = pack.getRequestedFeatures();
                boolean bl = set.contains(string2);
                if (!bl && pack.getPackSource().shouldAddAutomatically()) {
                    if (featureFlagSet3.isSubsetOf(featureFlagSet2)) {
                        LOGGER.info("Found new data pack {}, loading it automatically", string2);
                        set.add(string2);
                    } else {
                        LOGGER.info(
                            "Found new data pack {}, but can't load it due to missing features {}",
                            string2,
                            FeatureFlags.printMissingFlags(featureFlagSet2, featureFlagSet3)
                        );
                    }
                }

                if (bl && !featureFlagSet3.isSubsetOf(featureFlagSet2)) {
                    LOGGER.warn(
                        "Pack {} requires features {} that are not enabled for this world, disabling pack.",
                        string2,
                        FeatureFlags.printMissingFlags(featureFlagSet2, featureFlagSet3)
                    );
                    set.remove(string2);
                }
            }
        }

        if (set.isEmpty()) {
            LOGGER.info("No datapacks selected, forcing vanilla");
            set.add("vanilla");
        }

        return configureRepositoryWithSelection(packRepository, set, featureFlagSet, true);
    }

    private static WorldDataConfiguration configureRepositoryWithSelection(
        PackRepository packRepository, Collection<String> selectedPacks, FeatureFlagSet enabledFeatures, boolean safeMode
    ) {
        packRepository.setSelected(selectedPacks);
        enableForcedFeaturePacks(packRepository, enabledFeatures);
        DataPackConfig dataPackConfig = getSelectedPacks(packRepository, safeMode);
        FeatureFlagSet featureFlagSet = packRepository.getRequestedFeatureFlags().join(enabledFeatures);
        return new WorldDataConfiguration(dataPackConfig, featureFlagSet);
    }

    private static void enableForcedFeaturePacks(PackRepository packRepository, FeatureFlagSet enabledFeatures) {
        FeatureFlagSet featureFlagSet = packRepository.getRequestedFeatureFlags();
        FeatureFlagSet featureFlagSet2 = enabledFeatures.subtract(featureFlagSet);
        if (!featureFlagSet2.isEmpty()) {
            Set<String> set = new ObjectArraySet<>(packRepository.getSelectedIds());

            for (Pack pack : packRepository.getAvailablePacks()) {
                if (featureFlagSet2.isEmpty()) {
                    break;
                }

                if (pack.getPackSource() == PackSource.FEATURE) {
                    String string = pack.getId();
                    FeatureFlagSet featureFlagSet3 = pack.getRequestedFeatures();
                    if (!featureFlagSet3.isEmpty() && featureFlagSet3.intersects(featureFlagSet2) && featureFlagSet3.isSubsetOf(enabledFeatures)) {
                        if (!set.add(string)) {
                            throw new IllegalStateException("Tried to force '" + string + "', but it was already enabled");
                        }

                        LOGGER.info("Found feature pack ('{}') for requested feature, forcing to enabled", string);
                        featureFlagSet2 = featureFlagSet2.subtract(featureFlagSet3);
                    }
                }
            }

            packRepository.setSelected(set);
        }
    }

    private static DataPackConfig getSelectedPacks(PackRepository packRepository, boolean safeMode) {
        Collection<String> collection = packRepository.getSelectedIds();
        List<String> list = ImmutableList.copyOf(collection);
        List<String> list2 = safeMode ? packRepository.getAvailableIds().stream().filter(packId -> !collection.contains(packId)).toList() : List.of();
        return new DataPackConfig(list, list2);
    }

    public void kickUnlistedPlayers(CommandSourceStack commandSource) {
        if (this.isEnforceWhitelist()) {
            PlayerList playerList = commandSource.getServer().getPlayerList();
            UserWhiteList userWhiteList = playerList.getWhiteList();

            for (ServerPlayer serverPlayer : Lists.newArrayList(playerList.getPlayers())) {
                if (!userWhiteList.isWhiteListed(serverPlayer.getGameProfile())) {
                    serverPlayer.connection.disconnect(Component.translatable("multiplayer.disconnect.not_whitelisted"));
                }
            }
        }
    }

    public PackRepository getPackRepository() {
        return this.packRepository;
    }

    public Commands getCommands() {
        return this.resources.managers.getCommands();
    }

    public CommandSourceStack createCommandSourceStack() {
        ServerLevel serverLevel = this.overworld();
        return new CommandSourceStack(
            this,
            serverLevel == null ? Vec3.ZERO : Vec3.atLowerCornerOf(serverLevel.getSharedSpawnPos()),
            Vec2.ZERO,
            serverLevel,
            4,
            "Server",
            Component.literal("Server"),
            this,
            null
        );
    }

    @Override
    public boolean acceptsSuccess() {
        return true;
    }

    @Override
    public boolean acceptsFailure() {
        return true;
    }

    @Override
    public abstract boolean shouldInformAdmins();

    public RecipeManager getRecipeManager() {
        return this.resources.managers.getRecipeManager();
    }

    public ServerScoreboard getScoreboard() {
        return this.scoreboard;
    }

    public CommandStorage getCommandStorage() {
        if (this.commandStorage == null) {
            throw new NullPointerException("Called before server init");
        } else {
            return this.commandStorage;
        }
    }

    public GameRules getGameRules() {
        return this.overworld().getGameRules();
    }

    public CustomBossEvents getCustomBossEvents() {
        return this.customBossEvents;
    }

    public boolean isEnforceWhitelist() {
        return this.enforceWhitelist;
    }

    public void setEnforceWhitelist(boolean whitelistEnabled) {
        this.enforceWhitelist = whitelistEnabled;
    }

    public float getCurrentSmoothedTickTime() {
        return this.smoothedTickTimeMillis;
    }

    public ServerTickRateManager tickRateManager() {
        return this.tickRateManager;
    }

    public long getAverageTickTimeNanos() {
        return this.aggregatedTickTimesNanos / Math.min(100, Math.max(this.tickCount, 1));
    }

    public long[] getTickTimesNanos() {
        return this.tickTimesNanos;
    }

    public int getProfilePermissions(GameProfile profile) {
        if (this.getPlayerList().isOp(profile)) {
            ServerOpListEntry serverOpListEntry = this.getPlayerList().getOps().get(profile);
            if (serverOpListEntry != null) {
                return serverOpListEntry.getLevel();
            } else if (this.isSingleplayerOwner(profile)) {
                return 4;
            } else if (this.isSingleplayer()) {
                return this.getPlayerList().isAllowCommandsForAllPlayers() ? 4 : 0;
            } else {
                return this.getOperatorUserPermissionLevel();
            }
        } else {
            return 0;
        }
    }

    public ProfilerFiller getProfiler() {
        return this.profiler;
    }

    public abstract boolean isSingleplayerOwner(GameProfile var1);

    public void dumpServerProperties(Path path) throws IOException {
    }

    private void saveDebugReport(Path path) {
        Path path2 = path.resolve("levels");

        try {
            for (Entry<ResourceKey<Level>, ServerLevel> entry : this.levels.entrySet()) {
                ResourceLocation resourceLocation = entry.getKey().location();
                Path path3 = path2.resolve(resourceLocation.getNamespace()).resolve(resourceLocation.getPath());
                Files.createDirectories(path3);
                entry.getValue().saveDebugReport(path3);
            }

            this.dumpGameRules(path.resolve("gamerules.txt"));
            this.dumpClasspath(path.resolve("classpath.txt"));
            this.dumpMiscStats(path.resolve("stats.txt"));
            this.dumpThreads(path.resolve("threads.txt"));
            this.dumpServerProperties(path.resolve("server.properties.txt"));
            this.dumpNativeModules(path.resolve("modules.txt"));
        } catch (IOException iOException) {
            LOGGER.warn("Failed to save debug report", iOException);
        }
    }

    private void dumpMiscStats(Path path) throws IOException {
        try (Writer writer = Files.newBufferedWriter(path)) {
            writer.write(String.format(Locale.ROOT, "pending_tasks: %d\n", this.getPendingTasksCount()));
            writer.write(String.format(Locale.ROOT, "average_tick_time: %f\n", this.getCurrentSmoothedTickTime()));
            writer.write(String.format(Locale.ROOT, "tick_times: %s\n", Arrays.toString(this.tickTimesNanos)));
            writer.write(String.format(Locale.ROOT, "queue: %s\n", Util.backgroundExecutor()));
        }
    }

    private void dumpGameRules(Path path) throws IOException {
        try (Writer writer = Files.newBufferedWriter(path)) {
            final List<String> list = Lists.newArrayList();
            final GameRules gameRules = this.getGameRules();
            GameRules.visitGameRuleTypes(new GameRules.GameRuleTypeVisitor() {
                @Override
                public <T extends GameRules.Value<T>> void visit(GameRules.Key<T> key, GameRules.Type<T> type) {
                    list.add(String.format(Locale.ROOT, "%s=%s\n", key.getId(), gameRules.getRule(key)));
                }
            });

            for (String string : list) {
                writer.write(string);
            }
        }
    }

    private void dumpClasspath(Path path) throws IOException {
        try (Writer writer = Files.newBufferedWriter(path)) {
            String string = System.getProperty("java.class.path");
            String string2 = System.getProperty("path.separator");

            for (String string3 : Splitter.on(string2).split(string)) {
                writer.write(string3);
                writer.write("\n");
            }
        }
    }

    private void dumpThreads(Path path) throws IOException {
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        ThreadInfo[] threadInfos = threadMXBean.dumpAllThreads(true, true);
        Arrays.sort(threadInfos, Comparator.comparing(ThreadInfo::getThreadName));

        try (Writer writer = Files.newBufferedWriter(path)) {
            for (ThreadInfo threadInfo : threadInfos) {
                writer.write(threadInfo.toString());
                writer.write(10);
            }
        }
    }

    private void dumpNativeModules(Path path) throws IOException {
        try (Writer writer = Files.newBufferedWriter(path)) {
            List<NativeModuleLister.NativeModuleInfo> list;
            try {
                list = Lists.newArrayList(NativeModuleLister.listModules());
            } catch (Throwable throwable) {
                LOGGER.warn("Failed to list native modules", throwable);
                return;
            }

            list.sort(Comparator.comparing(nativeModuleInfox -> nativeModuleInfox.name));

            for (NativeModuleLister.NativeModuleInfo nativeModuleInfo : list) {
                writer.write(nativeModuleInfo.toString());
                writer.write(10);
            }
        }
    }

    private void startMetricsRecordingTick() {
        if (this.willStartRecordingMetrics) {
            this.metricsRecorder = ActiveMetricsRecorder.createStarted(
                new ServerMetricsSamplersProvider(Util.timeSource, this.isDedicatedServer()),
                Util.timeSource,
                Util.ioPool(),
                new MetricsPersister("server"),
                this.onMetricsRecordingStopped,
                path -> {
                    this.executeBlocking(() -> this.saveDebugReport(path.resolve("server")));
                    this.onMetricsRecordingFinished.accept(path);
                }
            );
            this.willStartRecordingMetrics = false;
        }

        this.profiler = SingleTickProfiler.decorateFiller(this.metricsRecorder.getProfiler(), SingleTickProfiler.createTickProfiler("Server"));
        this.metricsRecorder.startTick();
        this.profiler.startTick();
    }

    public void endMetricsRecordingTick() {
        this.profiler.endTick();
        this.metricsRecorder.endTick();
    }

    public boolean isRecordingMetrics() {
        return this.metricsRecorder.isRecording();
    }

    public void startRecordingMetrics(Consumer<ProfileResults> output, Consumer<Path> onMetricsRecordingFinished) {
        this.onMetricsRecordingStopped = profileResults -> {
            this.stopRecordingMetrics();
            output.accept(profileResults);
        };
        this.onMetricsRecordingFinished = onMetricsRecordingFinished;
        this.willStartRecordingMetrics = true;
    }

    public void stopRecordingMetrics() {
        this.metricsRecorder = InactiveMetricsRecorder.INSTANCE;
    }

    public void finishRecordingMetrics() {
        this.metricsRecorder.end();
    }

    public void cancelRecordingMetrics() {
        this.metricsRecorder.cancel();
        this.profiler = this.metricsRecorder.getProfiler();
    }

    public Path getWorldPath(LevelResource levelResource) {
        return this.storageSource.getLevelPath(levelResource);
    }

    public boolean forceSynchronousWrites() {
        return true;
    }

    public StructureTemplateManager getStructureManager() {
        return this.structureTemplateManager;
    }

    public WorldData getWorldData() {
        return this.worldData;
    }

    public RegistryAccess.Frozen registryAccess() {
        return this.registries.compositeAccess();
    }

    public LayeredRegistryAccess<RegistryLayer> registries() {
        return this.registries;
    }

    public ReloadableServerRegistries.Holder reloadableRegistries() {
        return this.resources.managers.fullRegistries();
    }

    public TextFilter createTextFilterForPlayer(ServerPlayer player) {
        return TextFilter.DUMMY;
    }

    public ServerPlayerGameMode createGameModeForPlayer(ServerPlayer player) {
        return this.isDemo() ? new DemoMode(player) : new ServerPlayerGameMode(player);
    }

    @Nullable
    public GameType getForcedGameType() {
        return null;
    }

    public ResourceManager getResourceManager() {
        return this.resources.resourceManager;
    }

    public boolean isCurrentlySaving() {
        return this.isSaving;
    }

    public boolean isTimeProfilerRunning() {
        return this.debugCommandProfilerDelayStart || this.debugCommandProfiler != null;
    }

    public void startTimeProfiler() {
        this.debugCommandProfilerDelayStart = true;
    }

    public ProfileResults stopTimeProfiler() {
        if (this.debugCommandProfiler == null) {
            return EmptyProfileResults.EMPTY;
        }

        ProfileResults profileResults = this.debugCommandProfiler.stop(Util.getNanos(), this.tickCount);
        this.debugCommandProfiler = null;
        return profileResults;
    }

    public int getMaxChainedNeighborUpdates() {
        return 1000000;
    }

    public void logChatMessage(Component content, ChatType.Bound boundChatType, @Nullable String header) {
        String string = boundChatType.decorate(content).getString();
        if (header != null) {
            LOGGER.info("[{}] {}", header, string);
        } else {
            LOGGER.info("{}", string);
        }
    }

    public ChatDecorator getChatDecorator() {
        return ChatDecorator.PLAIN;
    }

    public boolean logIPs() {
        return true;
    }

    public void subscribeToDebugSample(ServerPlayer player, RemoteDebugSampleType sampleType) {
    }

    public boolean acceptsTransfers() {
        return false;
    }

    private void storeChunkIoError(CrashReport crashReport, ChunkPos chunkPos, RegionStorageInfo regionStorageInfo) {
        Util.ioPool().execute(() -> {
            try {
                Path path = this.getFile("debug");
                FileUtil.createDirectoriesSafe(path);
                String string = FileUtil.sanitizeName(regionStorageInfo.level());
                Path path2 = path.resolve("chunk-" + string + "-" + Util.getFilenameFormattedDateTime() + "-server.txt");
                FileStore fileStore = Files.getFileStore(path);
                long l = fileStore.getUsableSpace();
                if (l < 8192L) {
                    LOGGER.warn("Not storing chunk IO report due to low space on drive {}", fileStore.name());
                    return;
                }

                CrashReportCategory crashReportCategory = crashReport.addCategory("Chunk Info");
                crashReportCategory.setDetail("Level", regionStorageInfo::level);
                crashReportCategory.setDetail("Dimension", () -> regionStorageInfo.dimension().location().toString());
                crashReportCategory.setDetail("Storage", regionStorageInfo::type);
                crashReportCategory.setDetail("Position", chunkPos::toString);
                crashReport.saveToFile(path2, ReportType.CHUNK_IO_ERROR);
                LOGGER.info("Saved details to {}", crashReport.getSaveFile());
            } catch (Exception exception) {
                LOGGER.warn("Failed to store chunk IO exception", exception);
            }
        });
    }

    @Override
    public void reportChunkLoadFailure(Throwable throwable, RegionStorageInfo regionStorageInfo, ChunkPos chunkPos) {
        LOGGER.error("Failed to load chunk {},{}", chunkPos.x, chunkPos.z, throwable);
        this.storeChunkIoError(CrashReport.forThrowable(throwable, "Chunk load failure"), chunkPos, regionStorageInfo);
    }

    @Override
    public void reportChunkSaveFailure(Throwable throwable, RegionStorageInfo regionStorageInfo, ChunkPos chunkPos) {
        LOGGER.error("Failed to save chunk {},{}", chunkPos.x, chunkPos.z, throwable);
        this.storeChunkIoError(CrashReport.forThrowable(throwable, "Chunk save failure"), chunkPos, regionStorageInfo);
    }

    public PotionBrewing potionBrewing() {
        return this.potionBrewing;
    }

    public ServerLinks serverLinks() {
        return ServerLinks.EMPTY;
    }

    record ReloadableResources(CloseableResourceManager resourceManager, ReloadableServerResources managers) implements AutoCloseable {
        @Override
        public void close() {
            this.resourceManager.close();
        }
    }

    public record ServerResourcePackInfo(UUID id, String url, String hash, boolean isRequired, @Nullable Component prompt) {
    }

    static class TimeProfiler {
        final long startNanos;
        final int startTick;

        TimeProfiler(long startNanos, int startTick) {
            this.startNanos = startNanos;
            this.startTick = startTick;
        }

        ProfileResults stop(long endTimeNano, int endTimeTicks) {
            return new ProfileResults() {
                @Override
                public List<ResultField> getTimes(String string) {
                    return Collections.emptyList();
                }

                @Override
                public boolean saveResults(Path path) {
                    return false;
                }

                @Override
                public long getStartTimeNano() {
                    return TimeProfiler.this.startNanos;
                }

                @Override
                public int getStartTimeTicks() {
                    return TimeProfiler.this.startTick;
                }

                @Override
                public long getEndTimeNano() {
                    return endTimeNano;
                }

                @Override
                public int getEndTimeTicks() {
                    return endTimeTicks;
                }

                @Override
                public String getProfilerResults() {
                    return "";
                }
            };
        }
    }
}
