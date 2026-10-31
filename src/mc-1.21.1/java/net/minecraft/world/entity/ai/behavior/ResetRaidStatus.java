package net.minecraft.world.entity.ai.behavior;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.schedule.Activity;

public class ResetRaidStatus {
    public static BehaviorControl<LivingEntity> create() {
        return BehaviorBuilder.create(instance -> instance.point((level, entity, gameTime) -> {
            if (level.random.nextInt(20) != 0) {
                return false;
            }

            Brain<?> brain = entity.getBrain();
            Raid raid = level.getRaidAt(entity.blockPosition());
            if (raid == null || raid.isStopped() || raid.isLoss()) {
                brain.setDefaultActivity(Activity.IDLE);
                brain.updateActivityFromSchedule(level.getDayTime(), level.getGameTime());
            }

            return true;
        }));
    }
}
