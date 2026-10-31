package net.minecraft.world.entity.vehicle;

import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class DismountHelper {
    public static int[][] offsetsForDirection(Direction direction) {
        Direction direction2 = direction.getClockWise();
        Direction direction3 = direction2.getOpposite();
        Direction direction4 = direction.getOpposite();
        return new int[][]{
            {direction2.getStepX(), direction2.getStepZ()},
            {direction3.getStepX(), direction3.getStepZ()},
            {direction4.getStepX() + direction2.getStepX(), direction4.getStepZ() + direction2.getStepZ()},
            {direction4.getStepX() + direction3.getStepX(), direction4.getStepZ() + direction3.getStepZ()},
            {direction.getStepX() + direction2.getStepX(), direction.getStepZ() + direction2.getStepZ()},
            {direction.getStepX() + direction3.getStepX(), direction.getStepZ() + direction3.getStepZ()},
            {direction4.getStepX(), direction4.getStepZ()},
            {direction.getStepX(), direction.getStepZ()}
        };
    }

    public static boolean isBlockFloorValid(double distance) {
        return !Double.isInfinite(distance) && distance < 1.0;
    }

    public static boolean canDismountTo(CollisionGetter level, LivingEntity passenger, AABB boundingBox) {
        for (VoxelShape voxelShape : level.getBlockCollisions(passenger, boundingBox)) {
            if (!voxelShape.isEmpty()) {
                return false;
            }
        }

        return level.getWorldBorder().isWithinBounds(boundingBox);
    }

    public static boolean canDismountTo(CollisionGetter level, Vec3 offset, LivingEntity passenger, Pose pose) {
        return canDismountTo(level, passenger, passenger.getLocalBoundsForPose(pose).move(offset));
    }

    public static VoxelShape nonClimbableShape(BlockGetter level, BlockPos pos) {
        BlockState blockState = level.getBlockState(pos);
        return !blockState.is(BlockTags.CLIMBABLE) && (!(blockState.getBlock() instanceof TrapDoorBlock) || !blockState.getValue(TrapDoorBlock.OPEN))
            ? blockState.getCollisionShape(level, pos)
            : Shapes.empty();
    }

    public static double findCeilingFrom(BlockPos pos, int ceiling, Function<BlockPos, VoxelShape> shapeForPos) {
        BlockPos.MutableBlockPos mutableBlockPos = pos.mutable();
        int i = 0;

        while (i < ceiling) {
            VoxelShape voxelShape = shapeForPos.apply(mutableBlockPos);
            if (!voxelShape.isEmpty()) {
                return pos.getY() + i + voxelShape.min(Direction.Axis.Y);
            }

            i++;
            mutableBlockPos.move(Direction.UP);
        }

        return Double.POSITIVE_INFINITY;
    }

    @Nullable
    public static Vec3 findSafeDismountLocation(EntityType<?> entityType, CollisionGetter level, BlockPos pos, boolean onlySafePositions) {
        if (onlySafePositions && entityType.isBlockDangerous(level.getBlockState(pos))) {
            return null;
        }

        double d = level.getBlockFloorHeight(nonClimbableShape(level, pos), () -> nonClimbableShape(level, pos.below()));
        if (!isBlockFloorValid(d)) {
            return null;
        }

        if (onlySafePositions && d <= 0.0 && entityType.isBlockDangerous(level.getBlockState(pos.below()))) {
            return null;
        }

        Vec3 vec3 = Vec3.upFromBottomCenterOf(pos, d);
        AABB aABB = entityType.getDimensions().makeBoundingBox(vec3);

        for (VoxelShape voxelShape : level.getBlockCollisions(null, aABB)) {
            if (!voxelShape.isEmpty()) {
                return null;
            }
        }

        if (entityType != EntityType.PLAYER
            || !level.getBlockState(pos).is(BlockTags.INVALID_SPAWN_INSIDE) && !level.getBlockState(pos.above()).is(BlockTags.INVALID_SPAWN_INSIDE)) {
            return !level.getWorldBorder().isWithinBounds(aABB) ? null : vec3;
        } else {
            return null;
        }
    }
}
