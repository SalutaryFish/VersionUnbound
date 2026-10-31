package net.minecraft.world.level.lighting;

import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import org.jetbrains.annotations.VisibleForTesting;

public final class SkyLightEngine extends LightEngine<SkyLightSectionStorage.SkyDataLayerStorageMap, SkyLightSectionStorage> {
    private static final long REMOVE_TOP_SKY_SOURCE_ENTRY = LightEngine.QueueEntry.decreaseAllDirections(15);
    private static final long REMOVE_SKY_SOURCE_ENTRY = LightEngine.QueueEntry.decreaseSkipOneDirection(15, Direction.UP);
    private static final long ADD_SKY_SOURCE_ENTRY = LightEngine.QueueEntry.increaseSkipOneDirection(15, false, Direction.UP);
    private final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
    private final ChunkSkyLightSources emptyChunkSources;

    public SkyLightEngine(LightChunkGetter chunkSource) {
        this(chunkSource, new SkyLightSectionStorage(chunkSource));
    }

    @VisibleForTesting
    protected SkyLightEngine(LightChunkGetter chunkSource, SkyLightSectionStorage sectionStorage) {
        super(chunkSource, sectionStorage);
        this.emptyChunkSources = new ChunkSkyLightSources(chunkSource.getLevel());
    }

    private static boolean isSourceLevel(int level) {
        return level == 15;
    }

