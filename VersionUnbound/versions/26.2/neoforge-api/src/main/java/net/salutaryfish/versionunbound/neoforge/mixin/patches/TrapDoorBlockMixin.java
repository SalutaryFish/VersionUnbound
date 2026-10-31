package net.salutaryfish.versionunbound.neoforge.mixin.patches;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

/**
 * NeoForge's patch to {@code TrapDoorBlock}: an open trapdoor is a ladder when the block below says so
 * ({@code makesOpenTrapdoorAboveClimbable}, whose default is vanilla's rule: a ladder facing the same way), which
 * LivingEntityClimbingMixin asks through {@code isLadder}.
 */
@Mixin(TrapDoorBlock.class)
abstract class TrapDoorBlockMixin {
    // Neo: Allows Trapdoors to be climbable if any ladder, even modded ladders, is below Trapdoor
    public boolean isLadder(BlockState state, LevelReader world, BlockPos pos, LivingEntity entity) {
        if (state.getValue(TrapDoorBlock.OPEN)) {
            BlockPos downPos = pos.below();
            BlockState down = world.getBlockState(downPos);
            return down.getBlock().makesOpenTrapdoorAboveClimbable(down, world, downPos, state);
        }
        return false;
    }
}
