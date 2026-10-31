package net.minecraft.client.multiplayer;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Queues;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintCache;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import net.minecraft.client.particle.FireworkParticles;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.resources.sounds.EntityBoundSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Cursor3D;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.CubicSampler;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.Difficulty;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.entity.EntityTickList;
import net.minecraft.world.level.entity.LevelCallback;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.level.entity.TransientEntitySectionManager;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.WritableLevelData;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.ticks.BlackholeTickAccess;
import net.minecraft.world.ticks.LevelTickAccess;
import org.slf4j.Logger;

public class ClientLevel extends Level {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final double FLUID_PARTICLE_SPAWN_OFFSET = 0.05;
    private static final int NORMAL_LIGHT_UPDATES_PER_FRAME = 10;
    private static final int LIGHT_UPDATE_QUEUE_SIZE_THRESHOLD = 1000;
    final EntityTickList tickingEntities = new EntityTickList();
    private final TransientEntitySectionManager<Entity> entityStorage = new TransientEntitySectionManager<>(Entity.class, new ClientLevel.EntityCallbacks());
    private final ClientPacketListener connection;
    private final LevelRenderer levelRenderer;
    private final ClientLevel.ClientLevelData clientLevelData;
    private final DimensionSpecialEffects effects;
    private final TickRateManager tickRateManager;
    private final Minecraft minecraft = Minecraft.getInstance();
    final List<AbstractClientPlayer> players = Lists.newArrayList();
    private final Map<MapId, MapItemSavedData> mapData = Maps.newHashMap();
    private static final long CLOUD_COLOR = 16777215L;
    private int skyFlashTime;
    private final Object2ObjectArrayMap<ColorResolver, BlockTintCache> tintCaches = Util.make(new Object2ObjectArrayMap<>(3), map -> {
        map.put(BiomeColors.GRASS_COLOR_RESOLVER, new BlockTintCache(pos -> this.calculateBlockTint(pos, BiomeColors.GRASS_COLOR_RESOLVER)));
        map.put(BiomeColors.FOLIAGE_COLOR_RESOLVER, new BlockTintCache(pos -> this.calculateBlockTint(pos, BiomeColors.FOLIAGE_COLOR_RESOLVER)));
        map.put(BiomeColors.WATER_COLOR_RESOLVER, new BlockTintCache(pos -> this.calculateBlockTint(pos, BiomeColors.WATER_COLOR_RESOLVER)));
    });
    private final ClientChunkCache chunkSource;
    private final Deque<Runnable> lightUpdateQueue = Queues.newArrayDeque();
    private int serverSimulationDistance;
    private final BlockStatePredictionHandler blockStatePredictionHandler = new BlockStatePredictionHandler();
    private static final Set<Item> MARKER_PARTICLE_ITEMS = Set.of(Items.BARRIER, Items.LIGHT);

    public void handleBlockChangedAck(int sequence) {
        this.blockStatePredictionHandler.endPredictionsUpTo(sequence, this);
    }

    public void setServerVerifiedBlockState(BlockPos pos, BlockState state, int flags) {
        if (!this.blockStatePredictionHandler.updateKnownServerState(pos, state)) {
            super.setBlock(pos, state, flags, 512);
        }
    }

    public void syncBlockState(BlockPos pos, BlockState state, Vec3 playerPos) {
        BlockState blockState = this.getBlockState(pos);
        if (blockState != state) {
            this.setBlock(pos, state, 19);
            Player player = this.minecraft.player;
            if (this == player.level() && player.isColliding(pos, state)) {
                player.absMoveTo(playerPos.x, playerPos.y, playerPos.z);
            }
        }
    }

    BlockStatePredictionHandler getBlockStatePredictionHandler() {
        return this.blockStatePredictionHandler;
    }

    @Override
    public boolean setBlock(BlockPos pos, BlockState state, int flags, int recursionLeft) {
        if (this.blockStatePredictionHandler.isPredicting()) {
            BlockState blockState = this.getBlockState(pos);
            boolean bl = super.setBlock(pos, state, flags, recursionLeft);
            if (bl) {
                this.blockStatePredictionHandler.retainKnownServerState(pos, blockState, this.minecraft.player);
            }

            return bl;
        } else {
            return super.setBlock(pos, state, flags, recursionLeft);
        }
    }

