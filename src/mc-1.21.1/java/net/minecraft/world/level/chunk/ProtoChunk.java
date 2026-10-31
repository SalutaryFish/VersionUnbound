package net.minecraft.world.level.chunk;

import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.BelowZeroRetrogen;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.lighting.LightEngine;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.ProtoChunkTicks;
import net.minecraft.world.ticks.TickContainerAccess;

public class ProtoChunk extends ChunkAccess {
    @Nullable
    private volatile LevelLightEngine lightEngine;
    private volatile ChunkStatus status = ChunkStatus.EMPTY;
    private final List<CompoundTag> entities = Lists.newArrayList();
    private final Map<GenerationStep.Carving, CarvingMask> carvingMasks = new Object2ObjectArrayMap<>();
    @Nullable
    private BelowZeroRetrogen belowZeroRetrogen;
    private final ProtoChunkTicks<Block> blockTicks;
    private final ProtoChunkTicks<Fluid> fluidTicks;

    public ProtoChunk(
        ChunkPos chunkPos, UpgradeData upgradeData, LevelHeightAccessor levelHeightAccessor, Registry<Biome> biomeRegistry, @Nullable BlendingData blendingData
    ) {
        this(chunkPos, upgradeData, null, new ProtoChunkTicks<>(), new ProtoChunkTicks<>(), levelHeightAccessor, biomeRegistry, blendingData);
    }

    public ProtoChunk(
        ChunkPos chunkPos,
        UpgradeData upgradeData,
        @Nullable LevelChunkSection[] sections,
        ProtoChunkTicks<Block> blockTicks,
        ProtoChunkTicks<Fluid> liquidTicks,
        LevelHeightAccessor levelHeightAccessor,
        Registry<Biome> biomeRegistry,
        @Nullable BlendingData blendingData
    ) {
        super(chunkPos, upgradeData, levelHeightAccessor, biomeRegistry, 0L, sections, blendingData);
        this.blockTicks = blockTicks;
        this.fluidTicks = liquidTicks;
    }

    @Override
    public TickContainerAccess<Block> getBlockTicks() {
        return this.blockTicks;
    }

    @Override
    public TickContainerAccess<Fluid> getFluidTicks() {
        return this.fluidTicks;
    }

    @Override
    public ChunkAccess.TicksToSave getTicksForSerialization() {
        return new ChunkAccess.TicksToSave(this.blockTicks, this.fluidTicks);
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        int i = pos.getY();
        if (this.isOutsideBuildHeight(i)) {
            return Blocks.VOID_AIR.defaultBlockState();
        }

        LevelChunkSection levelChunkSection = this.getSection(this.getSectionIndex(i));
        return levelChunkSection.hasOnlyAir() ? Blocks.AIR.defaultBlockState() : levelChunkSection.getBlockState(pos.getX() & 15, i & 15, pos.getZ() & 15);
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        int i = pos.getY();
        if (this.isOutsideBuildHeight(i)) {
            return Fluids.EMPTY.defaultFluidState();
        }

        LevelChunkSection levelChunkSection = this.getSection(this.getSectionIndex(i));
        return levelChunkSection.hasOnlyAir() ? Fluids.EMPTY.defaultFluidState() : levelChunkSection.getFluidState(pos.getX() & 15, i & 15, pos.getZ() & 15);
    }

    @Nullable
    @Override
    public BlockState setBlockState(BlockPos pos, BlockState state, boolean isMoving) {
        int i = pos.getX();
        int j = pos.getY();
        int k = pos.getZ();
        if (j >= this.getMinBuildHeight() && j < this.getMaxBuildHeight()) {
            int l = this.getSectionIndex(j);
            LevelChunkSection levelChunkSection = this.getSection(l);
            boolean bl = levelChunkSection.hasOnlyAir();
            if (bl && state.is(Blocks.AIR)) {
                return state;
            }

            int m = SectionPos.sectionRelative(i);
            int n = SectionPos.sectionRelative(j);
            int o = SectionPos.sectionRelative(k);
            BlockState blockState = levelChunkSection.setBlockState(m, n, o, state);
            if (this.status.isOrAfter(ChunkStatus.INITIALIZE_LIGHT)) {
                boolean bl2 = levelChunkSection.hasOnlyAir();
                if (bl2 != bl) {
                    this.lightEngine.updateSectionStatus(pos, bl2);
                }

                if (LightEngine.hasDifferentLightProperties(this, pos, blockState, state)) {
                    this.skyLightSources.update(this, m, j, o);
                    this.lightEngine.checkBlock(pos);
                }
            }

            EnumSet<Heightmap.Types> enumSet = this.getPersistedStatus().heightmapsAfter();
            EnumSet<Heightmap.Types> enumSet2 = null;

            for (Heightmap.Types types : enumSet) {
                Heightmap heightmap = this.heightmaps.get(types);
                if (heightmap == null) {
                    if (enumSet2 == null) {
                        enumSet2 = EnumSet.noneOf(Heightmap.Types.class);
                    }

                    enumSet2.add(types);
                }
            }

            if (enumSet2 != null) {
                Heightmap.primeHeightmaps(this, enumSet2);
            }

            for (Heightmap.Types types : enumSet) {
                this.heightmaps.get(types).update(m, j, o, state);
            }

            return blockState;
        } else {
            return Blocks.VOID_AIR.defaultBlockState();
        }
    }

    @Override
    public void setBlockEntity(BlockEntity blockEntity) {
        this.blockEntities.put(blockEntity.getBlockPos(), blockEntity);
    }

