package net.minecraft.server.level;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.mojang.datafixers.DataFixer;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportType;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundSetDefaultSpawnPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.server.players.SleepStatus;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.util.CsvOutput;
import net.minecraft.util.Mth;
import net.minecraft.util.ProgressListener;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Unit;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.RandomSequences;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ReputationEventHandler;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.village.ReputationEventType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.animal.horse.SkeletonHorse;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raids;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.BlockEventData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.ForcedChunksSavedData;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.storage.EntityStorage;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.dimension.end.EndDragonFight;
import net.minecraft.world.level.entity.EntityPersistentStorage;
import net.minecraft.world.level.entity.EntityTickList;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.entity.LevelCallback;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.level.gameevent.DynamicGameEventListener;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEventDispatcher;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheck;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathTypeCache;
import net.minecraft.world.level.portal.PortalForcer;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapIndex;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.ticks.LevelTicks;
import org.slf4j.Logger;

public class ServerLevel extends Level implements WorldGenLevel {
    public static final BlockPos END_SPAWN_POINT = new BlockPos(100, 50, 0);
    public static final IntProvider RAIN_DELAY = UniformInt.of(12000, 180000);
    public static final IntProvider RAIN_DURATION = UniformInt.of(12000, 24000);
    private static final IntProvider THUNDER_DELAY = UniformInt.of(12000, 180000);
    public static final IntProvider THUNDER_DURATION = UniformInt.of(3600, 15600);
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int EMPTY_TIME_NO_TICK = 300;
    private static final int MAX_SCHEDULED_TICKS_PER_TICK = 65536;
    final List<ServerPlayer> players = Lists.newArrayList();
    private final ServerChunkCache chunkSource;
    private final MinecraftServer server;
    private final ServerLevelData serverLevelData;
    private int lastSpawnChunkRadius;
    final EntityTickList entityTickList = new EntityTickList();
    private final PersistentEntitySectionManager<Entity> entityManager;
    private final GameEventDispatcher gameEventDispatcher;
    public boolean noSave;
    private final SleepStatus sleepStatus;
    private int emptyTime;
    private final PortalForcer portalForcer;
    private final LevelTicks<Block> blockTicks = new LevelTicks<>(this::isPositionTickingWithEntitiesLoaded, this.getProfilerSupplier());
    private final LevelTicks<Fluid> fluidTicks = new LevelTicks<>(this::isPositionTickingWithEntitiesLoaded, this.getProfilerSupplier());
    private final PathTypeCache pathTypesByPosCache = new PathTypeCache();
    final Set<Mob> navigatingMobs = new ObjectOpenHashSet<>();
    volatile boolean isUpdatingNavigations;
    protected final Raids raids;
    private final ObjectLinkedOpenHashSet<BlockEventData> blockEvents = new ObjectLinkedOpenHashSet<>();
    private final List<BlockEventData> blockEventsToReschedule = new ArrayList<>(64);
    private boolean handlingTick;
    private final List<CustomSpawner> customSpawners;
    @Nullable
    private EndDragonFight dragonFight;
    final Int2ObjectMap<EnderDragonPart> dragonParts = new Int2ObjectOpenHashMap<>();
    private final StructureManager structureManager;
    private final StructureCheck structureCheck;
    private final boolean tickTime;
    private final RandomSequences randomSequences;

    public ServerLevel(
        MinecraftServer server,
        Executor dispatcher,
        LevelStorageSource.LevelStorageAccess levelStorageAccess,
        ServerLevelData serverLevelData,
        ResourceKey<Level> dimension,
        LevelStem levelStem,
        ChunkProgressListener progressListener,
        boolean isDebug,
        long biomeZoomSeed,
        List<CustomSpawner> customSpawners,
        boolean tickTime,
        @Nullable RandomSequences randomSequences
    ) {
        super(
            serverLevelData,
            dimension,
            server.registryAccess(),
            levelStem.type(),
            server::getProfiler,
            false,
            isDebug,
            biomeZoomSeed,
            server.getMaxChainedNeighborUpdates()
        );
        this.tickTime = tickTime;
        this.server = server;
        this.customSpawners = customSpawners;
        this.serverLevelData = serverLevelData;
        ChunkGenerator chunkGenerator = levelStem.generator();
        boolean bl = server.forceSynchronousWrites();
        DataFixer dataFixer = server.getFixerUpper();
        EntityPersistentStorage<Entity> entityPersistentStorage = new EntityStorage(
            new SimpleRegionStorage(
                new RegionStorageInfo(levelStorageAccess.getLevelId(), dimension, "entities"),
                levelStorageAccess.getDimensionPath(dimension).resolve("entities"),
                dataFixer,
                bl,
                DataFixTypes.ENTITY_CHUNK
            ),
            this,
            server
        );
        this.entityManager = new PersistentEntitySectionManager<>(Entity.class, new ServerLevel.EntityCallbacks(), entityPersistentStorage);
        this.chunkSource = new ServerChunkCache(
            this,
            levelStorageAccess,
            dataFixer,
            server.getStructureManager(),
            dispatcher,
            chunkGenerator,
            server.getPlayerList().getViewDistance(),
            server.getPlayerList().getSimulationDistance(),
            bl,
            progressListener,
            this.entityManager::updateChunkStatus,
            () -> server.overworld().getDataStorage()
        );
        this.chunkSource.getGeneratorState().ensureStructuresGenerated();
        this.portalForcer = new PortalForcer(this);
        this.updateSkyBrightness();
        this.prepareWeather();
        this.getWorldBorder().setAbsoluteMaxSize(server.getAbsoluteMaxWorldSize());
        this.raids = this.getDataStorage().computeIfAbsent(Raids.factory(this), Raids.getFileId(this.dimensionTypeRegistration()));
        if (!server.isSingleplayer()) {
            serverLevelData.setGameType(server.getDefaultGameType());
        }

        long l = server.getWorldData().worldGenOptions().seed();
        this.structureCheck = new StructureCheck(
            this.chunkSource.chunkScanner(),
            this.registryAccess(),
            server.getStructureManager(),
            dimension,
            chunkGenerator,
            this.chunkSource.randomState(),
            this,
            chunkGenerator.getBiomeSource(),
            l,
            dataFixer
        );
        this.structureManager = new StructureManager(this, server.getWorldData().worldGenOptions(), this.structureCheck);
        if (this.dimension() == Level.END && this.dimensionTypeRegistration().is(BuiltinDimensionTypes.END)) {
            this.dragonFight = new EndDragonFight(this, l, server.getWorldData().endDragonFightData());
        } else {
            this.dragonFight = null;
        }

        this.sleepStatus = new SleepStatus();
        this.gameEventDispatcher = new GameEventDispatcher(this);
        this.randomSequences = Objects.requireNonNullElseGet(
            randomSequences, () -> this.getDataStorage().computeIfAbsent(RandomSequences.factory(l), "random_sequences")
        );
    }

    @Deprecated
    @VisibleForTesting
    public void setDragonFight(@Nullable EndDragonFight dragonFight) {
        this.dragonFight = dragonFight;
    }

    public void setWeatherParameters(int clearTime, int weatherTime, boolean isRaining, boolean isThundering) {
        this.serverLevelData.setClearWeatherTime(clearTime);
        this.serverLevelData.setRainTime(weatherTime);
        this.serverLevelData.setThunderTime(weatherTime);
        this.serverLevelData.setRaining(isRaining);
        this.serverLevelData.setThundering(isThundering);
    }

