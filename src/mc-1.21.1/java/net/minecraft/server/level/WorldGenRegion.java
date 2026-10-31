package net.minecraft.server.level;

import com.mojang.logging.LogUtils;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.level.chunk.status.ChunkType;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.ticks.LevelTickAccess;
import net.minecraft.world.ticks.WorldGenTickAccess;
import org.slf4j.Logger;

public class WorldGenRegion implements WorldGenLevel {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final StaticCache2D<GenerationChunkHolder> cache;
    private final ChunkAccess center;
    private final ServerLevel level;
    private final long seed;
    private final LevelData levelData;
    private final RandomSource random;
    private final DimensionType dimensionType;
    private final WorldGenTickAccess<Block> blockTicks = new WorldGenTickAccess<>(blockPos -> this.getChunk(blockPos).getBlockTicks());
    private final WorldGenTickAccess<Fluid> fluidTicks = new WorldGenTickAccess<>(blockPos -> this.getChunk(blockPos).getFluidTicks());
    private final BiomeManager biomeManager;
    private final ChunkStep generatingStep;
    @Nullable
    private Supplier<String> currentlyGenerating;
    private final AtomicLong subTickCount = new AtomicLong();
    private static final ResourceLocation WORLDGEN_REGION_RANDOM = ResourceLocation.withDefaultNamespace("worldgen_region_random");

    public WorldGenRegion(ServerLevel level, StaticCache2D<GenerationChunkHolder> cache, ChunkStep generatingStep, ChunkAccess center) {
        this.generatingStep = generatingStep;
        this.cache = cache;
        this.center = center;
        this.level = level;
        this.seed = level.getSeed();
        this.levelData = level.getLevelData();
        this.random = level.getChunkSource().randomState().getOrCreateRandomFactory(WORLDGEN_REGION_RANDOM).at(this.center.getPos().getWorldPosition());
        this.dimensionType = level.dimensionType();
        this.biomeManager = new BiomeManager(this, BiomeManager.obfuscateSeed(this.seed));
    }

    public boolean isOldChunkAround(ChunkPos pos, int radius) {
        return this.level.getChunkSource().chunkMap.isOldChunkAround(pos, radius);
    }

    public ChunkPos getCenter() {
        return this.center.getPos();
    }

    @Override
    public void setCurrentlyGenerating(@Nullable Supplier<String> currentlyGenerating) {
        this.currentlyGenerating = currentlyGenerating;
    }

    @Override
    public ChunkAccess getChunk(int chunkX, int chunkZ) {
        return this.getChunk(chunkX, chunkZ, ChunkStatus.EMPTY);
    }

    @Nullable
    @Override
    public ChunkAccess getChunk(int x, int z, ChunkStatus chunkStatus, boolean requireChunk) {
        int i = this.center.getPos().getChessboardDistance(x, z);
        ChunkStatus chunkStatus2 = i >= this.generatingStep.directDependencies().size() ? null : this.generatingStep.directDependencies().get(i);
        GenerationChunkHolder generationChunkHolder;
        if (chunkStatus2 != null) {
            generationChunkHolder = this.cache.get(x, z);
            if (chunkStatus.isOrBefore(chunkStatus2)) {
                ChunkAccess chunkAccess = generationChunkHolder.getChunkIfPresentUnchecked(chunkStatus2);
                if (chunkAccess != null) {
                    return chunkAccess;
                }
            }
        } else {
            generationChunkHolder = null;
        }

        CrashReport crashReport = CrashReport.forThrowable(
            new IllegalStateException("Requested chunk unavailable during world generation"), "Exception generating new chunk"
        );
        CrashReportCategory crashReportCategory = crashReport.addCategory("Chunk request details");
        crashReportCategory.setDetail("Requested chunk", String.format(Locale.ROOT, "%d, %d", x, z));
        crashReportCategory.setDetail("Generating status", () -> this.generatingStep.targetStatus().getName());
        crashReportCategory.setDetail("Requested status", chunkStatus::getName);
        crashReportCategory.setDetail(
            "Actual status", () -> generationChunkHolder == null ? "[out of cache bounds]" : generationChunkHolder.getPersistedStatus().getName()
        );
        crashReportCategory.setDetail("Maximum allowed status", () -> chunkStatus2 == null ? "null" : chunkStatus2.getName());
        crashReportCategory.setDetail("Dependencies", this.generatingStep.directDependencies()::toString);
        crashReportCategory.setDetail("Requested distance", i);
        crashReportCategory.setDetail("Generating chunk", this.center.getPos()::toString);
        throw new ReportedException(crashReport);
    }

