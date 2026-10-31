package net.minecraft.world.entity.ai.util;

import java.util.function.ToDoubleFunction;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.phys.Vec3;

public class LandRandomPos {
    @Nullable
    public static Vec3 getPos(PathfinderMob mob, int radius, int verticalRange) {
        return getPos(mob, radius, verticalRange, mob::getWalkTargetValue);
    }

    @Nullable
    public static Vec3 getPos(PathfinderMob mob, int radius, int yRange, ToDoubleFunction<BlockPos> toDoubleFunction) {
        boolean bl = GoalUtils.mobRestricted(mob, radius);
        return RandomPos.generateRandomPos(() -> {
            BlockPos blockPos = RandomPos.generateRandomDirection(mob.getRandom(), radius, yRange);
            BlockPos blockPos2 = generateRandomPosTowardDirection(mob, radius, bl, blockPos);
            return blockPos2 == null ? null : movePosUpOutOfSolid(mob, blockPos2);
        }, toDoubleFunction);
    }

    @Nullable
    public static Vec3 getPosTowards(PathfinderMob mob, int radius, int yRange, Vec3 vectorPosition) {
        Vec3 vec3 = vectorPosition.subtract(mob.getX(), mob.getY(), mob.getZ());
        boolean bl = GoalUtils.mobRestricted(mob, radius);
        return getPosInDirection(mob, radius, yRange, vec3, bl);
    }

    @Nullable
    public static Vec3 getPosAway(PathfinderMob mob, int radius, int yRange, Vec3 vectorPosition) {
        Vec3 vec3 = mob.position().subtract(vectorPosition);
        boolean bl = GoalUtils.mobRestricted(mob, radius);
        return getPosInDirection(mob, radius, yRange, vec3, bl);
    }

    @Nullable
    private static Vec3 getPosInDirection(PathfinderMob mob, int radius, int yRange, Vec3 vectorPosition, boolean shortCircuit) {
        return RandomPos.generateRandomPos(
            mob,
            () -> {
                BlockPos blockPos = RandomPos.generateRandomDirectionWithinRadians(
                    mob.getRandom(), radius, yRange, 0, vectorPosition.x, vectorPosition.z, (float) (Math.PI / 2)
                );
                if (blockPos == null) {
                    return null;
                }

                BlockPos blockPos2 = generateRandomPosTowardDirection(mob, radius, shortCircuit, blockPos);
                return blockPos2 == null ? null : movePosUpOutOfSolid(mob, blockPos2);
            }
        );
    }

    @Nullable
    public static BlockPos movePosUpOutOfSolid(PathfinderMob mob, BlockPos pos) {
        pos = RandomPos.moveUpOutOfSolid(pos, mob.level().getMaxBuildHeight(), posx -> GoalUtils.isSolid(mob, posx));
        return !GoalUtils.isWater(mob, pos) && !GoalUtils.hasMalus(mob, pos) ? pos : null;
    }

    @Nullable
    public static BlockPos generateRandomPosTowardDirection(PathfinderMob mob, int radius, boolean shortCircuit, BlockPos pos) {
        BlockPos blockPos = RandomPos.generateRandomPosTowardDirection(mob, radius, mob.getRandom(), pos);
        return !GoalUtils.isOutsideLimits(blockPos, mob)
                && !GoalUtils.isRestricted(shortCircuit, mob, blockPos)
                && !GoalUtils.isNotStable(mob.getNavigation(), blockPos)
            ? blockPos
            : null;
    }
}