    @Override
    public Holder<Biome> getUncachedNoiseBiome(int x, int y, int z) {
        return this.getChunkSource().getGenerator().getBiomeSource().getNoiseBiome(x, y, z, this.getChunkSource().randomState().sampler());
    }

    public StructureManager structureManager() {
        return this.structureManager;
    }

    /**
     * Runs a single tick for the world
     */
    public void tick(BooleanSupplier hasTimeLeft) {
        ProfilerFiller profilerFiller = this.getProfiler();
        this.handlingTick = true;
        TickRateManager tickRateManager = this.tickRateManager();
        boolean bl = tickRateManager.runsNormally();
        if (bl) {
            profilerFiller.push("world border");
            this.getWorldBorder().tick();
            profilerFiller.popPush("weather");
            this.advanceWeatherCycle();
        }

        int i = this.getGameRules().getInt(GameRules.RULE_PLAYERS_SLEEPING_PERCENTAGE);
        if (this.sleepStatus.areEnoughSleeping(i) && this.sleepStatus.areEnoughDeepSleeping(i, this.players)) {
            if (this.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)) {
                long l = this.levelData.getDayTime() + 24000L;
                this.setDayTime(l - l % 24000L);
            }

            this.wakeUpAllPlayers();
            if (this.getGameRules().getBoolean(GameRules.RULE_WEATHER_CYCLE) && this.isRaining()) {
                this.resetWeatherCycle();
            }
        }

        this.updateSkyBrightness();
        if (bl) {
            this.tickTime();
        }

        profilerFiller.popPush("tickPending");
        if (!this.isDebug() && bl) {
            long l = this.getGameTime();
            profilerFiller.push("blockTicks");
            this.blockTicks.tick(l, 65536, this::tickBlock);
            profilerFiller.popPush("fluidTicks");
            this.fluidTicks.tick(l, 65536, this::tickFluid);
            profilerFiller.pop();
        }

        profilerFiller.popPush("raid");
        if (bl) {
            this.raids.tick();
        }

        profilerFiller.popPush("chunkSource");
        this.getChunkSource().tick(hasTimeLeft, true);
        profilerFiller.popPush("blockEvents");
        if (bl) {
            this.runBlockEvents();
        }

        this.handlingTick = false;
        profilerFiller.pop();
        boolean bl2 = !this.players.isEmpty() || !this.getForcedChunks().isEmpty();
        if (bl2) {
            this.resetEmptyTime();
        }

        if (bl2 || this.emptyTime++ < 300) {
            profilerFiller.push("entities");
            if (this.dragonFight != null && bl) {
                profilerFiller.push("dragonFight");
                this.dragonFight.tick();
                profilerFiller.pop();
            }

            this.entityTickList.forEach(entity -> {
                if (!entity.isRemoved()) {
                    if (this.shouldDiscardEntity(entity)) {
                        entity.discard();
                    } else if (!tickRateManager.isEntityFrozen(entity)) {
                        profilerFiller.push("checkDespawn");
                        entity.checkDespawn();
                        profilerFiller.pop();
                        if (this.chunkSource.chunkMap.getDistanceManager().inEntityTickingRange(entity.chunkPosition().toLong())) {
                            Entity entity2 = entity.getVehicle();
                            if (entity2 != null) {
                                if (!entity2.isRemoved() && entity2.hasPassenger(entity)) {
                                    return;
                                }

                                entity.stopRiding();
                            }

                            profilerFiller.push("tick");
                            this.guardEntityTick(this::tickNonPassenger, entity);
                            profilerFiller.pop();
                        }
                    }
                }
            });
            profilerFiller.pop();
            this.tickBlockEntities();
        }

