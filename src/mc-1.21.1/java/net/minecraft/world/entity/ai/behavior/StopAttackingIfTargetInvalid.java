package net.minecraft.world.entity.ai.behavior;

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

public class StopAttackingIfTargetInvalid {
    private static final int TIMEOUT_TO_GET_WITHIN_ATTACK_RANGE = 200;

    public static <E extends Mob> BehaviorControl<E> create(BiConsumer<E, LivingEntity> onStopAttacking) {
        return create(entity -> false, onStopAttacking, true);
    }

    public static <E extends Mob> BehaviorControl<E> create(Predicate<LivingEntity> canStopAttacking) {
        return create(canStopAttacking, (entity, target) -> {}, true);
    }

    public static <E extends Mob> BehaviorControl<E> create() {
        return create(entity -> false, (entity, target) -> {}, true);
    }

    public static <E extends Mob> BehaviorControl<E> create(
        Predicate<LivingEntity> canStopAttacking, BiConsumer<E, LivingEntity> onStopAttacking, boolean canGrowTiredOfTryingToReachTarget
    ) {
        return BehaviorBuilder.create(
            instance -> instance.group(instance.present(MemoryModuleType.ATTACK_TARGET), instance.registered(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE))
                .apply(
                    instance,
                    (attackTarget, cantReachWalkTargetSince) -> (level, mob, gameTime) -> {
                        LivingEntity livingEntity = instance.get(attackTarget);
                        if (mob.canAttack(livingEntity)
                            && (!canGrowTiredOfTryingToReachTarget || !isTiredOfTryingToReachTarget(mob, instance.tryGet(cantReachWalkTargetSince)))
                            && livingEntity.isAlive()
                            && livingEntity.level() == mob.level()
                            && !canStopAttacking.test(livingEntity)) {
                            return true;
                        }

                        onStopAttacking.accept(mob, livingEntity);
                        attackTarget.erase();
                        return true;
                    }
                )
        );
    }

    private static boolean isTiredOfTryingToReachTarget(LivingEntity entity, Optional<Long> timeSinceInvalidTarget) {
        return timeSinceInvalidTarget.isPresent() && entity.level().getGameTime() - timeSinceInvalidTarget.get() > 200L;
    }
}