    public ClientLevel(
        ClientPacketListener connection,
        ClientLevel.ClientLevelData clientLevelData,
        ResourceKey<Level> dimension,
        Holder<DimensionType> dimensionType,
        int viewDistance,
        int serverSimulationDistance,
        Supplier<ProfilerFiller> profiler,
        LevelRenderer levelRenderer,
        boolean isDebug,
        long biomeZoomSeed
    ) {
        super(clientLevelData, dimension, connection.registryAccess(), dimensionType, profiler, true, isDebug, biomeZoomSeed, 1000000);
        this.connection = connection;
        this.chunkSource = new ClientChunkCache(this, viewDistance);
        this.tickRateManager = new TickRateManager();
        this.clientLevelData = clientLevelData;
        this.levelRenderer = levelRenderer;
        this.effects = DimensionSpecialEffects.forType(dimensionType.value());
        this.setDefaultSpawnPos(new BlockPos(8, 64, 8), 0.0F);
        this.serverSimulationDistance = serverSimulationDistance;
        this.updateSkyBrightness();
        this.prepareWeather();
    }

    public void queueLightUpdate(Runnable task) {
        this.lightUpdateQueue.add(task);
    }

    public void pollLightUpdates() {
        int i = this.lightUpdateQueue.size();
        int j = i < 1000 ? Math.max(10, i / 10) : i;

        for (int k = 0; k < j; k++) {
            Runnable runnable = this.lightUpdateQueue.poll();
            if (runnable == null) {
                break;
            }

            runnable.run();
        }
    }

    public boolean isLightUpdateQueueEmpty() {
        return this.lightUpdateQueue.isEmpty();
    }

    public DimensionSpecialEffects effects() {
        return this.effects;
    }

    /**
     * Runs a single tick for the world
     */
    public void tick(BooleanSupplier hasTimeLeft) {
        this.getWorldBorder().tick();
        if (this.tickRateManager().runsNormally()) {
            this.tickTime();
        }

        if (this.skyFlashTime > 0) {
            this.setSkyFlashTime(this.skyFlashTime - 1);
        }

        this.getProfiler().push("blocks");
        this.chunkSource.tick(hasTimeLeft, true);
        this.getProfiler().pop();
    }

