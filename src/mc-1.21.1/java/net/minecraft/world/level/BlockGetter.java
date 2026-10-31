package net.minecraft.world.level;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public interface BlockGetter extends LevelHeightAccessor {
    @Nullable
    BlockEntity getBlockEntity(BlockPos var1);

    default <T extends BlockEntity> Optional<T> getBlockEntity(BlockPos pos, BlockEntityType<T> blockEntityType) {
        BlockEntity blockEntity = this.getBlockEntity(pos);
        return blockEntity != null && blockEntity.getType() == blockEntityType ? Optional.of((T)blockEntity) : Optional.empty();
    }

    BlockState getBlockState(BlockPos var1);

    FluidState getFluidState(BlockPos var1);

    default int getLightEmission(BlockPos pos) {
        return this.getBlockState(pos).getLightEmission();
    }

    default int getMaxLightLevel() {
        return 15;
    }

    default Stream<BlockState> getBlockStates(AABB area) {
        return BlockPos.betweenClosedStream(area).map(this::getBlockState);
    }

    default BlockHitResult isBlockInLine(ClipBlockStateContext context) {
        return traverseBlocks(
            context.getFrom(),
            context.getTo(),
            context,
            (traverseContext, traversePos) -> {
                BlockState blockState = this.getBlockState(traversePos);
                Vec3 vec3 = traverseContext.getFrom().subtract(traverseContext.getTo());
                return traverseContext.isTargetBlock().test(blockState)
                    ? new BlockHitResult(
                        traverseContext.getTo(), Direction.getNearest(vec3.x, vec3.y, vec3.z), BlockPos.containing(traverseContext.getTo()), false
                    )
                    : null;
            },
            failContext -> {
                Vec3 vec3 = failContext.getFrom().subtract(failContext.getTo());
                return BlockHitResult.miss(failContext.getTo(), Direction.getNearest(vec3.x, vec3.y, vec3.z), BlockPos.containing(failContext.getTo()));
            }
        );
    }

    /**
     * Checks if there's block between {@code from} and {@code to} of context.
     * This uses the collision shape of provided block.
     */
    default BlockHitResult clip(ClipContext context) {
        return traverseBlocks(context.getFrom(), context.getTo(), context, (traverseContext, traversePos) -> {
            BlockState blockState = this.getBlockState(traversePos);
            FluidState fluidState = this.getFluidState(traversePos);
            Vec3 vec3 = traverseContext.getFrom();
            Vec3 vec32 = traverseContext.getTo();
            VoxelShape voxelShape = traverseContext.getBlockShape(blockState, this, traversePos);
            BlockHitResult blockHitResult = this.clipWithInteractionOverride(vec3, vec32, traversePos, voxelShape, blockState);
            VoxelShape voxelShape2 = traverseContext.getFluidShape(fluidState, this, traversePos);
            BlockHitResult blockHitResult2 = voxelShape2.clip(vec3, vec32, traversePos);
            double d = blockHitResult == null ? Double.MAX_VALUE : traverseContext.getFrom().distanceToSqr(blockHitResult.getLocation());
            double e = blockHitResult2 == null ? Double.MAX_VALUE : traverseContext.getFrom().distanceToSqr(blockHitResult2.getLocation());
            return d <= e ? blockHitResult : blockHitResult2;
        }, failContext -> {
            Vec3 vec3 = failContext.getFrom().subtract(failContext.getTo());
            return BlockHitResult.miss(failContext.getTo(), Direction.getNearest(vec3.x, vec3.y, vec3.z), BlockPos.containing(failContext.getTo()));
        });
    }

    @Nullable
    default BlockHitResult clipWithInteractionOverride(Vec3 startVec, Vec3 endVec, BlockPos pos, VoxelShape shape, BlockState state) {
        BlockHitResult blockHitResult = shape.clip(startVec, endVec, pos);
        if (blockHitResult != null) {
            BlockHitResult blockHitResult2 = state.getInteractionShape(this, pos).clip(startVec, endVec, pos);
            if (blockHitResult2 != null
                && blockHitResult2.getLocation().subtract(startVec).lengthSqr() < blockHitResult.getLocation().subtract(startVec).lengthSqr()) {
                return blockHitResult.withDirection(blockHitResult2.getDirection());
            }
        }

        return blockHitResult;
    }

    default double getBlockFloorHeight(VoxelShape shape, Supplier<VoxelShape> belowShapeSupplier) {
        if (!shape.isEmpty()) {
            return shape.max(Direction.Axis.Y);
        }

        double d = belowShapeSupplier.get().max(Direction.Axis.Y);
        return d >= 1.0 ? d - 1.0 : Double.NEGATIVE_INFINITY;
    }

    default double getBlockFloorHeight(BlockPos pos) {
        return this.getBlockFloorHeight(this.getBlockState(pos).getCollisionShape(this, pos), () -> {
            BlockPos blockPos2 = pos.below();
            return this.getBlockState(blockPos2).getCollisionShape(this, blockPos2);
        });
    }

    static <T, C> T traverseBlocks(Vec3 from, Vec3 to, C context, BiFunction<C, BlockPos, T> tester, Function<C, T> onFail) {
        if (from.equals(to)) {
            return onFail.apply(context);
        }

        double d = Mth.lerp(-1.0E-7, to.x, from.x);
        double e = Mth.lerp(-1.0E-7, to.y, from.y);
        double f = Mth.lerp(-1.0E-7, to.z, from.z);
        double g = Mth.lerp(-1.0E-7, from.x, to.x);
        double h = Mth.lerp(-1.0E-7, from.y, to.y);
        double i = Mth.lerp(-1.0E-7, from.z, to.z);
        int j = Mth.floor(g);
        int k = Mth.floor(h);
        int l = Mth.floor(i);
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos(j, k, l);
        T object = tester.apply(context, mutableBlockPos);
        if (object != null) {
            return object;
        }

        double m = d - g;
        double n = e - h;
        double o = f - i;
        int p = Mth.sign(m);
        int q = Mth.sign(n);
        int r = Mth.sign(o);
        double s = p == 0 ? Double.MAX_VALUE : p / m;
        double t = q == 0 ? Double.MAX_VALUE : q / n;
        double u = r == 0 ? Double.MAX_VALUE : r / o;
        double v = s * (p > 0 ? 1.0 - Mth.frac(g) : Mth.frac(g));
        double w = t * (q > 0 ? 1.0 - Mth.frac(h) : Mth.frac(h));
        double x = u * (r > 0 ? 1.0 - Mth.frac(i) : Mth.frac(i));

        while (v <= 1.0 || w <= 1.0 || x <= 1.0) {
            if (v < w) {
                if (v < x) {
                    j += p;
                    v += s;
                } else {
                    l += r;
                    x += u;
                }
            } else if (w < x) {
                k += q;
                w += t;
            } else {
                l += r;
                x += u;
            }

            T object2 = tester.apply(context, mutableBlockPos.set(j, k, l));
            if (object2 != null) {
                return object2;
            }
        }

        return onFail.apply(context);
    }
}
