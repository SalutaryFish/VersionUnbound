package net.salutaryfish.versionunbound.neoforge.patches;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The position-sensitive methods NeoForge's patch adds to {@code Player}, added by {@code PlayerMixin} with NeoForge's
 * names and descriptors (mods call them on {@code Player}); Layer A's code reaches them through this interface.
 */
public interface PlayerExtension {
    /** NeoForge's {@code getDestroySpeed(BlockState, BlockPos)}: vanilla's speed, then {@code PlayerEvent.BreakSpeed} with the position. */
    float getDestroySpeed(BlockState state, @Nullable BlockPos pos);

    /** NeoForge's {@code hasCorrectToolForDrops(BlockState, Level, BlockPos)}: vanilla's answer, then {@code PlayerEvent.HarvestCheck}. */
    boolean hasCorrectToolForDrops(BlockState state, Level level, BlockPos pos);
}
