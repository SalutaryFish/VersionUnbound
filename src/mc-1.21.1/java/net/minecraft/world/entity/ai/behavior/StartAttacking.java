package net.minecraft.world.entity.ai.behavior;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

public class StartAttacking {
    public static <E extends Mob> BehaviorControl<E> create(Function<E, Optional<? extends LivingEntity>> targetFinder) {
        return create(entity -> true, targetFinder);
    }

    public static <E extends Mob> BehaviorControl<E> create(Predicate<E> canAttack, Function<E, Optional<? extends LivingEntity>> targetFinder) {
        return BehaviorBuilder.create(
            instance -> instance.group(instance.absent(MemoryModuleType.ATTACK_TARGET), instance.registered(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE))
                .apply(instance, (attackTarget, cantReachWalkTargetSince) -> (level, mob, gameTime) -> {
                    if (!canAttack.test(mob)) {
                        return false;
                    }

                    Optional<? extends LivingEntity> optional = targetFinder.apply(mob);
                    if (optional.isEmpty()) {
                        return false;
                    }

                    LivingEntity livingEntity = optional.get();
                    if (!mob.canAttack(livingEntity)) {
                        return false;
                    }

                    attackTarget.set(livingEntity);
                    cantReachWalkTargetSince.erase();
                    return true;
                })
        );
    }
}
