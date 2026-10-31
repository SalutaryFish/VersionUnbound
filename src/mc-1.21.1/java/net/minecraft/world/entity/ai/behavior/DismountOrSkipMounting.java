package net.minecraft.world.entity.ai.behavior;

import java.util.function.BiPredicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

public class DismountOrSkipMounting {
    public static <E extends LivingEntity> BehaviorControl<E> create(int maxDistanceFromVehicle, BiPredicate<E, Entity> shouldStopRiding) {
        return BehaviorBuilder.create(
            instance -> instance.group(instance.registered(MemoryModuleType.RIDE_TARGET)).apply(instance, rideTarget -> (level, entity, gameTime) -> {
                Entity entity2 = entity.getVehicle();
                Entity entity3 = instance.<Entity>tryGet(rideTarget).orElse(null);
                if (entity2 == null && entity3 == null) {
                    return false;
                }

                Entity entity4 = entity2 == null ? entity3 : entity2;
                if (isVehicleValid(entity, entity4, maxDistanceFromVehicle) && !shouldStopRiding.test(entity, entity4)) {
                    return false;
                }

                entity.stopRiding();
                rideTarget.erase();
                return true;
            })
        );
    }

    private static boolean isVehicleValid(LivingEntity entity, Entity vehicle, int distance) {
        return vehicle.isAlive() && vehicle.closerThan(entity, distance) && vehicle.level() == entity.level();
    }
}