    @Nullable
    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        return this.blockEntities.get(pos);
    }

    public Map<BlockPos, BlockEntity> getBlockEntities() {
        return this.blockEntities;
    }

    public void addEntity(CompoundTag tag) {
        this.entities.add(tag);
    }

    @Override
    public void addEntity(Entity entity) {
        if (!entity.isPassenger()) {
            CompoundTag compoundTag = new CompoundTag();
            entity.save(compoundTag);
            this.addEntity(compoundTag);
        }
    }

    @Override
    public void setStartForStructure(Structure structure, StructureStart structureStart) {
        BelowZeroRetrogen belowZeroRetrogen = this.getBelowZeroRetrogen();
        if (belowZeroRetrogen != null && structureStart.isValid()) {
            BoundingBox boundingBox = structureStart.getBoundingBox();
            LevelHeightAccessor levelHeightAccessor = this.getHeightAccessorForGeneration();
            if (boundingBox.minY() < levelHeightAccessor.getMinBuildHeight() || boundingBox.maxY() >= levelHeightAccessor.getMaxBuildHeight()) {
                return;
            }
        }

        super.setStartForStructure(structure, structureStart);
    }

    public List<CompoundTag> getEntities() {
        return this.entities;
    }

    @Override
    public ChunkStatus getPersistedStatus() {
        return this.status;
    }

    public void setPersistedStatus(ChunkStatus status) {
        this.status = status;
        if (this.belowZeroRetrogen != null && status.isOrAfter(this.belowZeroRetrogen.targetStatus())) {
            this.setBelowZeroRetrogen(null);
        }

        this.setUnsaved(true);
    }

    /**
     * Gets the biome at the given quart positions.
     * Note that the coordinates passed into this method are 1/4 the scale of block coordinates.
     */
    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z) {
        if (this.getHighestGeneratedStatus().isOrAfter(ChunkStatus.BIOMES)) {
            return super.getNoiseBiome(x, y, z);
        } else {
            throw new IllegalStateException("Asking for biomes before we have biomes");
        }
    }

    public static short packOffsetCoordinates(BlockPos pos) {
        int i = pos.getX();
        int j = pos.getY();
        int k = pos.getZ();
        int l = i & 15;
        int m = j & 15;
        int n = k & 15;
        return (short)(l | m << 4 | n << 8);
    }

    public static BlockPos unpackOffsetCoordinates(short packedPos, int yOffset, ChunkPos chunkPos) {
        int i = SectionPos.sectionToBlockCoord(chunkPos.x, packedPos & 15);
        int j = SectionPos.sectionToBlockCoord(yOffset, packedPos >>> 4 & 15);
        int k = SectionPos.sectionToBlockCoord(chunkPos.z, packedPos >>> 8 & 15);
        return new BlockPos(i, j, k);
    }

    @Override
    public void markPosForPostprocessing(BlockPos pos) {
        if (!this.isOutsideBuildHeight(pos)) {
            ChunkAccess.getOrCreateOffsetList(this.postProcessing, this.getSectionIndex(pos.getY())).add(packOffsetCoordinates(pos));
        }
    }

    @Override
    public void addPackedPostProcess(short packedPosition, int index) {
        ChunkAccess.getOrCreateOffsetList(this.postProcessing, index).add(packedPosition);
    }

    public Map<BlockPos, CompoundTag> getBlockEntityNbts() {
        return Collections.unmodifiableMap(this.pendingBlockEntities);
    }

    @Nullable
    @Override
    public CompoundTag getBlockEntityNbtForSaving(BlockPos pos, HolderLookup.Provider registries) {
        BlockEntity blockEntity = this.getBlockEntity(pos);
        return blockEntity != null ? blockEntity.saveWithFullMetadata(registries) : this.pendingBlockEntities.get(pos);
    }

    @Override
    public void removeBlockEntity(BlockPos pos) {
        this.blockEntities.remove(pos);
        this.pendingBlockEntities.remove(pos);
    }

    @Nullable
    public CarvingMask getCarvingMask(GenerationStep.Carving step) {
        return this.carvingMasks.get(step);
    }

    public CarvingMask getOrCreateCarvingMask(GenerationStep.Carving step) {
        return this.carvingMasks.computeIfAbsent(step, key -> new CarvingMask(this.getHeight(), this.getMinBuildHeight()));
    }

    public void setCarvingMask(GenerationStep.Carving step, CarvingMask carvingMask) {
        this.carvingMasks.put(step, carvingMask);
    }

    public void setLightEngine(LevelLightEngine lightEngine) {
        this.lightEngine = lightEngine;
    }

    public void setBelowZeroRetrogen(@Nullable BelowZeroRetrogen belowZeroRetrogen) {
        this.belowZeroRetrogen = belowZeroRetrogen;
    }

    @Nullable
    @Override
    public BelowZeroRetrogen getBelowZeroRetrogen() {
        return this.belowZeroRetrogen;
    }

    private static <T> LevelChunkTicks<T> unpackTicks(ProtoChunkTicks<T> ticks) {
        return new LevelChunkTicks<>(ticks.scheduledTicks());
    }

    public LevelChunkTicks<Block> unpackBlockTicks() {
        return unpackTicks(this.blockTicks);
    }

    public LevelChunkTicks<Fluid> unpackFluidTicks() {
        return unpackTicks(this.fluidTicks);
    }

    @Override
    public LevelHeightAccessor getHeightAccessorForGeneration() {
        return this.isUpgrading() ? BelowZeroRetrogen.UPGRADE_HEIGHT_ACCESSOR : this;
    }
}
