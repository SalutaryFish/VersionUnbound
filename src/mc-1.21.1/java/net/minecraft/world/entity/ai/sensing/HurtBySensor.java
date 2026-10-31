package net.minecraft.world.entity.ai.sensing;

import com.google.common.collect.ImmutableSet;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

public class HurtBySensor extends Sensor<LivingEntity> {
    @Override
    public Set<MemoryModuleType<?>> requires() {
        return ImmutableSet.of(MemoryModuleType.HURT_BY, MemoryModuleType.HURT_BY_ENTITY);
    }

    @Override
    protected void doTick(ServerLevel level, LivingEntity entity) {
        Brain<?> brain = entity.getBrain();
        DamageSource damageSource = entity.getLastDamageSource();
        if (damageSource != null) {
            brain.setMemory(MemoryModuleType.HURT_BY, entity.getLastDamageSource());
            Entity entity2 = damageSource.getEntity();
            if (entity2 instanceof LivingEntity) {
                brain.setMemory(MemoryModuleType.HURT_BY_ENTITY, (LivingEntity)entity2);
            }
        } else {
            brain.eraseMemory(MemoryModuleType.HURT_BY);
        }

        brain.getMemory(MemoryModuleType.HURT_BY_ENTITY).ifPresent(attacker -> {
            if (!attacker.isAlive() || attacker.level() != level) {
                brain.eraseMemory(MemoryModuleType.HURT_BY_ENTITY);
            }
        });
    }
}
