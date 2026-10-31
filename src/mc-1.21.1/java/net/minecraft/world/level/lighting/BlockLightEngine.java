package net.minecraft.world.level.lighting;

import com.google.common.annotations.VisibleForTesting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;

public final class BlockLightEngine extends LightEngine<BlockLightSectionStorage.BlockDataLayerStorageMap, BlockLightSectionStorage> {
    private final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

    public BlockLightEngine(LightChunkGetter chunkSource) {
        this(chunkSource, new BlockLightSectionStorage(chunkSource));
    }

    @VisibleForTesting
    public BlockLightEngine(LightChunkGetter chunkSource, BlockLightSectionStorage storage) {
        super(chunkSource, storage);
    }

    @Override
    protected void checkNode(long packedPos) {
        long l = SectionPos.blockToSection(packedPos);
        if (this.storage.storingLightForSection(l)) {
            BlockState blockState = this.getState(this.mutablePos.set(packedPos));
            int i = this.getEmission(packedPos, blockState);
            int j = this.storage.getStoredLevel(packedPos);
            if (i < j) {
                this.storage.setStoredLevel(packedPos, 0);
                this.enqueueDecrease(packedPos, LightEngine.QueueEntry.decreaseAllDirections(j));
            } else {
                this.enqueueDecrease(packedPos, PULL_LIGHT_IN_ENTRY);
            }

            if (i > 0) {
                this.enqueueIncrease(packedPos, LightEngine.QueueEntry.increaseLightFromEmission(i, isEmptyShape(blockState)));
            }
        }
    }

    @Override
    protected void propagateIncrease(long packedPos, long queueEntry, int lightLevel) {
        BlockState blockState = null;

        for (Direction direction : PROPAGATION_DIRECTIONS) {
            if (LightEngine.QueueEntry.shouldPropagateInDirection(queueEntry, direction)) {
                long l = BlockPos.offset(packedPos, direction);
                if (this.storage.storingLightForSection(SectionPos.blockToSection(l))) {
                    int i = this.storage.getStoredLevel(l);
                    int j = lightLevel - 1;
                    if (j > i) {
                        this.mutablePos.set(l);
                        BlockState blockState2 = this.getState(this.mutablePos);
                        int k = lightLevel - this.getOpacity(blockState2, this.mutablePos);
                        if (k > i) {
                            if (blockState == null) {
                                blockState = LightEngine.QueueEntry.isFromEmptyShape(queueEntry)
                                    ? Blocks.AIR.defaultBlockState()
                                    : this.getState(this.mutablePos.set(packedPos));
                            }

                            if (!this.shapeOccludes(packedPos, blockState, l, blockState2, direction)) {
                                this.storage.setStoredLevel(l, k);
                                if (k > 1) {
                                    this.enqueueIncrease(
                                        l, LightEngine.QueueEntry.increaseSkipOneDirection(k, isEmptyShape(blockState2), direction.getOpposite())
                                    );
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    protected void propagateDecrease(long packedPos, long lightLevel) {
        int i = LightEngine.QueueEntry.getFromLevel(lightLevel);

        for (Direction direction : PROPAGATION_DIRECTIONS) {
            if (LightEngine.QueueEntry.shouldPropagateInDirection(lightLevel, direction)) {
                long l = BlockPos.offset(packedPos, direction);
                if (this.storage.storingLightForSection(SectionPos.blockToSection(l))) {
                    int j = this.storage.getStoredLevel(l);
                    if (j != 0) {
                        if (j <= i - 1) {
                            BlockState blockState = this.getState(this.mutablePos.set(l));
                            int k = this.getEmission(l, blockState);
                            this.storage.setStoredLevel(l, 0);
                            if (k < j) {
                                this.enqueueDecrease(l, LightEngine.QueueEntry.decreaseSkipOneDirection(j, direction.getOpposite()));
                            }

                            if (k > 0) {
                                this.enqueueIncrease(l, LightEngine.QueueEntry.increaseLightFromEmission(k, isEmptyShape(blockState)));
                            }
                        } else {
                            this.enqueueIncrease(l, LightEngine.QueueEntry.increaseOnlyOneDirection(j, false, direction.getOpposite()));
                        }
                    }
                }
            }
        }
    }

    private int getEmission(long packedPos, BlockState state) {
        int i = state.getLightEmission();
        return i > 0 && this.storage.lightOnInSection(SectionPos.blockToSection(packedPos)) ? i : 0;
    }

    @Override
    public void propagateLightSources(ChunkPos chunkPos) {
        this.setLightEnabled(chunkPos, true);
        LightChunk lightChunk = this.chunkSource.getChunkForLighting(chunkPos.x, chunkPos.z);
        if (lightChunk != null) {
            lightChunk.findBlockLightSources((blockPos, blockState) -> {
                int i = blockState.getLightEmission();
                this.enqueueIncrease(blockPos.asLong(), LightEngine.QueueEntry.increaseLightFromEmission(i, isEmptyShape(blockState)));
            });
        }
    }
}
