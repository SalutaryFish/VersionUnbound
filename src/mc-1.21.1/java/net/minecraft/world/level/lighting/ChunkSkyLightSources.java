package net.minecraft.world.level.lighting;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.BitStorage;
import net.minecraft.util.Mth;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ChunkSkyLightSources {
    private static final int SIZE = 16;
    public static final int NEGATIVE_INFINITY = Integer.MIN_VALUE;
    private final int minY;
    private final BitStorage heightmap;
    private final BlockPos.MutableBlockPos mutablePos1 = new BlockPos.MutableBlockPos();
    private final BlockPos.MutableBlockPos mutablePos2 = new BlockPos.MutableBlockPos();

    public ChunkSkyLightSources(LevelHeightAccessor level) {
        this.minY = level.getMinBuildHeight() - 1;
        int i = level.getMaxBuildHeight();
        int j = Mth.ceillog2(i - this.minY + 1);
        this.heightmap = new SimpleBitStorage(j, 256);
    }

    public void fillFrom(ChunkAccess chunk) {
        int i = chunk.getHighestFilledSectionIndex();
        if (i == -1) {
            this.fill(this.minY);
        } else {
            for (int j = 0; j < 16; j++) {
                for (int k = 0; k < 16; k++) {
                    int l = Math.max(this.findLowestSourceY(chunk, i, k, j), this.minY);
                    this.set(index(k, j), l);
                }
            }
        }
    }

    private int findLowestSourceY(ChunkAccess chunk, int sectionIndex, int x, int z) {
        int i = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(sectionIndex) + 1);
        BlockPos.MutableBlockPos mutableBlockPos = this.mutablePos1.set(x, i, z);
        BlockPos.MutableBlockPos mutableBlockPos2 = this.mutablePos2.setWithOffset(mutableBlockPos, Direction.DOWN);
        BlockState blockState = Blocks.AIR.defaultBlockState();

        for (int j = sectionIndex; j >= 0; j--) {
            LevelChunkSection levelChunkSection = chunk.getSection(j);
            if (levelChunkSection.hasOnlyAir()) {
                blockState = Blocks.AIR.defaultBlockState();
                int k = chunk.getSectionYFromSectionIndex(j);
                mutableBlockPos.setY(SectionPos.sectionToBlockCoord(k));
                mutableBlockPos2.setY(mutableBlockPos.getY() - 1);
            } else {
                for (int k = 15; k >= 0; k--) {
                    BlockState blockState2 = levelChunkSection.getBlockState(x, k, z);
                    if (isEdgeOccluded(chunk, mutableBlockPos, blockState, mutableBlockPos2, blockState2)) {
                        return mutableBlockPos.getY();
                    }

                    blockState = blockState2;
                    mutableBlockPos.set(mutableBlockPos2);
                    mutableBlockPos2.move(Direction.DOWN);
                }
            }
        }

        return this.minY;
    }

    public boolean update(BlockGetter level, int x, int y, int z) {
        int i = y + 1;
        int j = index(x, z);
        int k = this.get(j);
        if (i < k) {
            return false;
        }

        BlockPos blockPos = this.mutablePos1.set(x, y + 1, z);
        BlockState blockState = level.getBlockState(blockPos);
        BlockPos blockPos2 = this.mutablePos2.set(x, y, z);
        BlockState blockState2 = level.getBlockState(blockPos2);
        if (this.updateEdge(level, j, k, blockPos, blockState, blockPos2, blockState2)) {
            return true;
        }

        BlockPos blockPos3 = this.mutablePos1.set(x, y - 1, z);
        BlockState blockState3 = level.getBlockState(blockPos3);
        return this.updateEdge(level, j, k, blockPos2, blockState2, blockPos3, blockState3);
    }

    private boolean updateEdge(BlockGetter level, int index, int minY, BlockPos pos1, BlockState state1, BlockPos pos2, BlockState state2) {
        int i = pos1.getY();
        if (isEdgeOccluded(level, pos1, state1, pos2, state2)) {
            if (i > minY) {
                this.set(index, i);
                return true;
            }
        } else if (i == minY) {
            this.set(index, this.findLowestSourceBelow(level, pos2, state2));
            return true;
        }

        return false;
    }

    private int findLowestSourceBelow(BlockGetter level, BlockPos pos, BlockState state) {
        BlockPos.MutableBlockPos mutableBlockPos = this.mutablePos1.set(pos);
        BlockPos.MutableBlockPos mutableBlockPos2 = this.mutablePos2.setWithOffset(pos, Direction.DOWN);
        BlockState blockState = state;

        while (mutableBlockPos2.getY() >= this.minY) {
            BlockState blockState2 = level.getBlockState(mutableBlockPos2);
            if (isEdgeOccluded(level, mutableBlockPos, blockState, mutableBlockPos2, blockState2)) {
                return mutableBlockPos.getY();
            }

            blockState = blockState2;
            mutableBlockPos.set(mutableBlockPos2);
            mutableBlockPos2.move(Direction.DOWN);
        }

        return this.minY;
    }

    private static boolean isEdgeOccluded(BlockGetter level, BlockPos pos1, BlockState state1, BlockPos pos2, BlockState state2) {
        if (state2.getLightBlock(level, pos2) != 0) {
            return true;
        }

        VoxelShape voxelShape = LightEngine.getOcclusionShape(level, pos1, state1, Direction.DOWN);
        VoxelShape voxelShape2 = LightEngine.getOcclusionShape(level, pos2, state2, Direction.UP);
        return Shapes.faceShapeOccludes(voxelShape, voxelShape2);
    }

    public int getLowestSourceY(int x, int z) {
        int i = this.get(index(x, z));
        return this.extendSourcesBelowWorld(i);
    }

    public int getHighestLowestSourceY() {
        int i = Integer.MIN_VALUE;

        for (int j = 0; j < this.heightmap.getSize(); j++) {
            int k = this.heightmap.get(j);
            if (k > i) {
                i = k;
            }
        }

        return this.extendSourcesBelowWorld(i + this.minY);
    }

    private void fill(int value) {
        int i = value - this.minY;

        for (int j = 0; j < this.heightmap.getSize(); j++) {
            this.heightmap.set(j, i);
        }
    }

    private void set(int index, int value) {
        this.heightmap.set(index, value - this.minY);
    }

    private int get(int index) {
        return this.heightmap.get(index) + this.minY;
    }

    private int extendSourcesBelowWorld(int y) {
        return y == this.minY ? Integer.MIN_VALUE : y;
    }

    private static int index(int x, int z) {
        return x + z * 16;
    }
}
