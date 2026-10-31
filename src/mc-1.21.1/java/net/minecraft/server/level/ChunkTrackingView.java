package net.minecraft.server.level;

import com.google.common.annotations.VisibleForTesting;
import java.util.function.Consumer;
import net.minecraft.world.level.ChunkPos;

public interface ChunkTrackingView {
    ChunkTrackingView EMPTY = new ChunkTrackingView() {
        @Override
        public boolean contains(int i, int j, boolean bl) {
            return false;
        }

        @Override
        public void forEach(Consumer<ChunkPos> consumer) {
        }
    };

    static ChunkTrackingView of(ChunkPos center, int viewDistance) {
        return new ChunkTrackingView.Positioned(center, viewDistance);
    }

    /**
     * Calculates the chunks that the player needs to drop in the {@code oldChunkTrackingView} and the chunks that need to be sent for the {@code newChunkTrackingView}. The chunks that overlap in both views can be kept.
     */
    static void difference(
        ChunkTrackingView oldChunkTrackingView, ChunkTrackingView newChunkTrackingView, Consumer<ChunkPos> chunkMarker, Consumer<ChunkPos> chunkDropper
    ) {
        if (!oldChunkTrackingView.equals(newChunkTrackingView)) {
            if (oldChunkTrackingView instanceof ChunkTrackingView.Positioned positioned
                && newChunkTrackingView instanceof ChunkTrackingView.Positioned positioned2
                && positioned.squareIntersects(positioned2)) {
                int i = Math.min(positioned.minX(), positioned2.minX());
                int j = Math.min(positioned.minZ(), positioned2.minZ());
                int k = Math.max(positioned.maxX(), positioned2.maxX());
                int l = Math.max(positioned.maxZ(), positioned2.maxZ());

                for (int m = i; m <= k; m++) {
                    for (int n = j; n <= l; n++) {
                        boolean bl = positioned.contains(m, n);
                        boolean bl2 = positioned2.contains(m, n);
                        if (bl != bl2) {
                            if (bl2) {
                                chunkMarker.accept(new ChunkPos(m, n));
                            } else {
                                chunkDropper.accept(new ChunkPos(m, n));
                            }
                        }
                    }
                }
            } else {
                oldChunkTrackingView.forEach(chunkDropper);
                newChunkTrackingView.forEach(chunkMarker);
            }
        }
    }

    default boolean contains(ChunkPos chunkPos) {
        return this.contains(chunkPos.x, chunkPos.z);
    }

    default boolean contains(int x, int z) {
        return this.contains(x, z, true);
    }

    boolean contains(int var1, int var2, boolean var3);

    void forEach(Consumer<ChunkPos> var1);

    default boolean isInViewDistance(int x, int z) {
        return this.contains(x, z, false);
    }

    static boolean isInViewDistance(int centerX, int centerZ, int viewDistance, int x, int z) {
        return isWithinDistance(centerX, centerZ, viewDistance, x, z, false);
    }

    /**
     * Check if a chunk {@code (x,z)} is within a {@code viewDistance} which is centered on {@code (centerX, centerZ)}
     */
    static boolean isWithinDistance(int centerX, int centerZ, int viewDistance, int x, int z, boolean includeOuterChunksAdjacentToViewBorder) {
        int i = Math.max(0, Math.abs(x - centerX) - 1);
        int j = Math.max(0, Math.abs(z - centerZ) - 1);
        long l = Math.max(0, Math.max(i, j) - (includeOuterChunksAdjacentToViewBorder ? 1 : 0));
        long m = Math.min(i, j);
        long n = m * m + l * l;
        int k = viewDistance * viewDistance;
        return n < k;
    }

    record Positioned(ChunkPos center, int viewDistance) implements ChunkTrackingView {
        int minX() {
            return this.center.x - this.viewDistance - 1;
        }

        int minZ() {
            return this.center.z - this.viewDistance - 1;
        }

        int maxX() {
            return this.center.x + this.viewDistance + 1;
        }

        int maxZ() {
            return this.center.z + this.viewDistance + 1;
        }

        /**
         * Determines if another {@link ChunkTrackingView}'s bounds intersects with its own
         */
        @VisibleForTesting
        protected boolean squareIntersects(ChunkTrackingView.Positioned other) {
            return this.minX() <= other.maxX() && this.maxX() >= other.minX() && this.minZ() <= other.maxZ() && this.maxZ() >= other.minZ();
        }

        @Override
        public boolean contains(int x, int z, boolean includeOuterChunksAdjacentToViewBorder) {
            return ChunkTrackingView.isWithinDistance(this.center.x, this.center.z, this.viewDistance, x, z, includeOuterChunksAdjacentToViewBorder);
        }

        @Override
        public void forEach(Consumer<ChunkPos> action) {
            for (int i = this.minX(); i <= this.maxX(); i++) {
                for (int j = this.minZ(); j <= this.maxZ(); j++) {
                    if (this.contains(i, j)) {
                        action.accept(new ChunkPos(i, j));
                    }
                }
            }
        }
    }
}
