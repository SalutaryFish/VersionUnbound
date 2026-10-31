package net.minecraft.world.entity.ai.behavior;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.apache.commons.lang3.mutable.MutableLong;

public class TryFindLand {
    private static final int COOLDOWN_TICKS = 60;

    public static BehaviorControl<PathfinderMob> create(int range, float speedModifier) {
        MutableLong mutableLong = new MutableLong(0L);
        return BehaviorBuilder.create(
            instance -> instance.group(
                    instance.absent(MemoryModuleType.ATTACK_TARGET),
                    instance.absent(MemoryModuleType.WALK_TARGET),
                    instance.registered(MemoryModuleType.LOOK_TARGET)
                )
                .apply(
                    instance,
                    (attackTarget, walkTarget, lookTarget) -> (level, mob, gameTime) -> {
                        if (!level.getFluidState(mob.blockPosition()).is(FluidTags.WATER)) {
                            return false;
                        }

                        if (gameTime < mutableLong.getValue()) {
                            mutableLong.setValue(gameTime + 60L);
                            return true;
                        }

                        BlockPos blockPos = mob.blockPosition();
                        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
                        CollisionContext collisionContext = CollisionContext.of(mob);

                        for (BlockPos blockPos2 : BlockPos.withinManhattan(blockPos, range, range, range)) {
                            if (blockPos2.getX() != blockPos.getX() || blockPos2.getZ() != blockPos.getZ()) {
                                BlockState blockState = level.getBlockState(blockPos2);
                                BlockState blockState2 = level.getBlockState(mutableBlockPos.setWithOffset(blockPos2, Direction.DOWN));
                                if (!blockState.is(Blocks.WATER)
                                    && level.getFluidState(blockPos2).isEmpty()
                                    && blockState.getCollisionShape(level, blockPos2, collisionContext).isEmpty()
                                    && blockState2.isFaceSturdy(level, mutableBlockPos, Direction.UP)) {
                                    BlockPos blockPos3 = blockPos2.immutable();
                                    lookTarget.set(new BlockPosTracker(blockPos3));
                                    walkTarget.set(new WalkTarget(new BlockPosTracker(blockPos3), speedModifier, 1));
                                    break;
                                }
                            }
                        }

                        mutableLong.setValue(gameTime + 60L);
                        return true;
                    }
                )
        );
    }
}
