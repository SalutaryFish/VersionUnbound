package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;

public class SleepInBed extends Behavior<LivingEntity> {
    public static final int COOLDOWN_AFTER_BEING_WOKEN = 100;
    private long nextOkStartTime;

    public SleepInBed() {
        super(ImmutableMap.of(MemoryModuleType.HOME, MemoryStatus.VALUE_PRESENT, MemoryModuleType.LAST_WOKEN, MemoryStatus.REGISTERED));
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, LivingEntity owner) {
        if (owner.isPassenger()) {
            return false;
        }

        Brain<?> brain = owner.getBrain();
        GlobalPos globalPos = brain.getMemory(MemoryModuleType.HOME).get();
        if (level.dimension() != globalPos.dimension()) {
            return false;
        }

        Optional<Long> optional = brain.getMemory(MemoryModuleType.LAST_WOKEN);
        if (optional.isPresent()) {
            long l = level.getGameTime() - optional.get();
            if (l > 0L && l < 100L) {
                return false;
            }
        }

        BlockState blockState = level.getBlockState(globalPos.pos());
        return globalPos.pos().closerToCenterThan(owner.position(), 2.0) && blockState.is(BlockTags.BEDS) && !blockState.getValue(BedBlock.OCCUPIED);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, LivingEntity entity, long gameTime) {
        Optional<GlobalPos> optional = entity.getBrain().getMemory(MemoryModuleType.HOME);
        if (optional.isEmpty()) {
            return false;
        }

        BlockPos blockPos = optional.get().pos();
        return entity.getBrain().isActive(Activity.REST) && entity.getY() > blockPos.getY() + 0.4 && blockPos.closerToCenterThan(entity.position(), 1.14);
    }

    @Override
    protected void start(ServerLevel level, LivingEntity entity, long gameTime) {
        if (gameTime > this.nextOkStartTime) {
            Brain<?> brain = entity.getBrain();
            if (brain.hasMemoryValue(MemoryModuleType.DOORS_TO_CLOSE)) {
                Set<GlobalPos> set = brain.getMemory(MemoryModuleType.DOORS_TO_CLOSE).get();
                Optional<List<LivingEntity>> optional;
                if (brain.hasMemoryValue(MemoryModuleType.NEAREST_LIVING_ENTITIES)) {
                    optional = brain.getMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES);
                } else {
                    optional = Optional.empty();
                }

                InteractWithDoor.closeDoorsThatIHaveOpenedOrPassedThrough(level, entity, null, null, set, optional);
            }

            entity.startSleeping(entity.getBrain().getMemory(MemoryModuleType.HOME).get().pos());
        }
    }

    @Override
    protected boolean timedOut(long gameTime) {
        return false;
    }

    @Override
    protected void stop(ServerLevel level, LivingEntity entity, long gameTime) {
        if (entity.isSleeping()) {
            entity.stopSleeping();
            this.nextOkStartTime = gameTime + 40L;
        }
    }
}