    private void tickTime() {
        this.setGameTime(this.levelData.getGameTime() + 1L);
        if (this.levelData.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)) {
            this.setDayTime(this.levelData.getDayTime() + 1L);
        }
    }

    public void setGameTime(long time) {
        this.clientLevelData.setGameTime(time);
    }

    /**
     * Sets the world time.
     */
    public void setDayTime(long time) {
        if (time < 0L) {
            time = -time;
            this.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        } else {
            this.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(true, null);
        }

        this.clientLevelData.setDayTime(time);
    }

    public Iterable<Entity> entitiesForRendering() {
        return this.getEntities().getAll();
    }

    public void tickEntities() {
        ProfilerFiller profilerFiller = this.getProfiler();
        profilerFiller.push("entities");
        this.tickingEntities.forEach(entity -> {
            if (!entity.isRemoved() && !entity.isPassenger() && !this.tickRateManager.isEntityFrozen(entity)) {
                this.guardEntityTick(this::tickNonPassenger, entity);
            }
        });
        profilerFiller.pop();
        this.tickBlockEntities();
    }

    @Override
    public boolean shouldTickDeath(Entity entity) {
        return entity.chunkPosition().getChessboardDistance(this.minecraft.player.chunkPosition()) <= this.serverSimulationDistance;
    }

    public void tickNonPassenger(Entity entity) {
        entity.setOldPosAndRot();
        entity.tickCount++;
        this.getProfiler().push(() -> BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
        entity.tick();
        this.getProfiler().pop();

        for (Entity entity2 : entity.getPassengers()) {
            this.tickPassenger(entity, entity2);
        }
    }

    private void tickPassenger(Entity mount, Entity rider) {
        if (rider.isRemoved() || rider.getVehicle() != mount) {
            rider.stopRiding();
        } else if (rider instanceof Player || this.tickingEntities.contains(rider)) {
            rider.setOldPosAndRot();
            rider.tickCount++;
            rider.rideTick();

            for (Entity entity : rider.getPassengers()) {
                this.tickPassenger(rider, entity);
            }
        }
    }

    public void unload(LevelChunk chunk) {
        chunk.clearAllBlockEntities();
        this.chunkSource.getLightEngine().setLightEnabled(chunk.getPos(), false);
        this.entityStorage.stopTicking(chunk.getPos());
    }

    public void onChunkLoaded(ChunkPos chunkPos) {
        this.tintCaches.forEach((resolver, cache) -> cache.invalidateForChunk(chunkPos.x, chunkPos.z));
        this.entityStorage.startTicking(chunkPos);
        this.levelRenderer.onChunkLoaded(chunkPos);
    }

    public void clearTintCaches() {
        this.tintCaches.forEach((resolver, cache) -> cache.invalidateAll());
    }

    @Override
    public boolean hasChunk(int chunkX, int chunkZ) {
        return true;
    }

    public int getEntityCount() {
        return this.entityStorage.count();
    }

    public void addEntity(Entity entity) {
        this.removeEntity(entity.getId(), Entity.RemovalReason.DISCARDED);
        this.entityStorage.addEntity(entity);
    }

    public void removeEntity(int entityId, Entity.RemovalReason reason) {
        Entity entity = this.getEntities().get(entityId);
        if (entity != null) {
            entity.setRemoved(reason);
            entity.onClientRemoval();
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

    @Override
    public void disconnect() {
        this.connection.getConnection().disconnect(Component.translatable("multiplayer.status.quitting"));
    }

    public void animateTick(int posX, int posY, int posZ) {
        int i = 32;
        RandomSource randomSource = RandomSource.create();
        Block block = this.getMarkerParticleTarget();
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

        for (int j = 0; j < 667; j++) {
            this.doAnimateTick(posX, posY, posZ, 16, randomSource, block, mutableBlockPos);
            this.doAnimateTick(posX, posY, posZ, 32, randomSource, block, mutableBlockPos);
        }
    }

    @Nullable
    private Block getMarkerParticleTarget() {
        if (this.minecraft.gameMode.getPlayerMode() == GameType.CREATIVE) {
            ItemStack itemStack = this.minecraft.player.getMainHandItem();
            Item item = itemStack.getItem();
            if (MARKER_PARTICLE_ITEMS.contains(item) && item instanceof BlockItem blockItem) {
                return blockItem.getBlock();
            }
        }

        return null;
    }

    public void doAnimateTick(int posX, int posY, int posZ, int range, RandomSource random, @Nullable Block block, BlockPos.MutableBlockPos blockPos) {
        int i = posX + this.random.nextInt(range) - this.random.nextInt(range);
        int j = posY + this.random.nextInt(range) - this.random.nextInt(range);
        int k = posZ + this.random.nextInt(range) - this.random.nextInt(range);
        blockPos.set(i, j, k);
        BlockState blockState = this.getBlockState(blockPos);
        blockState.getBlock().animateTick(blockState, this, blockPos, random);
        FluidState fluidState = this.getFluidState(blockPos);
        if (!fluidState.isEmpty()) {
            fluidState.animateTick(this, blockPos, random);
            ParticleOptions particleOptions = fluidState.getDripParticle();
            if (particleOptions != null && this.random.nextInt(10) == 0) {
                boolean bl = blockState.isFaceSturdy(this, blockPos, Direction.DOWN);
                BlockPos blockPos2 = blockPos.below();
                this.trySpawnDripParticles(blockPos2, this.getBlockState(blockPos2), particleOptions, bl);
            }
        }

        if (block == blockState.getBlock()) {
            this.addParticle(new BlockParticleOption(ParticleTypes.BLOCK_MARKER, blockState), i + 0.5, j + 0.5, k + 0.5, 0.0, 0.0, 0.0);
        }

        if (!blockState.isCollisionShapeFullBlock(this, blockPos)) {
            this.getBiome(blockPos)
                .value()
                .getAmbientParticle()
                .ifPresent(
                    particleSettings -> {
                        if (particleSettings.canSpawn(this.random)) {
                            this.addParticle(
                                particleSettings.getOptions(),
                                blockPos.getX() + this.random.nextDouble(),
                                blockPos.getY() + this.random.nextDouble(),
                                blockPos.getZ() + this.random.nextDouble(),
                                0.0,
                                0.0,
                                0.0
                            );
                        }
                    }
                );
        }
    }

    private void trySpawnDripParticles(BlockPos blockPos, BlockState blockState, ParticleOptions particleData, boolean shapeDownSolid) {
        if (blockState.getFluidState().isEmpty()) {
            VoxelShape voxelShape = blockState.getCollisionShape(this, blockPos);
            double d = voxelShape.max(Direction.Axis.Y);
            if (d < 1.0) {
                if (shapeDownSolid) {
                    this.spawnFluidParticle(
                        blockPos.getX(), blockPos.getX() + 1, blockPos.getZ(), blockPos.getZ() + 1, blockPos.getY() + 1 - 0.05, particleData
                    );
                }
            } else if (!blockState.is(BlockTags.IMPERMEABLE)) {
                double e = voxelShape.min(Direction.Axis.Y);
                if (e > 0.0) {
                    this.spawnParticle(blockPos, particleData, voxelShape, blockPos.getY() + e - 0.05);
                } else {
                    BlockPos blockPos2 = blockPos.below();
                    BlockState blockState2 = this.getBlockState(blockPos2);
                    VoxelShape voxelShape2 = blockState2.getCollisionShape(this, blockPos2);
                    double f = voxelShape2.max(Direction.Axis.Y);
                    if (f < 1.0 && blockState2.getFluidState().isEmpty()) {
                        this.spawnParticle(blockPos, particleData, voxelShape, blockPos.getY() - 0.05);
                    }
                }
            }
        }
    }

    private void spawnParticle(BlockPos pos, ParticleOptions particleData, VoxelShape voxelShape, double y) {
        this.spawnFluidParticle(
            pos.getX() + voxelShape.min(Direction.Axis.X),
            pos.getX() + voxelShape.max(Direction.Axis.X),
            pos.getZ() + voxelShape.min(Direction.Axis.Z),
            pos.getZ() + voxelShape.max(Direction.Axis.Z),
            y,
            particleData
        );
    }

    private void spawnFluidParticle(double xStart, double xEnd, double zStart, double zEnd, double y, ParticleOptions particleData) {
        this.addParticle(particleData, Mth.lerp(this.random.nextDouble(), xStart, xEnd), y, Mth.lerp(this.random.nextDouble(), zStart, zEnd), 0.0, 0.0, 0.0);
    }

    /**
     * Adds some basic stats of the world to the given crash report.
     */
    @Override
    public CrashReportCategory fillReportDetails(CrashReport report) {
        CrashReportCategory crashReportCategory = super.fillReportDetails(report);
        crashReportCategory.setDetail("Server brand", () -> this.minecraft.player.connection.serverBrand());
        crashReportCategory.setDetail(
            "Server type", () -> this.minecraft.getSingleplayerServer() == null ? "Non-integrated multiplayer server" : "Integrated singleplayer server"
        );
        crashReportCategory.setDetail("Tracked entity count", () -> String.valueOf(this.getEntityCount()));
        return crashReportCategory;
    }

    @Override
    public void playSeededSound(
        @Nullable Player player, double x, double y, double z, Holder<SoundEvent> sound, SoundSource category, float volume, float pitch, long seed
    ) {
        if (player == this.minecraft.player) {
            this.playSound(x, y, z, sound.value(), category, volume, pitch, false, seed);
        }
    }

    @Override
    public void playSeededSound(@Nullable Player player, Entity entity, Holder<SoundEvent> sound, SoundSource category, float volume, float pitch, long seed) {
        if (player == this.minecraft.player) {
            this.minecraft.getSoundManager().play(new EntityBoundSoundInstance(sound.value(), category, volume, pitch, entity, seed));
        }
    }

    @Override
    public void playLocalSound(Entity entity, SoundEvent sound, SoundSource category, float volume, float pitch) {
        this.minecraft.getSoundManager().play(new EntityBoundSoundInstance(sound, category, volume, pitch, entity, this.random.nextLong()));
    }

    @Override
    public void playLocalSound(double x, double y, double z, SoundEvent sound, SoundSource category, float volume, float pitch, boolean distanceDelay) {
        this.playSound(x, y, z, sound, category, volume, pitch, distanceDelay, this.random.nextLong());
    }

    private void playSound(double x, double y, double z, SoundEvent soundEvent, SoundSource source, float volume, float pitch, boolean distanceDelay, long seed) {
        double d = this.minecraft.gameRenderer.getMainCamera().getPosition().distanceToSqr(x, y, z);
        SimpleSoundInstance simpleSoundInstance = new SimpleSoundInstance(soundEvent, source, volume, pitch, RandomSource.create(seed), x, y, z);
        if (distanceDelay && d > 100.0) {
            double e = Math.sqrt(d) / 40.0;
            this.minecraft.getSoundManager().playDelayed(simpleSoundInstance, (int)(e * 20.0));
        } else {
            this.minecraft.getSoundManager().play(simpleSoundInstance);
        }
    }

    @Override
    public void createFireworks(double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, List<FireworkExplosion> explosions) {
        if (explosions.isEmpty()) {
            for (int i = 0; i < this.random.nextInt(3) + 2; i++) {
                this.addParticle(ParticleTypes.POOF, x, y, z, this.random.nextGaussian() * 0.05, 0.005, this.random.nextGaussian() * 0.05);
            }
        } else {
            this.minecraft.particleEngine.add(new FireworkParticles.Starter(this, x, y, z, xSpeed, ySpeed, zSpeed, this.minecraft.particleEngine, explosions));
        }
    }

    @Override
    public void sendPacketToServer(Packet<?> packet) {
        this.connection.send(packet);
    }

    @Override
    public RecipeManager getRecipeManager() {
        return this.connection.getRecipeManager();
    }

    @Override
    public TickRateManager tickRateManager() {
        return this.tickRateManager;
    }

    @Override
    public LevelTickAccess<Block> getBlockTicks() {
        return BlackholeTickAccess.emptyLevelList();
    }

    @Override
    public LevelTickAccess<Fluid> getFluidTicks() {
        return BlackholeTickAccess.emptyLevelList();
    }

    public ClientChunkCache getChunkSource() {
        return this.chunkSource;
    }

    @Nullable
    @Override
    public MapItemSavedData getMapData(MapId mapId) {
        return this.mapData.get(mapId);
    }

    public void overrideMapData(MapId mapId, MapItemSavedData mapData) {
        this.mapData.put(mapId, mapData);
    }

    @Override
    public void setMapData(MapId mapId, MapItemSavedData mapData) {
    }

    @Override
    public MapId getFreeMapId() {
        return new MapId(0);
    }

    @Override
    public Scoreboard getScoreboard() {
        return this.connection.scoreboard();
    }

    /**
     * Flags are as in setBlockState
     */
    @Override
    public void sendBlockUpdated(BlockPos pos, BlockState oldState, BlockState newState, int flags) {
        this.levelRenderer.blockChanged(this, pos, oldState, newState, flags);
    }

    @Override
    public void setBlocksDirty(BlockPos blockPos, BlockState oldState, BlockState newState) {
        this.levelRenderer.setBlockDirty(blockPos, oldState, newState);
    }

    public void setSectionDirtyWithNeighbors(int sectionX, int sectionY, int sectionZ) {
        this.levelRenderer.setSectionDirtyWithNeighbors(sectionX, sectionY, sectionZ);
    }

    @Override
    public void destroyBlockProgress(int breakerId, BlockPos pos, int progress) {
        this.levelRenderer.destroyBlockProgress(breakerId, pos, progress);
    }

    @Override
    public void globalLevelEvent(int id, BlockPos pos, int data) {
        this.levelRenderer.globalLevelEvent(id, pos, data);
    }

    @Override
    public void levelEvent(@Nullable Player player, int type, BlockPos pos, int data) {
        try {
            this.levelRenderer.levelEvent(type, pos, data);
        } catch (Throwable throwable) {
            CrashReport crashReport = CrashReport.forThrowable(throwable, "Playing level event");
            CrashReportCategory crashReportCategory = crashReport.addCategory("Level event being played");
            crashReportCategory.setDetail("Block coordinates", CrashReportCategory.formatLocation(this, pos));
            crashReportCategory.setDetail("Event source", player);
            crashReportCategory.setDetail("Event type", type);
            crashReportCategory.setDetail("Event data", data);
            throw new ReportedException(crashReport);
        }
    }

    @Override
    public void addParticle(ParticleOptions particleData, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
        this.levelRenderer.addParticle(particleData, particleData.getType().getOverrideLimiter(), x, y, z, xSpeed, ySpeed, zSpeed);
    }

    @Override
    public void addParticle(ParticleOptions particleData, boolean forceAlwaysRender, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
        this.levelRenderer.addParticle(particleData, particleData.getType().getOverrideLimiter() || forceAlwaysRender, x, y, z, xSpeed, ySpeed, zSpeed);
    }

    @Override
    public void addAlwaysVisibleParticle(ParticleOptions particleData, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
        this.levelRenderer.addParticle(particleData, false, true, x, y, z, xSpeed, ySpeed, zSpeed);
    }

    @Override
    public void addAlwaysVisibleParticle(
        ParticleOptions particleData, boolean ignoreRange, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed
    ) {
        this.levelRenderer.addParticle(particleData, particleData.getType().getOverrideLimiter() || ignoreRange, true, x, y, z, xSpeed, ySpeed, zSpeed);
    }

    @Override
    public List<AbstractClientPlayer> players() {
        return this.players;
    }

    @Override
    public Holder<Biome> getUncachedNoiseBiome(int x, int y, int z) {
        return this.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.PLAINS);
    }

    public float getSkyDarken(float partialTick) {
        float f = this.getTimeOfDay(partialTick);
        float g = 1.0F - (Mth.cos(f * (float) (Math.PI * 2)) * 2.0F + 0.2F);
        g = Mth.clamp(g, 0.0F, 1.0F);
        g = 1.0F - g;
        g *= 1.0F - this.getRainLevel(partialTick) * 5.0F / 16.0F;
        g *= 1.0F - this.getThunderLevel(partialTick) * 5.0F / 16.0F;
        return g * 0.8F + 0.2F;
    }

    public Vec3 getSkyColor(Vec3 pos, float partialTick) {
        float f = this.getTimeOfDay(partialTick);
        Vec3 vec3 = pos.subtract(2.0, 2.0, 2.0).scale(0.25);
        BiomeManager biomeManager = this.getBiomeManager();
        Vec3 vec32 = CubicSampler.gaussianSampleVec3(vec3, (x, y, z) -> Vec3.fromRGB24(biomeManager.getNoiseBiomeAtQuart(x, y, z).value().getSkyColor()));
        float g = Mth.cos(f * (float) (Math.PI * 2)) * 2.0F + 0.5F;
        g = Mth.clamp(g, 0.0F, 1.0F);
        float h = (float)vec32.x * g;
        float i = (float)vec32.y * g;
        float j = (float)vec32.z * g;
        float k = this.getRainLevel(partialTick);
        if (k > 0.0F) {
            float l = (h * 0.3F + i * 0.59F + j * 0.11F) * 0.6F;
            float m = 1.0F - k * 0.75F;
            h = h * m + l * (1.0F - m);
            i = i * m + l * (1.0F - m);
            j = j * m + l * (1.0F - m);
        }

        float l = this.getThunderLevel(partialTick);
        if (l > 0.0F) {
            float m = (h * 0.3F + i * 0.59F + j * 0.11F) * 0.2F;
            float n = 1.0F - l * 0.75F;
            h = h * n + m * (1.0F - n);
            i = i * n + m * (1.0F - n);
            j = j * n + m * (1.0F - n);
        }

        int o = this.getSkyFlashTime();
        if (o > 0) {
            float n = o - partialTick;
            if (n > 1.0F) {
                n = 1.0F;
            }

            n *= 0.45F;
            h = h * (1.0F - n) + 0.8F * n;
            i = i * (1.0F - n) + 0.8F * n;
            j = j * (1.0F - n) + 1.0F * n;
        }

        return new Vec3(h, i, j);
    }

    public Vec3 getCloudColor(float partialTick) {
        float f = this.getTimeOfDay(partialTick);
        float g = Mth.cos(f * (float) (Math.PI * 2)) * 2.0F + 0.5F;
        g = Mth.clamp(g, 0.0F, 1.0F);
        float h = 1.0F;
        float i = 1.0F;
        float j = 1.0F;
        float k = this.getRainLevel(partialTick);
        if (k > 0.0F) {
            float l = (h * 0.3F + i * 0.59F + j * 0.11F) * 0.6F;
            float m = 1.0F - k * 0.95F;
            h = h * m + l * (1.0F - m);
            i = i * m + l * (1.0F - m);
            j = j * m + l * (1.0F - m);
        }

        h *= g * 0.9F + 0.1F;
        i *= g * 0.9F + 0.1F;
        j *= g * 0.85F + 0.15F;
        float l = this.getThunderLevel(partialTick);
        if (l > 0.0F) {
            float m = (h * 0.3F + i * 0.59F + j * 0.11F) * 0.2F;
            float n = 1.0F - l * 0.95F;
            h = h * n + m * (1.0F - n);
            i = i * n + m * (1.0F - n);
            j = j * n + m * (1.0F - n);
        }

        return new Vec3(h, i, j);
    }

    public float getStarBrightness(float partialTick) {
        float f = this.getTimeOfDay(partialTick);
        float g = 1.0F - (Mth.cos(f * (float) (Math.PI * 2)) * 2.0F + 0.25F);
        g = Mth.clamp(g, 0.0F, 1.0F);
        return g * g * 0.5F;
    }

    public int getSkyFlashTime() {
        return this.minecraft.options.hideLightningFlash().get() ? 0 : this.skyFlashTime;
    }

    @Override
    public void setSkyFlashTime(int timeFlash) {
        this.skyFlashTime = timeFlash;
    }

    @Override
    public float getShade(Direction direction, boolean shade) {
        boolean bl = this.effects().constantAmbientLight();
        if (!shade) {
            return bl ? 0.9F : 1.0F;
        }

        switch (direction) {
            case DOWN:
                return bl ? 0.9F : 0.5F;
            case UP:
                return bl ? 0.9F : 1.0F;
            case NORTH:
            case SOUTH:
                return 0.8F;
            case WEST:
            case EAST:
                return 0.6F;
            default:
                return 1.0F;
        }
    }

    @Override
    public int getBlockTint(BlockPos blockPos, ColorResolver colorResolver) {
        BlockTintCache blockTintCache = this.tintCaches.get(colorResolver);
        return blockTintCache.getColor(blockPos);
    }

    public int calculateBlockTint(BlockPos blockPos, ColorResolver colorResolver) {
        int i = Minecraft.getInstance().options.biomeBlendRadius().get();
        if (i == 0) {
            return colorResolver.getColor(this.getBiome(blockPos).value(), blockPos.getX(), blockPos.getZ());
        }

        int j = (i * 2 + 1) * (i * 2 + 1);
        int k = 0;
        int l = 0;
        int m = 0;
        Cursor3D cursor3D = new Cursor3D(blockPos.getX() - i, blockPos.getY(), blockPos.getZ() - i, blockPos.getX() + i, blockPos.getY(), blockPos.getZ() + i);
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

        while (cursor3D.advance()) {
            mutableBlockPos.set(cursor3D.nextX(), cursor3D.nextY(), cursor3D.nextZ());
            int n = colorResolver.getColor(this.getBiome(mutableBlockPos).value(), mutableBlockPos.getX(), mutableBlockPos.getZ());
            k += (n & 0xFF0000) >> 16;
            l += (n & 0xFF00) >> 8;
            m += n & 0xFF;
        }

        return (k / j & 0xFF) << 16 | (l / j & 0xFF) << 8 | m / j & 0xFF;
    }

    public void setDefaultSpawnPos(BlockPos spawnPos, float spawnAngle) {
        this.levelData.setSpawn(spawnPos, spawnAngle);
    }

    @Override
    public String toString() {
        return "ClientLevel";
    }

    public ClientLevel.ClientLevelData getLevelData() {
        return this.clientLevelData;
    }

    @Override
    public void gameEvent(Holder<GameEvent> gameEvent, Vec3 pos, GameEvent.Context context) {
    }

    protected Map<MapId, MapItemSavedData> getAllMapData() {
        return ImmutableMap.copyOf(this.mapData);
    }

    protected void addMapData(Map<MapId, MapItemSavedData> map) {
        this.mapData.putAll(map);
    }

    @Override
    protected LevelEntityGetter<Entity> getEntities() {
        return this.entityStorage.getEntityGetter();
    }

    @Override
    public String gatherChunkSourceStats() {
        return "Chunks[C] W: " + this.chunkSource.gatherStats() + " E: " + this.entityStorage.gatherStats();
    }

    @Override
    public void addDestroyBlockEffect(BlockPos pos, BlockState state) {
        this.minecraft.particleEngine.destroy(pos, state);
    }

    public void setServerSimulationDistance(int serverSimulationDistance) {
        this.serverSimulationDistance = serverSimulationDistance;
    }

    public int getServerSimulationDistance() {
        return this.serverSimulationDistance;
    }

    @Override
    public FeatureFlagSet enabledFeatures() {
        return this.connection.enabledFeatures();
    }

    @Override
    public PotionBrewing potionBrewing() {
        return this.connection.potionBrewing();
    }

    public static class ClientLevelData implements WritableLevelData {
        private final boolean hardcore;
        private final GameRules gameRules;
        private final boolean isFlat;
        private BlockPos spawnPos;
        private float spawnAngle;
        private long gameTime;
        private long dayTime;
        private boolean raining;
        private Difficulty difficulty;
        private boolean difficultyLocked;

        public ClientLevelData(Difficulty difficulty, boolean hardcore, boolean isFlat) {
            this.difficulty = difficulty;
            this.hardcore = hardcore;
            this.isFlat = isFlat;
            this.gameRules = new GameRules();
        }

        @Override
        public BlockPos getSpawnPos() {
            return this.spawnPos;
        }

        @Override
        public float getSpawnAngle() {
            return this.spawnAngle;
        }

        @Override
        public long getGameTime() {
            return this.gameTime;
        }

        @Override
        public long getDayTime() {
            return this.dayTime;
        }

        public void setGameTime(long gameTime) {
            this.gameTime = gameTime;
        }

        public void setDayTime(long dayTime) {
            this.dayTime = dayTime;
        }

        @Override
        public void setSpawn(BlockPos spawnPoint, float angle) {
            this.spawnPos = spawnPoint.immutable();
            this.spawnAngle = angle;
        }

        @Override
        public boolean isThundering() {
            return false;
        }

        @Override
        public boolean isRaining() {
            return this.raining;
        }

        /**
         * Sets whether it is raining or not.
         */
        @Override
        public void setRaining(boolean isRaining) {
            this.raining = isRaining;
        }

        @Override
        public boolean isHardcore() {
            return this.hardcore;
        }

        @Override
        public GameRules getGameRules() {
            return this.gameRules;
        }

        @Override
        public Difficulty getDifficulty() {
            return this.difficulty;
        }

        @Override
        public boolean isDifficultyLocked() {
            return this.difficultyLocked;
        }

        @Override
        public void fillCrashReportCategory(CrashReportCategory crashReportCategory, LevelHeightAccessor level) {
            WritableLevelData.super.fillCrashReportCategory(crashReportCategory, level);
        }

        public void setDifficulty(Difficulty difficulty) {
            this.difficulty = difficulty;
        }

        public void setDifficultyLocked(boolean difficultyLocked) {
            this.difficultyLocked = difficultyLocked;
        }

        public double getHorizonHeight(LevelHeightAccessor level) {
            return this.isFlat ? level.getMinBuildHeight() : 63.0;
        }

        public float getClearColorScale() {
            return this.isFlat ? 1.0F : 0.03125F;
        }
    }

    final class EntityCallbacks implements LevelCallback<Entity> {
        public void onCreated(Entity entity) {
        }

        public void onDestroyed(Entity entity) {
        }

        public void onTickingStart(Entity entity) {
            ClientLevel.this.tickingEntities.add(entity);
        }

        public void onTickingEnd(Entity entity) {
            ClientLevel.this.tickingEntities.remove(entity);
        }

        public void onTrackingStart(Entity entity) {
            if (entity instanceof AbstractClientPlayer) {
                ClientLevel.this.players.add((AbstractClientPlayer)entity);
            }
        }

        public void onTrackingEnd(Entity entity) {
            entity.unRide();
            ClientLevel.this.players.remove(entity);
        }

        public void onSectionChange(Entity entity) {
        }
    }
}
