package net.minecraft.world.entity.ai.behavior;

import java.util.Optional;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.phys.Vec3;

public class SetWalkTargetAwayFrom {
    public static BehaviorControl<PathfinderMob> pos(
        MemoryModuleType<BlockPos> walkTargetAwayFromMemory, float speedModifier, int desiredDistance, boolean hasTarget
    ) {
        return create(walkTargetAwayFromMemory, speedModifier, desiredDistance, hasTarget, Vec3::atBottomCenterOf);
    }

    public static OneShot<PathfinderMob> entity(
        MemoryModuleType<? extends Entity> walkTargetAwayFromMemory, float speedModifier, int desiredDistance, boolean hasTarget
    ) {
        return create(walkTargetAwayFromMemory, speedModifier, desiredDistance, hasTarget, Entity::position);
    }

    private static <T> OneShot<PathfinderMob> create(
        MemoryModuleType<T> walkTargetAwayFromMemory, float speedModifier, int desiredDistance, boolean hasTarget, Function<T, Vec3> toPosition
    ) {
        return BehaviorBuilder.create(
            instance -> instance.group(instance.registered(MemoryModuleType.WALK_TARGET), instance.present(walkTargetAwayFromMemory))
                .apply(instance, (walkTarget, walkTargetAwayFrom) -> (level, mob, gameTime) -> {
                    Optional<WalkTarget> optional = instance.tryGet(walkTarget);
                    if (optional.isPresent() && !hasTarget) {
                        return false;
                    }

                    Vec3 vec3 = mob.position();
                    Vec3 vec32 = toPosition.apply(instance.get(walkTargetAwayFrom));
                    if (!vec3.closerThan(vec32, desiredDistance)) {
                        return false;
                    }

                    if (optional.isPresent() && optional.get().getSpeedModifier() == speedModifier) {
                        Vec3 vec33 = optional.get().getTarget().currentPosition().subtract(vec3);
                        Vec3 vec34 = vec32.subtract(vec3);
                        if (vec33.dot(vec34) < 0.0) {
                            return false;
                        }
                    }

                    for (int j = 0; j < 10; j++) {
                        Vec3 vec34 = LandRandomPos.getPosAway(mob, 16, 7, vec32);
                        if (vec34 != null) {
                            walkTarget.set(new WalkTarget(vec34, speedModifier, 0));
                            break;
                        }
                    }

                    return true;
                })
        );
    }
}
