package net.salutaryfish.versionunbound.neoforge.patches;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;

/**
 * The instance method NeoForge's patch adds to {@code FireBlock}, injected into it by the class tweaker; the fire's
 * own checks call it (mixin/patches/FireBlockMixin), and a mod's fire can override it.
 */
public interface FireBlockExtension {
    /**
     * Side sensitive version that calls the block function.
     *
     * @param world The current world
     * @param pos Block position
     * @param face The side the fire is coming from
     * @return True if the face can catch fire.
     */
    default boolean canCatchFire(BlockGetter world, BlockPos pos, Direction face) {
        return world.getBlockState(pos).isFlammable(world, pos, face);
    }
}
