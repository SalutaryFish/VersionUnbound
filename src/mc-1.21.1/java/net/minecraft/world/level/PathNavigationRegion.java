package net.minecraft.world.level;

import com.google.common.base.Suppliers;
import java.util.List;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

public class PathNavigationRegion implements BlockGetter, CollisionGetter {
    protected final int centerX;
    protected final int centerZ;
    protected final ChunkAccess[][] chunks;
    protected boolean allEmpty;
    protected final Level level;
    private final Supplier<Holder<Biome>> plains;

    public PathNavigationRegion(Level level, BlockPos centerPos, BlockPos offsetPos) {
        this.level = level;
        this.plains = Suppliers.memoize(() -> level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.PLAINS));
        this.centerX = SectionPos.blockToSectionCoord(centerPos.getX());
        this.centerZ = SectionPos.blockToSectionCoord(centerPos.getZ());
        int i = SectionPos.blockToSectionCoord(offsetPos.getX());
        int j = SectionPos.blockToSectionCoord(offsetPos.getZ());
        this.chunks = new ChunkAccess[i - this.centerX + 1][j - this.centerZ + 1];
        ChunkSource chunkSource = level.getChunkSource();
        this.allEmpty = true;

        for (int k = this.centerX; k <= i; k++) {
            for (int l = this.centerZ; l <= j; l++) {
                this.chunks[k - this.centerX][l - this.centerZ] = chunkSource.getChunkNow(k, l);
            }
        }

        for (int k = SectionPos.blockToSectionCoord(centerPos.getX()); k <= SectionPos.blockToSectionCoord(offsetPos.getX()); k++) {
            for (int l = SectionPos.blockToSectionCoord(centerPos.getZ()); l <= SectionPos.blockToSectionCoord(offsetPos.getZ()); l++) {
                ChunkAccess chunkAccess = this.chunks[k - this.centerX][l - this.centerZ];
                if (chunkAccess != null && !chunkAccess.isYSpaceEmpty(centerPos.getY(), offsetPos.getY())) {
                    this.allEmpty = false;
                    return;
                }
            }
        }
    }

    private ChunkAccess getChunk(BlockPos pos) {
        return this.getChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
    }

    private ChunkAccess getChunk(int x, int z) {
        int i = x - this.centerX;
        int j = z - this.centerZ;
        if (i >= 0 && i < this.chunks.length && j >= 0 && j < this.chunks[i].length) {
            ChunkAccess chunkAccess = this.chunks[i][j];
            return chunkAccess != null ? chunkAccess : new EmptyLevelChunk(this.level, new ChunkPos(x, z), this.plains.get());
        } else {
            return new EmptyLevelChunk(this.level, new ChunkPos(x, z), this.plains.get());
        }
    }

    @Override
    public WorldBorder getWorldBorder() {
        return this.level.getWorldBorder();
    }

    @Override
    public BlockGetter getChunkForCollisions(int chunkX, int chunkZ) {
        return this.getChunk(chunkX, chunkZ);
    }

    @Override
    public List<VoxelShape> getEntityCollisions(@Nullable Entity entity, AABB collisionBox) {
        return List.of();
    }

    @Nullable
    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        ChunkAccess chunkAccess = this.getChunk(pos);
        return chunkAccess.getBlockEntity(pos);
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        if (this.isOutsideBuildHeight(pos)) {
            return Blocks.AIR.defaultBlockState();
        }

        ChunkAccess chunkAccess = this.getChunk(pos);
        return chunkAccess.getBlockState(pos);
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        if (this.isOutsideBuildHeight(pos)) {
            return Fluids.EMPTY.defaultFluidState();
        }

        ChunkAccess chunkAccess = this.getChunk(pos);
        return chunkAccess.getFluidState(pos);
    }

    @Override
    public int getMinBuildHeight() {
        return this.level.getMinBuildHeight();
    }

    @Override
    public int getHeight() {
        return this.level.getHeight();
    }

    public ProfilerFiller getProfiler() {
        return this.level.getProfiler();
    }
}
