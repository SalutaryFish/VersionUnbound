package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.ImmutableMap;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

public class MoveToTargetSink extends Behavior<Mob> {
    private static final int MAX_COOLDOWN_BEFORE_RETRYING = 40;
    private int remainingCooldown;
    @Nullable
    private Path path;
    @Nullable
    private BlockPos lastTargetPos;
    private float speedModifier;

    public MoveToTargetSink() {
        this(150, 250);
    }

    public MoveToTargetSink(int minDuration, int maxDuration) {
        super(
            ImmutableMap.of(
                MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE,
                MemoryStatus.REGISTERED,
                MemoryModuleType.PATH,
                MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.WALK_TARGET,
                MemoryStatus.VALUE_PRESENT
            ),
            minDuration,
            maxDuration
        );
    }

    protected boolean checkExtraStartConditions(ServerLevel level, Mob owner) {
        if (this.remainingCooldown > 0) {
            this.remainingCooldown--;
            return false;
        }

        Brain<?> brain = owner.getBrain();
        WalkTarget walkTarget = brain.getMemory(MemoryModuleType.WALK_TARGET).get();
        boolean bl = this.reachedTarget(owner, walkTarget);
        if (!bl && this.tryComputePath(owner, walkTarget, level.getGameTime())) {
            this.lastTargetPos = walkTarget.getTarget().currentBlockPosition();
            return true;
        }

        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        if (bl) {
            brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        }

        return false;
    }

    protected boolean canStillUse(ServerLevel level, Mob entity, long gameTime) {
        if (this.path != null && this.lastTargetPos != null) {
            Optional<WalkTarget> optional = entity.getBrain().getMemory(MemoryModuleType.WALK_TARGET);
            boolean bl = optional.map(MoveToTargetSink::isWalkTargetSpectator).orElse(false);
            PathNavigation pathNavigation = entity.getNavigation();
            return !pathNavigation.isDone() && optional.isPresent() && !this.reachedTarget(entity, optional.get()) && !bl;
        } else {
            return false;
        }
    }

    protected void stop(ServerLevel level, Mob entity, long gameTime) {
        if (entity.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)
            && !this.reachedTarget(entity, entity.getBrain().getMemory(MemoryModuleType.WALK_TARGET).get())
            && entity.getNavigation().isStuck()) {
            this.remainingCooldown = level.getRandom().nextInt(40);
        }

        entity.getNavigation().stop();
        entity.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        entity.getBrain().eraseMemory(MemoryModuleType.PATH);
        this.path = null;
    }

    protected void start(ServerLevel level, Mob entity, long gameTime) {
        entity.getBrain().setMemory(MemoryModuleType.PATH, this.path);
        entity.getNavigation().moveTo(this.path, this.speedModifier);
    }

    protected void tick(ServerLevel level, Mob owner, long gameTime) {
        Path path = owner.getNavigation().getPath();
        Brain<?> brain = owner.getBrain();
        if (this.path != path) {
            this.path = path;
            brain.setMemory(MemoryModuleType.PATH, path);
        }

        if (path != null && this.lastTargetPos != null) {
            WalkTarget walkTarget = brain.getMemory(MemoryModuleType.WALK_TARGET).get();
            if (walkTarget.getTarget().currentBlockPosition().distSqr(this.lastTargetPos) > 4.0 && this.tryComputePath(owner, walkTarget, level.getGameTime())) {
                this.lastTargetPos = walkTarget.getTarget().currentBlockPosition();
                this.start(level, owner, gameTime);
            }
        }
    }

    private boolean tryComputePath(Mob mob, WalkTarget target, long time) {
        BlockPos blockPos = target.getTarget().currentBlockPosition();
        this.path = mob.getNavigation().createPath(blockPos, 0);
        this.speedModifier = target.getSpeedModifier();
        Brain<?> brain = mob.getBrain();
        if (this.reachedTarget(mob, target)) {
            brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        } else {
            boolean bl = this.path != null && this.path.canReach();
            if (bl) {
                brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
            } else if (!brain.hasMemoryValue(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)) {
                brain.setMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, time);
            }

            if (this.path != null) {
                return true;
            }

            Vec3 vec3 = DefaultRandomPos.getPosTowards((PathfinderMob)mob, 10, 7, Vec3.atBottomCenterOf(blockPos), (float) (Math.PI / 2));
            if (vec3 != null) {
                this.path = mob.getNavigation().createPath(vec3.x, vec3.y, vec3.z, 0);
                return this.path != null;
            }
        }

        return false;
    }

    private boolean reachedTarget(Mob mob, WalkTarget target) {
        return target.getTarget().currentBlockPosition().distManhattan(mob.blockPosition()) <= target.getCloseEnoughDist();
    }

    private static boolean isWalkTargetSpectator(WalkTarget walkTarget) {
        return walkTarget.getTarget() instanceof EntityTracker entityTracker ? entityTracker.getEntity().isSpectator() : false;
    }
}
