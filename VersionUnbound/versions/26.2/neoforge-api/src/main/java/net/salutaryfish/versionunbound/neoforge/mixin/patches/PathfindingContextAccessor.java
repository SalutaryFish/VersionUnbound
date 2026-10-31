package net.salutaryfish.versionunbound.neoforge.mixin.patches;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** NeoForge's package-private {@code currentEvalPos}: the position {@code getPathTypeFromState} last looked at. */
@Mixin(PathfindingContext.class)
public interface PathfindingContextAccessor {
    @Accessor("mutablePos")
    BlockPos.MutableBlockPos versionunbound$currentEvalPos();
}
