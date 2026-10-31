package net.minecraft.world.entity.ai.util;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.phys.Vec3;

public class HoverRandomPos {
    /**
     * Tries to generate a random position a couple different ways, and if failing, sees if swimming vertically is an option.
     */
    @Nullable
    public static Vec3 getPos(PathfinderMob mob, int radius, int yRange, double x, double z, float amplifier, int maxSwimUp, int minSwimUp) {
        boolean bl = GoalUtils.mobRestricted(mob, radius);
        return RandomPos.generateRandomPos(
            mob,
            () -> {
                BlockPos blockPos = RandomPos.generateRandomDirectionWithinRadians(mob.getRandom(), radius, yRange, 0, x, z, amplifier);
                if (blockPos == null) {
                    return null;
                }

                BlockPos blockPos2 = LandRandomPos.generateRandomPosTowardDirection(mob, radius, bl, blockPos);
                if (blockPos2 == null) {
                    return null;
                }

                blockPos2 = RandomPos.moveUpToAboveSolid(
                    blockPos2,
                    mob.getRandom().nextInt(maxSwimUp - minSwimUp + 1) + minSwimUp,
                    mob.level().getMaxBuildHeight(),
                    pos -> GoalUtils.isSolid(mob, pos)
                );
                return !GoalUtils.isWater(mob, blockPos2) && !GoalUtils.hasMalus(mob, blockPos2) ? blockPos2 : null;
            }
        );
    }
}