        profilerFiller.push("entityManagement");
        this.entityManager.tick();
        profilerFiller.pop();
    }

    @Override
    public boolean shouldTickBlocksAt(long chunkPos) {
        return this.chunkSource.chunkMap.getDistanceManager().inBlockTickingRange(chunkPos);
    }

    protected void tickTime() {
        if (this.tickTime) {
            long l = this.levelData.getGameTime() + 1L;
            this.serverLevelData.setGameTime(l);
            this.serverLevelData.getScheduledEvents().tick(this.server, l);
            if (this.levelData.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)) {
                this.setDayTime(this.levelData.getDayTime() + 1L);
            }
        }
    }

    public void setDayTime(long time) {
        this.serverLevelData.setDayTime(time);
    }

    public void tickCustomSpawners(boolean spawnEnemies, boolean spawnFriendlies) {
        for (CustomSpawner customSpawner : this.customSpawners) {
            customSpawner.tick(this, spawnEnemies, spawnFriendlies);
        }
    }

    private boolean shouldDiscardEntity(Entity entity) {
        return this.server.isSpawningAnimals() || !(entity instanceof Animal) && !(entity instanceof WaterAnimal)
            ? !this.server.areNpcsEnabled() && entity instanceof Npc
            : true;
    }

    private void wakeUpAllPlayers() {
        this.sleepStatus.removeAllSleepers();
        this.players.stream().filter(LivingEntity::isSleeping).collect(Collectors.toList()).forEach(player -> player.stopSleepInBed(false, false));
    }

    public void tickChunk(LevelChunk chunk, int randomTickSpeed) {
        ChunkPos chunkPos = chunk.getPos();
        boolean bl = this.isRaining();
        int i = chunkPos.getMinBlockX();
        int j = chunkPos.getMinBlockZ();
        ProfilerFiller profilerFiller = this.getProfiler();
        profilerFiller.push("thunder");
        if (bl && this.isThundering() && this.random.nextInt(100000) == 0) {
            BlockPos blockPos = this.findLightningTargetAround(this.getBlockRandomPos(i, 0, j, 15));
            if (this.isRainingAt(blockPos)) {
                DifficultyInstance difficultyInstance = this.getCurrentDifficultyAt(blockPos);
                boolean bl2 = this.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)
                    && this.random.nextDouble() < difficultyInstance.getEffectiveDifficulty() * 0.01
                    && !this.getBlockState(blockPos.below()).is(Blocks.LIGHTNING_ROD);
                if (bl2) {
                    SkeletonHorse skeletonHorse = EntityType.SKELETON_HORSE.create(this);
                    if (skeletonHorse != null) {
                        skeletonHorse.setTrap(true);
                        skeletonHorse.setAge(0);
                        skeletonHorse.setPos(blockPos.getX(), blockPos.getY(), blockPos.getZ());
                        this.addFreshEntity(skeletonHorse);
                    }
                }

                LightningBolt lightningBolt = EntityType.LIGHTNING_BOLT.create(this);
                if (lightningBolt != null) {
                    lightningBolt.moveTo(Vec3.atBottomCenterOf(blockPos));
                    lightningBolt.setVisualOnly(bl2);
                    this.addFreshEntity(lightningBolt);
                }
            }
        }

        profilerFiller.popPush("iceandsnow");

        for (int k = 0; k < randomTickSpeed; k++) {
            if (this.random.nextInt(48) == 0) {
                this.tickPrecipitation(this.getBlockRandomPos(i, 0, j, 15));
            }
        }

        profilerFiller.popPush("tickBlocks");
        if (randomTickSpeed > 0) {
            LevelChunkSection[] levelChunkSections = chunk.getSections();

            for (int l = 0; l < levelChunkSections.length; l++) {
                LevelChunkSection levelChunkSection = levelChunkSections[l];
                if (levelChunkSection.isRandomlyTicking()) {
                    int m = chunk.getSectionYFromSectionIndex(l);
                    int n = SectionPos.sectionToBlockCoord(m);

                    for (int o = 0; o < randomTickSpeed; o++) {
                        BlockPos blockPos2 = this.getBlockRandomPos(i, n, j, 15);
                        profilerFiller.push("randomTick");
                        BlockState blockState = levelChunkSection.getBlockState(blockPos2.getX() - i, blockPos2.getY() - n, blockPos2.getZ() - j);
                        if (blockState.isRandomlyTicking()) {
                            blockState.randomTick(this, blockPos2, this.random);
                        }

                        FluidState fluidState = blockState.getFluidState();
                        if (fluidState.isRandomlyTicking()) {
                            fluidState.randomTick(this, blockPos2, this.random);
                        }

                        profilerFiller.pop();
                    }
                }
            }
        }

        profilerFiller.pop();
    }

    @VisibleForTesting
    public void tickPrecipitation(BlockPos blockPos) {
        BlockPos blockPos2 = this.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, blockPos);
        BlockPos blockPos3 = blockPos2.below();
        Biome biome = this.getBiome(blockPos2).value();
        if (biome.shouldFreeze(this, blockPos3)) {
            this.setBlockAndUpdate(blockPos3, Blocks.ICE.defaultBlockState());
        }

        if (this.isRaining()) {
            int i = this.getGameRules().getInt(GameRules.RULE_SNOW_ACCUMULATION_HEIGHT);
            if (i > 0 && biome.shouldSnow(this, blockPos2)) {
                BlockState blockState = this.getBlockState(blockPos2);
                if (blockState.is(Blocks.SNOW)) {
                    int j = blockState.getValue(SnowLayerBlock.LAYERS);
                    if (j < Math.min(i, 8)) {
                        BlockState blockState2 = blockState.setValue(SnowLayerBlock.LAYERS, j + 1);
                        Block.pushEntitiesUp(blockState, blockState2, this, blockPos2);
                        this.setBlockAndUpdate(blockPos2, blockState2);
                    }
                } else {
                    this.setBlockAndUpdate(blockPos2, Blocks.SNOW.defaultBlockState());
                }
            }

            Biome.Precipitation precipitation = biome.getPrecipitationAt(blockPos3);
            if (precipitation != Biome.Precipitation.NONE) {
                BlockState blockState3 = this.getBlockState(blockPos3);
                blockState3.getBlock().handlePrecipitation(blockState3, this, blockPos3, precipitation);
            }
        }
    }

    private Optional<BlockPos> findLightningRod(BlockPos pos) {
        Optional<BlockPos> optional = this.getPoiManager()
            .findClosest(
                pointOfInterestType -> pointOfInterestType.is(PoiTypes.LIGHTNING_ROD),
                blockPos -> blockPos.getY() == this.getHeight(Heightmap.Types.WORLD_SURFACE, blockPos.getX(), blockPos.getZ()) - 1,
                pos,
                128,
                PoiManager.Occupancy.ANY
            );
        return optional.map(blockPos -> blockPos.above(1));
    }

    protected BlockPos findLightningTargetAround(BlockPos pos) {
        BlockPos blockPos = this.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, pos);
        Optional<BlockPos> optional = this.findLightningRod(blockPos);
        if (optional.isPresent()) {
            return optional.get();
        }

        AABB aABB = AABB.encapsulatingFullBlocks(blockPos, new BlockPos(blockPos.atY(this.getMaxBuildHeight()))).inflate(3.0);
        List<LivingEntity> list = this.getEntitiesOfClass(
            LivingEntity.class, aABB, entity -> entity != null && entity.isAlive() && this.canSeeSky(entity.blockPosition())
        );
        if (!list.isEmpty()) {
            return list.get(this.random.nextInt(list.size())).blockPosition();
        }

        if (blockPos.getY() == this.getMinBuildHeight() - 1) {
            blockPos = blockPos.above(2);
        }

        return blockPos;
    }

    public boolean isHandlingTick() {
        return this.handlingTick;
    }

    public boolean canSleepThroughNights() {
        return this.getGameRules().getInt(GameRules.RULE_PLAYERS_SLEEPING_PERCENTAGE) <= 100;
    }

    private void announceSleepStatus() {
        if (this.canSleepThroughNights()) {
            if (!this.getServer().isSingleplayer() || this.getServer().isPublished()) {
                int i = this.getGameRules().getInt(GameRules.RULE_PLAYERS_SLEEPING_PERCENTAGE);
                Component component;
                if (this.sleepStatus.areEnoughSleeping(i)) {
                    component = Component.translatable("sleep.skipping_night");
                } else {
                    component = Component.translatable("sleep.players_sleeping", this.sleepStatus.amountSleeping(), this.sleepStatus.sleepersNeeded(i));
                }

                for (ServerPlayer serverPlayer : this.players) {
                    serverPlayer.displayClientMessage(component, true);
                }
            }
        }
    }

    public void updateSleepingPlayerList() {
        if (!this.players.isEmpty() && this.sleepStatus.update(this.players)) {
            this.announceSleepStatus();
        }
    }

    public ServerScoreboard getScoreboard() {
        return this.server.getScoreboard();
    }

    private void advanceWeatherCycle() {
        boolean bl = this.isRaining();
        if (this.dimensionType().hasSkyLight()) {
            if (this.getGameRules().getBoolean(GameRules.RULE_WEATHER_CYCLE)) {
                int i = this.serverLevelData.getClearWeatherTime();
                int j = this.serverLevelData.getThunderTime();
                int k = this.serverLevelData.getRainTime();
                boolean bl2 = this.levelData.isThundering();
                boolean bl3 = this.levelData.isRaining();
                if (i > 0) {
                    i--;
                    j = bl2 ? 0 : 1;
                    k = bl3 ? 0 : 1;
                    bl2 = false;
                    bl3 = false;
                } else {
                    if (j > 0) {
                        if (--j == 0) {
                            bl2 = !bl2;
                        }
                    } else if (bl2) {
                        j = THUNDER_DURATION.sample(this.random);
                    } else {
                        j = THUNDER_DELAY.sample(this.random);
                    }

                    if (k > 0) {
                        if (--k == 0) {
                            bl3 = !bl3;
                        }
                    } else if (bl3) {
                        k = RAIN_DURATION.sample(this.random);
                    } else {
                        k = RAIN_DELAY.sample(this.random);
                    }
                }

                this.serverLevelData.setThunderTime(j);
                this.serverLevelData.setRainTime(k);
                this.serverLevelData.setClearWeatherTime(i);
                this.serverLevelData.setThundering(bl2);
                this.serverLevelData.setRaining(bl3);
            }

            this.oThunderLevel = this.thunderLevel;
            if (this.levelData.isThundering()) {
                this.thunderLevel += 0.01F;
            } else {
                this.thunderLevel -= 0.01F;
            }

            this.thunderLevel = Mth.clamp(this.thunderLevel, 0.0F, 1.0F);
            this.oRainLevel = this.rainLevel;
            if (this.levelData.isRaining()) {
                this.rainLevel += 0.01F;
            } else {
                this.rainLevel -= 0.01F;
            }

            this.rainLevel = Mth.clamp(this.rainLevel, 0.0F, 1.0F);
        }

        if (this.oRainLevel != this.rainLevel) {
            this.server
                .getPlayerList()
                .broadcastAll(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, this.rainLevel), this.dimension());
        }

        if (this.oThunderLevel != this.thunderLevel) {
            this.server
                .getPlayerList()
                .broadcastAll(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, this.thunderLevel), this.dimension());
        }

        if (bl != this.isRaining()) {
            if (bl) {
                this.server.getPlayerList().broadcastAll(new ClientboundGameEventPacket(ClientboundGameEventPacket.STOP_RAINING, 0.0F));
            } else {
                this.server.getPlayerList().broadcastAll(new ClientboundGameEventPacket(ClientboundGameEventPacket.START_RAINING, 0.0F));
            }

            this.server.getPlayerList().broadcastAll(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, this.rainLevel));
            this.server.getPlayerList().broadcastAll(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, this.thunderLevel));
        }
    }

    @VisibleForTesting
    public void resetWeatherCycle() {
        this.serverLevelData.setRainTime(0);
        this.serverLevelData.setRaining(false);
        this.serverLevelData.setThunderTime(0);
        this.serverLevelData.setThundering(false);
    }

    public void resetEmptyTime() {
        this.emptyTime = 0;
    }

    private void tickFluid(BlockPos pos, Fluid fluid) {
        FluidState fluidState = this.getFluidState(pos);
        if (fluidState.is(fluid)) {
            fluidState.tick(this, pos);
        }
    }

    private void tickBlock(BlockPos pos, Block block) {
        BlockState blockState = this.getBlockState(pos);
        if (blockState.is(block)) {
            blockState.tick(this, pos, this.random);
        }
    }

    public void tickNonPassenger(Entity entity) {
        entity.setOldPosAndRot();
        ProfilerFiller profilerFiller = this.getProfiler();
        entity.tickCount++;
        this.getProfiler().push(() -> BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
        profilerFiller.incrementCounter("tickNonPassenger");
        entity.tick();
        this.getProfiler().pop();

        for (Entity entity2 : entity.getPassengers()) {
            this.tickPassenger(entity, entity2);
        }
    }

    private void tickPassenger(Entity ridingEntity, Entity passengerEntity) {
        if (passengerEntity.isRemoved() || passengerEntity.getVehicle() != ridingEntity) {
            passengerEntity.stopRiding();
        } else if (passengerEntity instanceof Player || this.entityTickList.contains(passengerEntity)) {
            passengerEntity.setOldPosAndRot();
            passengerEntity.tickCount++;
            ProfilerFiller profilerFiller = this.getProfiler();
            profilerFiller.push(() -> BuiltInRegistries.ENTITY_TYPE.getKey(passengerEntity.getType()).toString());
            profilerFiller.incrementCounter("tickPassenger");
            passengerEntity.rideTick();
            profilerFiller.pop();

            for (Entity entity : passengerEntity.getPassengers()) {
                this.tickPassenger(passengerEntity, entity);
            }
        }
    }

    @Override
    public boolean mayInteract(Player player, BlockPos pos) {
        return !this.server.isUnderSpawnProtection(this, pos, player) && this.getWorldBorder().isWithinBounds(pos);
    }

    public void save(@Nullable ProgressListener progress, boolean flush, boolean skipSave) {
        ServerChunkCache serverChunkCache = this.getChunkSource();
        if (!skipSave) {
            if (progress != null) {
                progress.progressStartNoAbort(Component.translatable("menu.savingLevel"));
            }

            this.saveLevelData();
            if (progress != null) {
                progress.progressStage(Component.translatable("menu.savingChunks"));
            }

            serverChunkCache.save(flush);
            if (flush) {
                this.entityManager.saveAll();
            } else {
                this.entityManager.autoSave();
            }
        }
    }

    private void saveLevelData() {
        if (this.dragonFight != null) {
            this.server.getWorldData().setEndDragonFightData(this.dragonFight.saveData());
        }

        this.getChunkSource().getDataStorage().save();
    }

    public <T extends Entity> List<? extends T> getEntities(EntityTypeTest<Entity, T> typeTest, Predicate<? super T> predicate) {
        List<T> list = Lists.newArrayList();
        this.getEntities(typeTest, predicate, list);
        return list;
    }

    public <T extends Entity> void getEntities(EntityTypeTest<Entity, T> typeTest, Predicate<? super T> predicate, List<? super T> output) {
        this.getEntities(typeTest, predicate, output, Integer.MAX_VALUE);
    }

    public <T extends Entity> void getEntities(EntityTypeTest<Entity, T> typeTest, Predicate<? super T> predicate, List<? super T> output, int maxResults) {
        this.getEntities().get(typeTest, entity -> {
            if (predicate.test(entity)) {
                output.add(entity);
                if (output.size() >= maxResults) {
                    return AbortableIterationConsumer.Continuation.ABORT;
                }
            }

            return AbortableIterationConsumer.Continuation.CONTINUE;
        });
    }

    public List<? extends EnderDragon> getDragons() {
        return this.getEntities(EntityType.ENDER_DRAGON, LivingEntity::isAlive);
    }

    public List<ServerPlayer> getPlayers(Predicate<? super ServerPlayer> predicate) {
        return this.getPlayers(predicate, Integer.MAX_VALUE);
    }

    public List<ServerPlayer> getPlayers(Predicate<? super ServerPlayer> predicate, int maxResults) {
        List<ServerPlayer> list = Lists.newArrayList();

        for (ServerPlayer serverPlayer : this.players) {
            if (predicate.test(serverPlayer)) {
                list.add(serverPlayer);
                if (list.size() >= maxResults) {
                    return list;
                }
            }
        }

        return list;
    }

    @Nullable
    public ServerPlayer getRandomPlayer() {
        List<ServerPlayer> list = this.getPlayers(LivingEntity::isAlive);
        return list.isEmpty() ? null : list.get(this.random.nextInt(list.size()));
    }

    @Override
    public boolean addFreshEntity(Entity entity) {
        return this.addEntity(entity);
    }

    /**
     * Used for "unnatural" ways of entities appearing in the world, e.g. summon command, interdimensional teleports
     */
    public boolean addWithUUID(Entity entity) {
        return this.addEntity(entity);
    }

    public void addDuringTeleport(Entity entity) {
        if (entity instanceof ServerPlayer serverPlayer) {
            this.addPlayer(serverPlayer);
        } else {
            this.addEntity(entity);
        }
    }

    public void addNewPlayer(ServerPlayer player) {
        this.addPlayer(player);
    }

    public void addRespawnedPlayer(ServerPlayer player) {
        this.addPlayer(player);
    }

    private void addPlayer(ServerPlayer player) {
        Entity entity = this.getEntities().get(player.getUUID());
        if (entity != null) {
            LOGGER.warn("Force-added player with duplicate UUID {}", player.getUUID());
            entity.unRide();
            this.removePlayerImmediately((ServerPlayer)entity, Entity.RemovalReason.DISCARDED);
        }

        this.entityManager.addNewEntity(player);
    }

    /**
     * Called when an entity is spawned in the world. This includes players.
     */
    private boolean addEntity(Entity entity) {
        if (entity.isRemoved()) {
            LOGGER.warn("Tried to add entity {} but it was marked as removed already", EntityType.getKey(entity.getType()));
            return false;
        } else {
            return this.entityManager.addNewEntity(entity);
        }
    }

    /**
     * Attempts to summon an entity and it's passangers. They will only be summoned if all entities are unique and not already in queue to be summoned.
     */
    public boolean tryAddFreshEntityWithPassengers(Entity entity) {
        if (entity.getSelfAndPassengers().map(Entity::getUUID).anyMatch(this.entityManager::isLoaded)) {
            return false;
        }

        this.addFreshEntityWithPassengers(entity);
        return true;
    }

    public void unload(LevelChunk chunk) {
        chunk.clearAllBlockEntities();
        chunk.unregisterTickContainerFromLevel(this);
    }

    public void removePlayerImmediately(ServerPlayer player, Entity.RemovalReason reason) {
        player.remove(reason);
    }

    @Override
    public void destroyBlockProgress(int breakerId, BlockPos pos, int progress) {
        for (ServerPlayer serverPlayer : this.server.getPlayerList().getPlayers()) {
            if (serverPlayer != null && serverPlayer.level() == this && serverPlayer.getId() != breakerId) {
                double d = pos.getX() - serverPlayer.getX();
                double e = pos.getY() - serverPlayer.getY();
                double f = pos.getZ() - serverPlayer.getZ();
                if (d * d + e * e + f * f < 1024.0) {
                    serverPlayer.connection.send(new ClientboundBlockDestructionPacket(breakerId, pos, progress));
                }
            }
        }
    }

    @Override
    public void playSeededSound(
        @Nullable Player player, double x, double y, double z, Holder<SoundEvent> sound, SoundSource category, float volume, float pitch, long seed
    ) {
        this.server
            .getPlayerList()
            .broadcast(
                player, x, y, z, sound.value().getRange(volume), this.dimension(), new ClientboundSoundPacket(sound, category, x, y, z, volume, pitch, seed)
            );
    }

    @Override
    public void playSeededSound(@Nullable Player player, Entity entity, Holder<SoundEvent> sound, SoundSource category, float volume, float pitch, long seed) {
        this.server
            .getPlayerList()
            .broadcast(
                player,
                entity.getX(),
                entity.getY(),
                entity.getZ(),
                sound.value().getRange(volume),
                this.dimension(),
                new ClientboundSoundEntityPacket(sound, category, entity, volume, pitch, seed)
            );
    }

    @Override
    public void globalLevelEvent(int id, BlockPos pos, int data) {
        if (this.getGameRules().getBoolean(GameRules.RULE_GLOBAL_SOUND_EVENTS)) {
            this.server.getPlayerList().broadcastAll(new ClientboundLevelEventPacket(id, pos, data, true));
        } else {
            this.levelEvent(null, id, pos, data);
        }
    }

    @Override
    public void levelEvent(@Nullable Player player, int type, BlockPos pos, int data) {
        this.server
            .getPlayerList()
            .broadcast(player, pos.getX(), pos.getY(), pos.getZ(), 64.0, this.dimension(), new ClientboundLevelEventPacket(type, pos, data, false));
    }

    public int getLogicalHeight() {
        return this.dimensionType().logicalHeight();
    }

    @Override
    public void gameEvent(Holder<GameEvent> gameEvent, Vec3 pos, GameEvent.Context context) {
        this.gameEventDispatcher.post(gameEvent, pos, context);
    }

    /**
     * Flags are as in setBlockState
     */
    @Override
    public void sendBlockUpdated(BlockPos pos, BlockState oldState, BlockState newState, int flags) {
        if (this.isUpdatingNavigations) {
            String string = "recursive call to sendBlockUpdated";
            Util.logAndPauseIfInIde("recursive call to sendBlockUpdated", new IllegalStateException("recursive call to sendBlockUpdated"));
        }

        this.getChunkSource().blockChanged(pos);
        this.pathTypesByPosCache.invalidate(pos);
        VoxelShape voxelShape = oldState.getCollisionShape(this, pos);
        VoxelShape voxelShape2 = newState.getCollisionShape(this, pos);
        if (Shapes.joinIsNotEmpty(voxelShape, voxelShape2, BooleanOp.NOT_SAME)) {
            List<PathNavigation> list = new ObjectArrayList<>();

            for (Mob mob : this.navigatingMobs) {
                PathNavigation pathNavigation = mob.getNavigation();
                if (pathNavigation.shouldRecomputePath(pos)) {
                    list.add(pathNavigation);
                }
            }

            try {
                this.isUpdatingNavigations = true;

                for (PathNavigation pathNavigation2 : list) {
                    pathNavigation2.recomputePath();
                }
            } finally {
                this.isUpdatingNavigations = false;
            }
        }
    }

    @Override
    public void updateNeighborsAt(BlockPos pos, Block block) {
        this.neighborUpdater.updateNeighborsAtExceptFromFacing(pos, block, null);
    }

    @Override
    public void updateNeighborsAtExceptFromFacing(BlockPos pos, Block blockType, Direction skipSide) {
        this.neighborUpdater.updateNeighborsAtExceptFromFacing(pos, blockType, skipSide);
    }

    @Override
    public void neighborChanged(BlockPos pos, Block block, BlockPos fromPos) {
        this.neighborUpdater.neighborChanged(pos, block, fromPos);
    }

    @Override
    public void neighborChanged(BlockState state, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        this.neighborUpdater.neighborChanged(state, pos, block, fromPos, isMoving);
    }

    /**
     * Sends a {@link net.minecraft.network.protocol.game.ClientboundEntityEventPacket} to all tracked players of that entity.
     */
    @Override
    public void broadcastEntityEvent(Entity entity, byte state) {
        this.getChunkSource().broadcastAndSend(entity, new ClientboundEntityEventPacket(entity, state));
    }

    @Override
    public void broadcastDamageEvent(Entity entity, DamageSource damageSource) {
        this.getChunkSource().broadcastAndSend(entity, new ClientboundDamageEventPacket(entity, damageSource));
    }

    public ServerChunkCache getChunkSource() {
        return this.chunkSource;
    }

    @Override
    public Explosion explode(
        @Nullable Entity source,
        @Nullable DamageSource damageSource,
        @Nullable ExplosionDamageCalculator damageCalculator,
        double x,
        double y,
        double z,
        float radius,
        boolean fire,
        Level.ExplosionInteraction explosionInteraction,
        ParticleOptions smallExplosionParticles,
        ParticleOptions largeExplosionParticles,
        Holder<SoundEvent> explosionSound
    ) {
        Explosion explosion = this.explode(
            source,
            damageSource,
            damageCalculator,
            x,
            y,
            z,
            radius,
            fire,
            explosionInteraction,
            false,
            smallExplosionParticles,
            largeExplosionParticles,
            explosionSound
        );
        if (!explosion.interactsWithBlocks()) {
            explosion.clearToBlow();
        }

        for (ServerPlayer serverPlayer : this.players) {
            if (serverPlayer.distanceToSqr(x, y, z) < 4096.0) {
                serverPlayer.connection
                    .send(
                        new ClientboundExplodePacket(
                            x,
                            y,
                            z,
                            radius,
                            explosion.getToBlow(),
                            explosion.getHitPlayers().get(serverPlayer),
                            explosion.getBlockInteraction(),
                            explosion.getSmallExplosionParticles(),
                            explosion.getLargeExplosionParticles(),
                            explosion.getExplosionSound()
                        )
                    );
            }
        }

        return explosion;
    }

    @Override
    public void blockEvent(BlockPos pos, Block block, int eventID, int eventParam) {
        this.blockEvents.add(new BlockEventData(pos, block, eventID, eventParam));
    }

    private void runBlockEvents() {
        this.blockEventsToReschedule.clear();

        while (!this.blockEvents.isEmpty()) {
            BlockEventData blockEventData = this.blockEvents.removeFirst();
            if (this.shouldTickBlocksAt(blockEventData.pos())) {
                if (this.doBlockEvent(blockEventData)) {
                    this.server
                        .getPlayerList()
                        .broadcast(
                            null,
                            blockEventData.pos().getX(),
                            blockEventData.pos().getY(),
                            blockEventData.pos().getZ(),
                            64.0,
                            this.dimension(),
                            new ClientboundBlockEventPacket(blockEventData.pos(), blockEventData.block(), blockEventData.paramA(), blockEventData.paramB())
                        );
                }
            } else {
                this.blockEventsToReschedule.add(blockEventData);
            }
        }

        this.blockEvents.addAll(this.blockEventsToReschedule);
    }

    private boolean doBlockEvent(BlockEventData event) {
        BlockState blockState = this.getBlockState(event.pos());
        return blockState.is(event.block()) ? blockState.triggerEvent(this, event.pos(), event.paramA(), event.paramB()) : false;
    }

    public LevelTicks<Block> getBlockTicks() {
        return this.blockTicks;
    }

    public LevelTicks<Fluid> getFluidTicks() {
        return this.fluidTicks;
    }

    @Nonnull
    @Override
    public MinecraftServer getServer() {
        return this.server;
    }

    public PortalForcer getPortalForcer() {
        return this.portalForcer;
    }

    public StructureTemplateManager getStructureManager() {
        return this.server.getStructureManager();
    }

    public <T extends ParticleOptions> int sendParticles(
        T type, double posX, double posY, double posZ, int particleCount, double xOffset, double yOffset, double zOffset, double speed
    ) {
        ClientboundLevelParticlesPacket clientboundLevelParticlesPacket = new ClientboundLevelParticlesPacket(
            type, false, posX, posY, posZ, (float)xOffset, (float)yOffset, (float)zOffset, (float)speed, particleCount
        );
        int i = 0;

        for (int j = 0; j < this.players.size(); j++) {
            ServerPlayer serverPlayer = this.players.get(j);
            if (this.sendParticles(serverPlayer, false, posX, posY, posZ, clientboundLevelParticlesPacket)) {
                i++;
            }
        }

        return i;
    }

    public <T extends ParticleOptions> boolean sendParticles(
        ServerPlayer player,
        T type,
        boolean longDistance,
        double posX,
        double posY,
        double posZ,
        int particleCount,
        double xOffset,
        double yOffset,
        double zOffset,
        double speed
    ) {
        Packet<?> packet = new ClientboundLevelParticlesPacket(
            type, longDistance, posX, posY, posZ, (float)xOffset, (float)yOffset, (float)zOffset, (float)speed, particleCount
        );
        return this.sendParticles(player, longDistance, posX, posY, posZ, packet);
    }

    private boolean sendParticles(ServerPlayer player, boolean longDistance, double posX, double posY, double posZ, Packet<?> packet) {
        if (player.level() != this) {
            return false;
        } else {
            BlockPos blockPos = player.blockPosition();
            if (blockPos.closerToCenterThan(new Vec3(posX, posY, posZ), longDistance ? 512.0 : 32.0)) {
                player.connection.send(packet);
                return true;
            } else {
                return false;
            }
        }
    }

    /**
     * Returns the Entity with the given ID, or null if it doesn't exist in this World.
     */
    @Nullable
    @Override
    public Entity getEntity(int id) {
        return this.getEntities().get(id);
    }

    @Deprecated
    @Nullable
    public Entity getEntityOrPart(int id) {
        Entity entity = this.getEntities().get(id);
        return entity != null ? entity : this.dragonParts.get(id);
    }

    @Nullable
    public Entity getEntity(UUID uniqueId) {
        return this.getEntities().get(uniqueId);
    }

    @Nullable
    public BlockPos findNearestMapStructure(TagKey<Structure> structureTag, BlockPos pos, int radius, boolean skipExistingChunks) {
        if (!this.server.getWorldData().worldGenOptions().generateStructures()) {
            return null;
        }

        Optional<HolderSet.Named<Structure>> optional = this.registryAccess().registryOrThrow(Registries.STRUCTURE).getTag(structureTag);
        if (optional.isEmpty()) {
            return null;
        }

        Pair<BlockPos, Holder<Structure>> pair = this.getChunkSource()
            .getGenerator()
            .findNearestMapStructure(this, optional.get(), pos, radius, skipExistingChunks);
        return pair != null ? pair.getFirst() : null;
    }

    @Nullable
    public Pair<BlockPos, Holder<Biome>> findClosestBiome3d(
        Predicate<Holder<Biome>> biomePredicate, BlockPos pos, int radius, int horizontalStep, int verticalStep
    ) {
        return this.getChunkSource()
            .getGenerator()
            .getBiomeSource()
            .findClosestBiome3d(pos, radius, horizontalStep, verticalStep, biomePredicate, this.getChunkSource().randomState().sampler(), this);
    }

    @Override
    public RecipeManager getRecipeManager() {
        return this.server.getRecipeManager();
    }

    @Override
    public TickRateManager tickRateManager() {
        return this.server.tickRateManager();
    }

    @Override
    public boolean noSave() {
        return this.noSave;
    }

    public DimensionDataStorage getDataStorage() {
        return this.getChunkSource().getDataStorage();
    }

    @Nullable
    @Override
    public MapItemSavedData getMapData(MapId mapId) {
        return this.getServer().overworld().getDataStorage().get(MapItemSavedData.factory(), mapId.key());
    }

    @Override
    public void setMapData(MapId mapId, MapItemSavedData mapData) {
        this.getServer().overworld().getDataStorage().set(mapId.key(), mapData);
    }

    @Override
    public MapId getFreeMapId() {
        return this.getServer().overworld().getDataStorage().computeIfAbsent(MapIndex.factory(), "idcounts").getFreeAuxValueForMap();
    }

    public void setDefaultSpawnPos(BlockPos pos, float angle) {
        BlockPos blockPos = this.levelData.getSpawnPos();
        float f = this.levelData.getSpawnAngle();
        if (!blockPos.equals(pos) || f != angle) {
            this.levelData.setSpawn(pos, angle);
            this.getServer().getPlayerList().broadcastAll(new ClientboundSetDefaultSpawnPositionPacket(pos, angle));
        }

        if (this.lastSpawnChunkRadius > 1) {
            this.getChunkSource().removeRegionTicket(TicketType.START, new ChunkPos(blockPos), this.lastSpawnChunkRadius, Unit.INSTANCE);
        }

        int i = this.getGameRules().getInt(GameRules.RULE_SPAWN_CHUNK_RADIUS) + 1;
        if (i > 1) {
            this.getChunkSource().addRegionTicket(TicketType.START, new ChunkPos(pos), i, Unit.INSTANCE);
        }

        this.lastSpawnChunkRadius = i;
    }

    public LongSet getForcedChunks() {
        ForcedChunksSavedData forcedChunksSavedData = this.getDataStorage().get(ForcedChunksSavedData.factory(), "chunks");
        return forcedChunksSavedData != null ? LongSets.unmodifiable(forcedChunksSavedData.getChunks()) : LongSets.EMPTY_SET;
    }

    public boolean setChunkForced(int chunkX, int chunkZ, boolean add) {
        ForcedChunksSavedData forcedChunksSavedData = this.getDataStorage().computeIfAbsent(ForcedChunksSavedData.factory(), "chunks");
        ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);
        long l = chunkPos.toLong();
        boolean bl;
        if (add) {
            bl = forcedChunksSavedData.getChunks().add(l);
            if (bl) {
                this.getChunk(chunkX, chunkZ);
            }
        } else {
            bl = forcedChunksSavedData.getChunks().remove(l);
        }

        forcedChunksSavedData.setDirty(bl);
        if (bl) {
            this.getChunkSource().updateChunkForced(chunkPos, add);
        }

        return bl;
    }

    @Override
    public List<ServerPlayer> players() {
        return this.players;
    }

    @Override
    public void onBlockStateChange(BlockPos pos, BlockState blockState, BlockState newState) {
        Optional<Holder<PoiType>> optional = PoiTypes.forState(blockState);
        Optional<Holder<PoiType>> optional2 = PoiTypes.forState(newState);
        if (!Objects.equals(optional, optional2)) {
            BlockPos blockPos = pos.immutable();
            optional.ifPresent(holder -> this.getServer().execute(() -> {
                this.getPoiManager().remove(blockPos);
                DebugPackets.sendPoiRemovedPacket(this, blockPos);
            }));
            optional2.ifPresent(holder -> this.getServer().execute(() -> {
                this.getPoiManager().add(blockPos, (Holder<PoiType>)holder);
                DebugPackets.sendPoiAddedPacket(this, blockPos);
            }));
        }
    }

    public PoiManager getPoiManager() {
        return this.getChunkSource().getPoiManager();
    }

    public boolean isVillage(BlockPos pos) {
        return this.isCloseToVillage(pos, 1);
    }

    public boolean isVillage(SectionPos pos) {
        return this.isVillage(pos.center());
    }

    public boolean isCloseToVillage(BlockPos pos, int sections) {
        return sections > 6 ? false : this.sectionsToVillage(SectionPos.of(pos)) <= sections;
    }

    public int sectionsToVillage(SectionPos pos) {
        return this.getPoiManager().sectionsToVillage(pos);
    }

    public Raids getRaids() {
        return this.raids;
    }

    @Nullable
    public Raid getRaidAt(BlockPos pos) {
        return this.raids.getNearbyRaid(pos, 9216);
    }

    public boolean isRaided(BlockPos pos) {
        return this.getRaidAt(pos) != null;
    }

    public void onReputationEvent(ReputationEventType type, Entity target, ReputationEventHandler host) {
        host.onReputationEventFrom(type, target);
    }

    public void saveDebugReport(Path path) throws IOException {
        ChunkMap chunkMap = this.getChunkSource().chunkMap;

        try (Writer writer = Files.newBufferedWriter(path.resolve("stats.txt"))) {
            writer.write(String.format(Locale.ROOT, "spawning_chunks: %d\n", chunkMap.getDistanceManager().getNaturalSpawnChunkCount()));
            NaturalSpawner.SpawnState spawnState = this.getChunkSource().getLastSpawnState();
            if (spawnState != null) {
                for (Entry<MobCategory> entry : spawnState.getMobCategoryCounts().object2IntEntrySet()) {
                    writer.write(String.format(Locale.ROOT, "spawn_count.%s: %d\n", entry.getKey().getName(), entry.getIntValue()));
                }
            }

            writer.write(String.format(Locale.ROOT, "entities: %s\n", this.entityManager.gatherStats()));
            writer.write(String.format(Locale.ROOT, "block_entity_tickers: %d\n", this.blockEntityTickers.size()));
            writer.write(String.format(Locale.ROOT, "block_ticks: %d\n", this.getBlockTicks().count()));
            writer.write(String.format(Locale.ROOT, "fluid_ticks: %d\n", this.getFluidTicks().count()));
            writer.write("distance_manager: " + chunkMap.getDistanceManager().getDebugStatus() + "\n");
            writer.write(String.format(Locale.ROOT, "pending_tasks: %d\n", this.getChunkSource().getPendingTasksCount()));
        }

        CrashReport crashReport = new CrashReport("Level dump", new Exception("dummy"));
        this.fillReportDetails(crashReport);

        try (Writer writer2 = Files.newBufferedWriter(path.resolve("example_crash.txt"))) {
            writer2.write(crashReport.getFriendlyReport(ReportType.TEST));
        }

        Path path2 = path.resolve("chunks.csv");

        try (Writer writer3 = Files.newBufferedWriter(path2)) {
            chunkMap.dumpChunks(writer3);
        }

        Path path3 = path.resolve("entity_chunks.csv");

        try (Writer writer4 = Files.newBufferedWriter(path3)) {
            this.entityManager.dumpSections(writer4);
        }

        Path path4 = path.resolve("entities.csv");

        try (Writer writer5 = Files.newBufferedWriter(path4)) {
            dumpEntities(writer5, this.getEntities().getAll());
        }

        Path path5 = path.resolve("block_entities.csv");

        try (Writer writer6 = Files.newBufferedWriter(path5)) {
            this.dumpBlockEntityTickers(writer6);
        }
    }

    private static void dumpEntities(Writer writer, Iterable<Entity> entities) throws IOException {
        CsvOutput csvOutput = CsvOutput.builder()
            .addColumn("x")
            .addColumn("y")
            .addColumn("z")
            .addColumn("uuid")
            .addColumn("type")
            .addColumn("alive")
            .addColumn("display_name")
            .addColumn("custom_name")
            .build(writer);

        for (Entity entity : entities) {
            Component component = entity.getCustomName();
            Component component2 = entity.getDisplayName();
            csvOutput.writeRow(
                entity.getX(),
                entity.getY(),
                entity.getZ(),
                entity.getUUID(),
                BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()),
                entity.isAlive(),
                component2.getString(),
                component != null ? component.getString() : null
            );
        }
    }

    private void dumpBlockEntityTickers(Writer output) throws IOException {
        CsvOutput csvOutput = CsvOutput.builder().addColumn("x").addColumn("y").addColumn("z").addColumn("type").build(output);

        for (TickingBlockEntity tickingBlockEntity : this.blockEntityTickers) {
            BlockPos blockPos = tickingBlockEntity.getPos();
            csvOutput.writeRow(blockPos.getX(), blockPos.getY(), blockPos.getZ(), tickingBlockEntity.getType());
        }
    }

    @VisibleForTesting
    public void clearBlockEvents(BoundingBox boundingBox) {
        this.blockEvents.removeIf(event -> boundingBox.isInside(event.pos()));
    }

    @Override
    public void blockUpdated(BlockPos pos, Block block) {
        if (!this.isDebug()) {
            this.updateNeighborsAt(pos, block);
        }
    }

    @Override
    public float getShade(Direction direction, boolean shade) {
        return 1.0F;
    }

    public Iterable<Entity> getAllEntities() {
        return this.getEntities().getAll();
    }

    @Override
    public String toString() {
        return "ServerLevel[" + this.serverLevelData.getLevelName() + "]";
    }

    public boolean isFlat() {
        return this.server.getWorldData().isFlatWorld();
    }

    @Override
    public long getSeed() {
        return this.server.getWorldData().worldGenOptions().seed();
    }

    @Nullable
    public EndDragonFight getDragonFight() {
        return this.dragonFight;
    }

    @Override
    public ServerLevel getLevel() {
        return this;
    }

    @VisibleForTesting
    public String getWatchdogStats() {
        return String.format(
            Locale.ROOT,
            "players: %s, entities: %s [%s], block_entities: %d [%s], block_ticks: %d, fluid_ticks: %d, chunk_source: %s",
            this.players.size(),
            this.entityManager.gatherStats(),
            getTypeCount(this.entityManager.getEntityGetter().getAll(), entity -> BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString()),
            this.blockEntityTickers.size(),
            getTypeCount(this.blockEntityTickers, TickingBlockEntity::getType),
            this.getBlockTicks().count(),
            this.getFluidTicks().count(),
            this.gatherChunkSourceStats()
        );
    }

    private static <T> String getTypeCount(Iterable<T> objects, Function<T, String> typeGetter) {
        try {
            Object2IntOpenHashMap<String> object2IntOpenHashMap = new Object2IntOpenHashMap<>();

            for (T object : objects) {
                String string = typeGetter.apply(object);
                object2IntOpenHashMap.addTo(string, 1);
            }

            return object2IntOpenHashMap.object2IntEntrySet()
                .stream()
                .sorted(Comparator.comparing(Entry::getIntValue).reversed())
                .limit(5L)
                .map(entry -> entry.getKey() + ":" + entry.getIntValue())
                .collect(Collectors.joining(","));
        } catch (Exception exception) {
            return "";
        }
    }

    @Override
    protected LevelEntityGetter<Entity> getEntities() {
        return this.entityManager.getEntityGetter();
    }

    public void addLegacyChunkEntities(Stream<Entity> entities) {
        this.entityManager.addLegacyChunkEntities(entities);
    }

    public void addWorldGenChunkEntities(Stream<Entity> entities) {
        this.entityManager.addWorldGenChunkEntities(entities);
    }

    public void startTickingChunk(LevelChunk chunk) {
        chunk.unpackTicks(this.getLevelData().getGameTime());
    }

    public void onStructureStartsAvailable(ChunkAccess chunk) {
        this.server.execute(() -> this.structureCheck.onStructureLoad(chunk.getPos(), chunk.getAllStarts()));
    }

    public PathTypeCache getPathTypeCache() {
        return this.pathTypesByPosCache;
    }

    @Override
    public void close() throws IOException {
        super.close();
        this.entityManager.close();
    }

    @Override
    public String gatherChunkSourceStats() {
        return "Chunks[S] W: " + this.chunkSource.gatherStats() + " E: " + this.entityManager.gatherStats();
    }

    public boolean areEntitiesLoaded(long chunkPos) {
        return this.entityManager.areEntitiesLoaded(chunkPos);
    }

    private boolean isPositionTickingWithEntitiesLoaded(long chunkPos) {
        return this.areEntitiesLoaded(chunkPos) && this.chunkSource.isPositionTicking(chunkPos);
    }

    public boolean isPositionEntityTicking(BlockPos pos) {
        return this.entityManager.canPositionTick(pos) && this.chunkSource.chunkMap.getDistanceManager().inEntityTickingRange(ChunkPos.asLong(pos));
    }

    public boolean isNaturalSpawningAllowed(BlockPos pos) {
        return this.entityManager.canPositionTick(pos);
    }

    public boolean isNaturalSpawningAllowed(ChunkPos chunkPos) {
        return this.entityManager.canPositionTick(chunkPos);
    }

    @Override
    public FeatureFlagSet enabledFeatures() {
        return this.server.getWorldData().enabledFeatures();
    }

    @Override
    public PotionBrewing potionBrewing() {
        return this.server.potionBrewing();
    }

    public RandomSource getRandomSequence(ResourceLocation location) {
        return this.randomSequences.get(location);
    }

    public RandomSequences getRandomSequences() {
        return this.randomSequences;
    }

    /**
     * Adds some basic stats of the world to the given crash report.
     */
    @Override
    public CrashReportCategory fillReportDetails(CrashReport report) {
        CrashReportCategory crashReportCategory = super.fillReportDetails(report);
        crashReportCategory.setDetail("Loaded entity count", () -> String.valueOf(this.entityManager.count()));
        return crashReportCategory;
    }

    final class EntityCallbacks implements LevelCallback<Entity> {
        public void onCreated(Entity entity) {
        }

        public void onDestroyed(Entity entity) {
            ServerLevel.this.getScoreboard().entityRemoved(entity);
        }

        public void onTickingStart(Entity entity) {
            ServerLevel.this.entityTickList.add(entity);
        }

        public void onTickingEnd(Entity entity) {
            ServerLevel.this.entityTickList.remove(entity);
        }

        public void onTrackingStart(Entity entity) {
            ServerLevel.this.getChunkSource().addEntity(entity);
            if (entity instanceof ServerPlayer serverPlayer) {
                ServerLevel.this.players.add(serverPlayer);
                ServerLevel.this.updateSleepingPlayerList();
            }

            if (entity instanceof Mob mob) {
                if (ServerLevel.this.isUpdatingNavigations) {
                    String string = "onTrackingStart called during navigation iteration";
                    Util.logAndPauseIfInIde(
                        "onTrackingStart called during navigation iteration", new IllegalStateException("onTrackingStart called during navigation iteration")
                    );
                }

                ServerLevel.this.navigatingMobs.add(mob);
            }

            if (entity instanceof EnderDragon enderDragon) {
                for (EnderDragonPart enderDragonPart : enderDragon.getSubEntities()) {
                    ServerLevel.this.dragonParts.put(enderDragonPart.getId(), enderDragonPart);
                }
            }

            entity.updateDynamicGameEventListener(DynamicGameEventListener::add);
        }

        public void onTrackingEnd(Entity entity) {
            ServerLevel.this.getChunkSource().removeEntity(entity);
            if (entity instanceof ServerPlayer serverPlayer) {
                ServerLevel.this.players.remove(serverPlayer);
                ServerLevel.this.updateSleepingPlayerList();
            }

            if (entity instanceof Mob mob) {
                if (ServerLevel.this.isUpdatingNavigations) {
                    String string = "onTrackingStart called during navigation iteration";
                    Util.logAndPauseIfInIde(
                        "onTrackingStart called during navigation iteration", new IllegalStateException("onTrackingStart called during navigation iteration")
                    );
                }

                ServerLevel.this.navigatingMobs.remove(mob);
            }

            if (entity instanceof EnderDragon enderDragon) {
                for (EnderDragonPart enderDragonPart : enderDragon.getSubEntities()) {
                    ServerLevel.this.dragonParts.remove(enderDragonPart.getId());
                }
            }

            entity.updateDynamicGameEventListener(DynamicGameEventListener::remove);
        }

        public void onSectionChange(Entity entity) {
            entity.updateDynamicGameEventListener(DynamicGameEventListener::move);
        }
    }
}
