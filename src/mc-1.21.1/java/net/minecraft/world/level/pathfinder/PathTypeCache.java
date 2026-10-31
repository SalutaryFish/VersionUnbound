package net.minecraft.world.level.pathfinder;

import it.unimi.dsi.fastutil.HashCommon;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import org.jetbrains.annotations.Nullable;

public class PathTypeCache {
    private static final int SIZE = 4096;
    private static final int MASK = 4095;
    private final long[] positions = new long[4096];
    private final PathType[] pathTypes = new PathType[4096];

    public PathType getOrCompute(BlockGetter level, BlockPos pos) {
        long l = pos.asLong();
        int i = index(l);
        PathType pathType = this.get(i, l);
        return pathType != null ? pathType : this.compute(level, pos, i, l);
    }

    @Nullable
    private PathType get(int index, long pos) {
        return this.positions[index] == pos ? this.pathTypes[index] : null;
    }

    private PathType compute(BlockGetter level, BlockPos pos, int index, long packedPos) {
        PathType pathType = WalkNodeEvaluator.getPathTypeFromState(level, pos);
        this.positions[index] = packedPos;
        this.pathTypes[index] = pathType;
        return pathType;
    }

    public void invalidate(BlockPos pos) {
        long l = pos.asLong();
        int i = index(l);
        if (this.positions[i] == l) {
            this.pathTypes[i] = null;
        }
    }

    private static int index(long pos) {
        return (int)HashCommon.mix(pos) & 4095;
    }
}
