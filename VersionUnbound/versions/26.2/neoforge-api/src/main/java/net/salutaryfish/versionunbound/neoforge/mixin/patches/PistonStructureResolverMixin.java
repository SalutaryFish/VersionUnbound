package net.salutaryfish.versionunbound.neoforge.mixin.patches;

import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * NeoForge's patch to {@code PistonStructureResolver}: stickiness asks the state's {@code isStickyBlock} and
 * {@code canStickTo} (whose defaults are vanilla's slime and honey rules). {@code @Overwrite}s with NeoForge's bodies.
 */
@Mixin(PistonStructureResolver.class)
abstract class PistonStructureResolverMixin {
    /**
     * @author NeoForge (ported by VersionUnbound)
     * @reason NeoForge's patched body
     */
    @Overwrite
    private static boolean isSticky(final BlockState state) {
        return state.isStickyBlock();
    }

    /**
     * @author NeoForge (ported by VersionUnbound)
     * @reason NeoForge's patched body
     */
    @Overwrite
    private static boolean canStickToEachOther(final BlockState state1, final BlockState state2) {
        return state1.canStickTo(state2) && state2.canStickTo(state1);
    }
}
