package net.minecraft.server.level;

import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

public class PlayerRespawnLogic {
    @Nullable
    protected static BlockPos getOverworldRespawnPos(ServerLevel level, int x, int z) {
        boolean bl = level.dimensionType().hasCeiling();
        LevelChunk levelChunk = level.getChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z));
        int i = bl ? level.getChunkSource().getGenerator().getSpawnHeight(level) : levelChunk.getHeight(Heightmap.Types.MOTION_BLOCKING, x & 15, z & 15);
        if (i < level.getMinBuildHeight()) {
            return null;
        }

        int j = levelChunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);
        if (j <= i && j > levelChunk.getHeight(Heightmap.Types.OCEAN_FLOOR, x & 15, z & 15)) {
            return null;
        }

        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

        for (int k = i + 1; k >= level.getMinBuildHeight(); k--) {
            mutableBlockPos.set(x, k, z);
            BlockState blockState = level.getBlockState(mutableBlockPos);
            if (!blockState.getFluidState().isEmpty()) {
                break;
            }

            if (Block.isFaceFull(blockState.getCollisionShape(level, mutableBlockPos), Direction.UP)) {
                return mutableBlockPos.above().immutable();
            }
        }

        return null;
    }

    @Nullable
    public static BlockPos getSpawnPosInChunk(ServerLevel level, ChunkPos chunkPos) {
        if (SharedConstants.debugVoidTerrain(chunkPos)) {
            return null;
        }

        for (int i = chunkPos.getMinBlockX(); i <= chunkPos.getMaxBlockX(); i++) {
            for (int j = chunkPos.getMinBlockZ(); j <= chunkPos.getMaxBlockZ(); j++) {
                BlockPos blockPos = getOverworldRespawnPos(level, i, j);
                if (blockPos != null) {
                    return blockPos;
                }
            }
        }

        return null;
    }
}
