package net.salutaryfish.versionunbound.testmod.events.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.MoveToBlockGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The block a goal moves to, so {@code GriefingScenario} can hand a fox its berry bush. */
@Mixin(MoveToBlockGoal.class)
public interface MoveToBlockGoalAccessor {
    @Accessor("blockPos")
    void events$setBlockPos(BlockPos pos);
}