    private int getLowestSourceY(int x, int z, int defaultReturnValue) {
        ChunkSkyLightSources chunkSkyLightSources = this.getChunkSources(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z));
        return chunkSkyLightSources == null
            ? defaultReturnValue
            : chunkSkyLightSources.getLowestSourceY(SectionPos.sectionRelative(x), SectionPos.sectionRelative(z));
    }

    @Nullable
    private ChunkSkyLightSources getChunkSources(int chunkX, int chunkZ) {
        LightChunk lightChunk = this.chunkSource.getChunkForLighting(chunkX, chunkZ);
        return lightChunk != null ? lightChunk.getSkyLightSources() : null;
    }

    @Override
    protected void checkNode(long levelPos) {
        int i = BlockPos.getX(levelPos);
        int j = BlockPos.getY(levelPos);
        int k = BlockPos.getZ(levelPos);
        long l = SectionPos.blockToSection(levelPos);
        int m = this.storage.lightOnInSection(l) ? this.getLowestSourceY(i, k, Integer.MAX_VALUE) : Integer.MAX_VALUE;
        if (m != Integer.MAX_VALUE) {
            this.updateSourcesInColumn(i, k, m);
        }

        if (this.storage.storingLightForSection(l)) {
            boolean bl = j >= m;
            if (bl) {
                this.enqueueDecrease(levelPos, REMOVE_SKY_SOURCE_ENTRY);
                this.enqueueIncrease(levelPos, ADD_SKY_SOURCE_ENTRY);
            } else {
                int n = this.storage.getStoredLevel(levelPos);
                if (n > 0) {
                    this.storage.setStoredLevel(levelPos, 0);
                    this.enqueueDecrease(levelPos, LightEngine.QueueEntry.decreaseAllDirections(n));
                } else {
                    this.enqueueDecrease(levelPos, PULL_LIGHT_IN_ENTRY);
                }
            }
        }
    }

    private void updateSourcesInColumn(int x, int z, int lowestY) {
        int i = SectionPos.sectionToBlockCoord(this.storage.getBottomSectionY());
        this.removeSourcesBelow(x, z, lowestY, i);
        this.addSourcesAbove(x, z, lowestY, i);
    }

    private void removeSourcesBelow(int x, int z, int minY, int bottomSectionY) {
        if (minY > bottomSectionY) {
            int i = SectionPos.blockToSectionCoord(x);
            int j = SectionPos.blockToSectionCoord(z);
            int k = minY - 1;

            for (int l = SectionPos.blockToSectionCoord(k); this.storage.hasLightDataAtOrBelow(l); l--) {
                if (this.storage.storingLightForSection(SectionPos.asLong(i, l, j))) {
                    int m = SectionPos.sectionToBlockCoord(l);
                    int n = m + 15;

                    for (int o = Math.min(n, k); o >= m; o--) {
                        long p = BlockPos.asLong(x, o, z);
                        if (!isSourceLevel(this.storage.getStoredLevel(p))) {
                            return;
                        }

                        this.storage.setStoredLevel(p, 0);
                        this.enqueueDecrease(p, o == minY - 1 ? REMOVE_TOP_SKY_SOURCE_ENTRY : REMOVE_SKY_SOURCE_ENTRY);
                    }
                }
            }
        }
    }

    private void addSourcesAbove(int x, int z, int maxY, int bottomSectionY) {
        int i = SectionPos.blockToSectionCoord(x);
        int j = SectionPos.blockToSectionCoord(z);
        int k = Math.max(
            Math.max(this.getLowestSourceY(x - 1, z, Integer.MIN_VALUE), this.getLowestSourceY(x + 1, z, Integer.MIN_VALUE)),
            Math.max(this.getLowestSourceY(x, z - 1, Integer.MIN_VALUE), this.getLowestSourceY(x, z + 1, Integer.MIN_VALUE))
        );
        int l = Math.max(maxY, bottomSectionY);

        for (long m = SectionPos.asLong(i, SectionPos.blockToSectionCoord(l), j); !this.storage.isAboveData(m); m = SectionPos.offset(m, Direction.UP)) {
            if (this.storage.storingLightForSection(m)) {
                int n = SectionPos.sectionToBlockCoord(SectionPos.y(m));
                int o = n + 15;

                for (int p = Math.max(n, l); p <= o; p++) {
                    long q = BlockPos.asLong(x, p, z);
                    if (isSourceLevel(this.storage.getStoredLevel(q))) {
                        return;
                    }

                    this.storage.setStoredLevel(q, 15);
                    if (p < k || p == maxY) {
                        this.enqueueIncrease(q, ADD_SKY_SOURCE_ENTRY);
                    }
                }
            }
        }
    }

    @Override
    protected void propagateIncrease(long packedPos, long queueEntry, int lightLevel) {
        BlockState blockState = null;
        int i = this.countEmptySectionsBelowIfAtBorder(packedPos);

        for (Direction direction : PROPAGATION_DIRECTIONS) {
            if (LightEngine.QueueEntry.shouldPropagateInDirection(queueEntry, direction)) {
                long l = BlockPos.offset(packedPos, direction);
                if (this.storage.storingLightForSection(SectionPos.blockToSection(l))) {
                    int j = this.storage.getStoredLevel(l);
                    int k = lightLevel - 1;
                    if (k > j) {
                        this.mutablePos.set(l);
                        BlockState blockState2 = this.getState(this.mutablePos);
                        int m = lightLevel - this.getOpacity(blockState2, this.mutablePos);
                        if (m > j) {
                            if (blockState == null) {
                                blockState = LightEngine.QueueEntry.isFromEmptyShape(queueEntry)
                                    ? Blocks.AIR.defaultBlockState()
                                    : this.getState(this.mutablePos.set(packedPos));
                            }

                            if (!this.shapeOccludes(packedPos, blockState, l, blockState2, direction)) {
                                this.storage.setStoredLevel(l, m);
                                if (m > 1) {
                                    this.enqueueIncrease(
                                        l, LightEngine.QueueEntry.increaseSkipOneDirection(m, isEmptyShape(blockState2), direction.getOpposite())
                                    );
                                }

                                this.propagateFromEmptySections(l, direction, m, true, i);
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    protected void propagateDecrease(long packedPos, long lightLevel) {
        int i = this.countEmptySectionsBelowIfAtBorder(packedPos);
        int j = LightEngine.QueueEntry.getFromLevel(lightLevel);

        for (Direction direction : PROPAGATION_DIRECTIONS) {
            if (LightEngine.QueueEntry.shouldPropagateInDirection(lightLevel, direction)) {
                long l = BlockPos.offset(packedPos, direction);
                if (this.storage.storingLightForSection(SectionPos.blockToSection(l))) {
                    int k = this.storage.getStoredLevel(l);
                    if (k != 0) {
                        if (k <= j - 1) {
                            this.storage.setStoredLevel(l, 0);
                            this.enqueueDecrease(l, LightEngine.QueueEntry.decreaseSkipOneDirection(k, direction.getOpposite()));
                            this.propagateFromEmptySections(l, direction, k, false, i);
                        } else {
                            this.enqueueIncrease(l, LightEngine.QueueEntry.increaseOnlyOneDirection(k, false, direction.getOpposite()));
                        }
                    }
                }
            }
        }
    }

    private int countEmptySectionsBelowIfAtBorder(long packedPos) {
        int i = BlockPos.getY(packedPos);
        int j = SectionPos.sectionRelative(i);
        if (j != 0) {
            return 0;
        }

        int k = BlockPos.getX(packedPos);
        int l = BlockPos.getZ(packedPos);
        int m = SectionPos.sectionRelative(k);
        int n = SectionPos.sectionRelative(l);
        if (m != 0 && m != 15 && n != 0 && n != 15) {
            return 0;
        }

        int o = SectionPos.blockToSectionCoord(k);
        int p = SectionPos.blockToSectionCoord(i);
        int q = SectionPos.blockToSectionCoord(l);
        int r = 0;

        while (!this.storage.storingLightForSection(SectionPos.asLong(o, p - r - 1, q)) && this.storage.hasLightDataAtOrBelow(p - r - 1)) {
            r++;
        }

        return r;
    }

    private void propagateFromEmptySections(long packedPos, Direction direction, int level, boolean shouldIncrease, int emptySections) {
        if (emptySections != 0) {
            int i = BlockPos.getX(packedPos);
            int j = BlockPos.getZ(packedPos);
            if (crossedSectionEdge(direction, SectionPos.sectionRelative(i), SectionPos.sectionRelative(j))) {
                int k = BlockPos.getY(packedPos);
                int l = SectionPos.blockToSectionCoord(i);
                int m = SectionPos.blockToSectionCoord(j);
                int n = SectionPos.blockToSectionCoord(k) - 1;
                int o = n - emptySections + 1;

                while (n >= o) {
                    if (!this.storage.storingLightForSection(SectionPos.asLong(l, n, m))) {
                        n--;
                    } else {
                        int p = SectionPos.sectionToBlockCoord(n);

                        for (int q = 15; q >= 0; q--) {
                            long r = BlockPos.asLong(i, p + q, j);
                            if (shouldIncrease) {
                                this.storage.setStoredLevel(r, level);
                                if (level > 1) {
                                    this.enqueueIncrease(r, LightEngine.QueueEntry.increaseSkipOneDirection(level, true, direction.getOpposite()));
                                }
                            } else {
                                this.storage.setStoredLevel(r, 0);
                                this.enqueueDecrease(r, LightEngine.QueueEntry.decreaseSkipOneDirection(level, direction.getOpposite()));
                            }
                        }

                        n--;
                    }
                }
            }
        }
    }

    private static boolean crossedSectionEdge(Direction direction, int x, int z) {
        return switch (direction) {
            case NORTH -> z == 15;
            case SOUTH -> z == 0;
            case WEST -> x == 15;
            case EAST -> x == 0;
            default -> false;
        };
    }

    @Override
    public void setLightEnabled(ChunkPos chunkPos, boolean lightEnabled) {
        super.setLightEnabled(chunkPos, lightEnabled);
        if (lightEnabled) {
            ChunkSkyLightSources chunkSkyLightSources = Objects.requireNonNullElse(this.getChunkSources(chunkPos.x, chunkPos.z), this.emptyChunkSources);
            int i = chunkSkyLightSources.getHighestLowestSourceY() - 1;
            int j = SectionPos.blockToSectionCoord(i) + 1;
            long l = SectionPos.getZeroNode(chunkPos.x, chunkPos.z);
            int k = this.storage.getTopSectionY(l);
            int m = Math.max(this.storage.getBottomSectionY(), j);

            for (int n = k - 1; n >= m; n--) {
                DataLayer dataLayer = this.storage.getDataLayerToWrite(SectionPos.asLong(chunkPos.x, n, chunkPos.z));
                if (dataLayer != null && dataLayer.isEmpty()) {
                    dataLayer.fill(15);
                }
            }
        }
    }

    @Override
    public void propagateLightSources(ChunkPos chunkPos) {
        long l = SectionPos.getZeroNode(chunkPos.x, chunkPos.z);
        this.storage.setLightEnabled(l, true);
        ChunkSkyLightSources chunkSkyLightSources = Objects.requireNonNullElse(this.getChunkSources(chunkPos.x, chunkPos.z), this.emptyChunkSources);
        ChunkSkyLightSources chunkSkyLightSources2 = Objects.requireNonNullElse(this.getChunkSources(chunkPos.x, chunkPos.z - 1), this.emptyChunkSources);
        ChunkSkyLightSources chunkSkyLightSources3 = Objects.requireNonNullElse(this.getChunkSources(chunkPos.x, chunkPos.z + 1), this.emptyChunkSources);
        ChunkSkyLightSources chunkSkyLightSources4 = Objects.requireNonNullElse(this.getChunkSources(chunkPos.x - 1, chunkPos.z), this.emptyChunkSources);
        ChunkSkyLightSources chunkSkyLightSources5 = Objects.requireNonNullElse(this.getChunkSources(chunkPos.x + 1, chunkPos.z), this.emptyChunkSources);
        int i = this.storage.getTopSectionY(l);
        int j = this.storage.getBottomSectionY();
        int k = SectionPos.sectionToBlockCoord(chunkPos.x);
        int m = SectionPos.sectionToBlockCoord(chunkPos.z);

        for (int n = i - 1; n >= j; n--) {
            long o = SectionPos.asLong(chunkPos.x, n, chunkPos.z);
            DataLayer dataLayer = this.storage.getDataLayerToWrite(o);
            if (dataLayer != null) {
                int p = SectionPos.sectionToBlockCoord(n);
                int q = p + 15;
                boolean bl = false;

                for (int r = 0; r < 16; r++) {
                    for (int s = 0; s < 16; s++) {
                        int t = chunkSkyLightSources.getLowestSourceY(s, r);
                        if (t <= q) {
                            int u = r == 0 ? chunkSkyLightSources2.getLowestSourceY(s, 15) : chunkSkyLightSources.getLowestSourceY(s, r - 1);
                            int v = r == 15 ? chunkSkyLightSources3.getLowestSourceY(s, 0) : chunkSkyLightSources.getLowestSourceY(s, r + 1);
                            int w = s == 0 ? chunkSkyLightSources4.getLowestSourceY(15, r) : chunkSkyLightSources.getLowestSourceY(s - 1, r);
                            int x = s == 15 ? chunkSkyLightSources5.getLowestSourceY(0, r) : chunkSkyLightSources.getLowestSourceY(s + 1, r);
                            int y = Math.max(Math.max(u, v), Math.max(w, x));

                            for (int z = q; z >= Math.max(p, t); z--) {
                                dataLayer.set(s, SectionPos.sectionRelative(z), r, 15);
                                if (z == t || z < y) {
                                    long aa = BlockPos.asLong(k + s, z, m + r);
                                    this.enqueueIncrease(aa, LightEngine.QueueEntry.increaseSkySourceInDirections(z == t, z < u, z < v, z < w, z < x));
                                }
                            }

                            if (t < p) {
                                bl = true;
                            }
                        }
                    }
                }

                if (!bl) {
                    break;
                }
            }
        }
    }
}
