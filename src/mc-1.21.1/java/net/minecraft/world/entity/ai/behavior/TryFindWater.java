package net.minecraft.world.entity.ai.behavior;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.apache.commons.lang3.mutable.MutableLong;

public class TryFindWater {
    public static BehaviorControl<PathfinderMob> create(int range, float speedModifier) {
        MutableLong mutableLong = new MutableLong(0L);
        return BehaviorBuilder.create(
            instance -> instance.group(
                    instance.absent(MemoryModuleType.ATTACK_TARGET),
                    instance.absent(MemoryModuleType.WALK_TARGET),
                    instance.registered(MemoryModuleType.LOOK_TARGET)
                )
                .apply(instance, (attackTarget, walkTarget, lookTarget) -> (level, mob, gameTime) -> {
                    if (level.getFluidState(mob.blockPosition()).is(FluidTags.WATER)) {
                        return false;
                    }

                    if (gameTime < mutableLong.getValue()) {
                        mutableLong.setValue(gameTime + 20L + 2L);
                        return true;
                    }

                    BlockPos blockPos = null;
                    BlockPos blockPos2 = null;
                    BlockPos blockPos3 = mob.blockPosition();

                    for (BlockPos blockPos4 : BlockPos.withinManhattan(blockPos3, range, range, range)) {
                        if (blockPos4.getX() != blockPos3.getX() || blockPos4.getZ() != blockPos3.getZ()) {
                            BlockState blockState = mob.level().getBlockState(blockPos4.above());
                            BlockState blockState2 = mob.level().getBlockState(blockPos4);
                            if (blockState2.is(Blocks.WATER)) {
                                if (blockState.isAir()) {
                                    blockPos = blockPos4.immutable();
                                    break;
                                }

                                if (blockPos2 == null && !blockPos4.closerToCenterThan(mob.position(), 1.5)) {
                                    blockPos2 = blockPos4.immutable();
                                }
                            }
                        }
                    }

                    if (blockPos == null) {
                        blockPos = blockPos2;
                    }

                    if (blockPos != null) {
                        lookTarget.set(new BlockPosTracker(blockPos));
                        walkTarget.set(new WalkTarget(new BlockPosTracker(blockPos), speedModifier, 0));
                    }

                    mutableLong.setValue(gameTime + 40L);
                    return true;
                })
        );
    }
}