    @Override
    public boolean hasChunk(int chunkX, int chunkZ) {
        int i = this.center.getPos().getChessboardDistance(chunkX, chunkZ);
        return i < this.generatingStep.directDependencies().size();
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        return this.getChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ())).getBlockState(pos);
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return this.getChunk(pos).getFluidState(pos);
    }

    @Nullable
    @Override
    public Player getNearestPlayer(double x, double y, double z, double distance, Predicate<Entity> predicate) {
        return null;
    }

    @Override
    public int getSkyDarken() {
        return 0;
    }

    @Override
    public BiomeManager getBiomeManager() {
        return this.biomeManager;
    }

    @Override
    public Holder<Biome> getUncachedNoiseBiome(int x, int y, int z) {
        return this.level.getUncachedNoiseBiome(x, y, z);
    }

    @Override
    public float getShade(Direction direction, boolean shade) {
        return 1.0F;
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return this.level.getLightEngine();
    }

    @Override
    public boolean destroyBlock(BlockPos pos, boolean dropBlock, @Nullable Entity entity, int recursionLeft) {
        BlockState blockState = this.getBlockState(pos);
        if (blockState.isAir()) {
            return false;
        }

        if (dropBlock) {
            BlockEntity blockEntity = blockState.hasBlockEntity() ? this.getBlockEntity(pos) : null;
            Block.dropResources(blockState, this.level, pos, blockEntity, entity, ItemStack.EMPTY);
        }

        return this.setBlock(pos, Blocks.AIR.defaultBlockState(), 3, recursionLeft);
    }

    @Nullable
    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        ChunkAccess chunkAccess = this.getChunk(pos);
        BlockEntity blockEntity = chunkAccess.getBlockEntity(pos);
        if (blockEntity != null) {
            return blockEntity;
        }

        CompoundTag compoundTag = chunkAccess.getBlockEntityNbt(pos);
        BlockState blockState = chunkAccess.getBlockState(pos);
        if (compoundTag != null) {
            if ("DUMMY".equals(compoundTag.getString("id"))) {
                if (!blockState.hasBlockEntity()) {
                    return null;
                }

                blockEntity = ((EntityBlock)blockState.getBlock()).newBlockEntity(pos, blockState);
            } else {
                blockEntity = BlockEntity.loadStatic(pos, blockState, compoundTag, this.level.registryAccess());
            }

            if (blockEntity != null) {
                chunkAccess.setBlockEntity(blockEntity);
                return blockEntity;
            }
        }

        if (blockState.hasBlockEntity()) {
            LOGGER.warn("Tried to access a block entity before it was created. {}", pos);
        }

        return null;
    }

    @Override
    public boolean ensureCanWrite(BlockPos pos) {
        int i = SectionPos.blockToSectionCoord(pos.getX());
        int j = SectionPos.blockToSectionCoord(pos.getZ());
        ChunkPos chunkPos = this.getCenter();
        int k = Math.abs(chunkPos.x - i);
        int l = Math.abs(chunkPos.z - j);
        if (k <= this.generatingStep.blockStateWriteRadius() && l <= this.generatingStep.blockStateWriteRadius()) {
            if (this.center.isUpgrading()) {
                LevelHeightAccessor levelHeightAccessor = this.center.getHeightAccessorForGeneration();
                if (pos.getY() < levelHeightAccessor.getMinBuildHeight() || pos.getY() >= levelHeightAccessor.getMaxBuildHeight()) {
                    return false;
                }
            }

            return true;
        } else {
            Util.logAndPauseIfInIde(
                "Detected setBlock in a far chunk ["
                    + i
                    + ", "
                    + j
                    + "], pos: "
                    + pos
                    + ", status: "
                    + this.generatingStep.targetStatus()
                    + (this.currentlyGenerating == null ? "" : ", currently generating: " + this.currentlyGenerating.get())
            );
            return false;
        }
    }

    @Override
    public boolean setBlock(BlockPos pos, BlockState state, int flags, int recursionLeft) {
        if (!this.ensureCanWrite(pos)) {
            return false;
        }

        ChunkAccess chunkAccess = this.getChunk(pos);
        BlockState blockState = chunkAccess.setBlockState(pos, state, false);
        if (blockState != null) {
            this.level.onBlockStateChange(pos, blockState, state);
        }

        if (state.hasBlockEntity()) {
            if (chunkAccess.getPersistedStatus().getChunkType() == ChunkType.LEVELCHUNK) {
                BlockEntity blockEntity = ((EntityBlock)state.getBlock()).newBlockEntity(pos, state);
                if (blockEntity != null) {
                    chunkAccess.setBlockEntity(blockEntity);
                } else {
                    chunkAccess.removeBlockEntity(pos);
                }
            } else {
                CompoundTag compoundTag = new CompoundTag();
                compoundTag.putInt("x", pos.getX());
                compoundTag.putInt("y", pos.getY());
                compoundTag.putInt("z", pos.getZ());
                compoundTag.putString("id", "DUMMY");
                chunkAccess.setBlockEntityNbt(compoundTag);
            }
        } else if (blockState != null && blockState.hasBlockEntity()) {
            chunkAccess.removeBlockEntity(pos);
        }

        if (state.hasPostProcess(this, pos)) {
            this.markPosForPostprocessing(pos);
        }

        return true;
    }

    private void markPosForPostprocessing(BlockPos pos) {
        this.getChunk(pos).markPosForPostprocessing(pos);
    }

    @Override
    public boolean addFreshEntity(Entity entity) {
        int i = SectionPos.blockToSectionCoord(entity.getBlockX());
        int j = SectionPos.blockToSectionCoord(entity.getBlockZ());
        this.getChunk(i, j).addEntity(entity);
        return true;
    }

    @Override
    public boolean removeBlock(BlockPos pos, boolean isMoving) {
        return this.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
    }

    @Override
    public WorldBorder getWorldBorder() {
        return this.level.getWorldBorder();
    }

    @Override
    public boolean isClientSide() {
        return false;
    }

    @Deprecated
    @Override
    public ServerLevel getLevel() {
        return this.level;
    }

    @Override
    public RegistryAccess registryAccess() {
        return this.level.registryAccess();
    }

    @Override
    public FeatureFlagSet enabledFeatures() {
        return this.level.enabledFeatures();
    }

    @Override
    public LevelData getLevelData() {
        return this.levelData;
    }

    @Override
    public DifficultyInstance getCurrentDifficultyAt(BlockPos pos) {
        if (!this.hasChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()))) {
            throw new RuntimeException("We are asking a region for a chunk out of bound");
        } else {
            return new DifficultyInstance(this.level.getDifficulty(), this.level.getDayTime(), 0L, this.level.getMoonBrightness());
        }
    }

    @Nullable
    @Override
    public MinecraftServer getServer() {
        return this.level.getServer();
    }

    @Override
    public ChunkSource getChunkSource() {
        return this.level.getChunkSource();
    }

    @Override
    public long getSeed() {
        return this.seed;
    }

    @Override
    public LevelTickAccess<Block> getBlockTicks() {
        return this.blockTicks;
    }

    @Override
    public LevelTickAccess<Fluid> getFluidTicks() {
        return this.fluidTicks;
    }

    @Override
    public int getSeaLevel() {
        return this.level.getSeaLevel();
    }

    @Override
    public RandomSource getRandom() {
        return this.random;
    }

    @Override
    public int getHeight(Heightmap.Types heightmapType, int x, int z) {
        return this.getChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z)).getHeight(heightmapType, x & 15, z & 15) + 1;
    }

    /**
     * Plays a sound. On the server, the sound is broadcast to all nearby <em>except</em> the given player. On the client, the sound only plays if the given player is the client player. Thus, this method is intended to be called from code running on both sides. The client plays it locally and the server plays it for everyone else.
     */
    @Override
    public void playSound(@Nullable Player player, BlockPos pos, SoundEvent sound, SoundSource category, float volume, float pitch) {
    }

    @Override
    public void addParticle(ParticleOptions particleData, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
    }

    @Override
    public void levelEvent(@Nullable Player player, int type, BlockPos pos, int data) {
    }

    @Override
    public void gameEvent(Holder<GameEvent> gameEvent, Vec3 pos, GameEvent.Context context) {
    }

    @Override
    public DimensionType dimensionType() {
        return this.dimensionType;
    }

    @Override
    public boolean isStateAtPosition(BlockPos pos, Predicate<BlockState> state) {
        return state.test(this.getBlockState(pos));
    }

    @Override
    public boolean isFluidAtPosition(BlockPos pos, Predicate<FluidState> predicate) {
        return predicate.test(this.getFluidState(pos));
    }

    @Override
    public <T extends Entity> List<T> getEntities(EntityTypeTest<Entity, T> entityTypeTest, AABB bounds, Predicate<? super T> predicate) {
        return Collections.emptyList();
    }

    /**
     * Gets all entities within the specified AABB excluding the one passed into it.
     */
    @Override
    public List<Entity> getEntities(@Nullable Entity entity, AABB boundingBox, @Nullable Predicate<? super Entity> predicate) {
        return Collections.emptyList();
    }

    @Override
    public List<Player> players() {
        return Collections.emptyList();
    }

    @Override
    public int getMinBuildHeight() {
        return this.level.getMinBuildHeight();
    }

    @Override
    public int getHeight() {
        return this.level.getHeight();
    }

    @Override
    public long nextSubTickCount() {
        return this.subTickCount.getAndIncrement();
    }
}
