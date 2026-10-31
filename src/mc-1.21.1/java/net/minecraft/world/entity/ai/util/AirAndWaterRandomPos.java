package net.minecraft.world.entity.ai.util;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.phys.Vec3;

public class AirAndWaterRandomPos {
    @Nullable
    public static Vec3 getPos(PathfinderMob mob, int maxDistance, int yRange, int y, double x, double z, double amplifier) {
        boolean bl = GoalUtils.mobRestricted(mob, maxDistance);
        return RandomPos.generateRandomPos(mob, () -> generateRandomPos(mob, maxDistance, yRange, y, x, z, amplifier, bl));
    }

    @Nullable
    public static BlockPos generateRandomPos(PathfinderMob mob, int maxDistance, int yRange, int y, double x, double z, double amplifier, boolean shortCircuit) {
        BlockPos blockPos = RandomPos.generateRandomDirectionWithinRadians(mob.getRandom(), maxDistance, yRange, y, x, z, amplifier);
        if (blockPos == null) {
            return null;
        } else {
            BlockPos blockPos2 = RandomPos.generateRandomPosTowardDirection(mob, maxDistance, mob.getRandom(), blockPos);
            if (!GoalUtils.isOutsideLimits(blockPos2, mob) && !GoalUtils.isRestricted(shortCircuit, mob, blockPos2)) {
                blockPos2 = RandomPos.moveUpOutOfSolid(blockPos2, mob.level().getMaxBuildHeight(), pos -> GoalUtils.isSolid(mob, pos));
                return GoalUtils.hasMalus(mob, blockPos2) ? null : blockPos2;
            } else {
                return null;
            }
        }
    }
}
