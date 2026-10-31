package net.salutaryfish.versionunbound.neoforge.mixin.patches;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NeoForge's patch to {@code WalkNodeEvaluator}, the block parts: a block's own path type ({@code getBlockPathType},
 * whose default answers lava and burning blocks) comes before vanilla's rules, and a neighbour's
 * {@code getAdjacentBlockPathType} before vanilla's neighbour rules. Not yet: the fluid state's path types (with the
 * fluid extensions). Fabric API's {@code LandPathTypeRegistry} hooks the same method after the first {@code getBlock()}
 * call, so NeoForge's block answer goes first.
 */
@Mixin(WalkNodeEvaluator.class)
abstract class WalkNodeEvaluatorMixin {
    @Inject(method = "getPathTypeFromState", at = @At("HEAD"), cancellable = true)
    private static void versionunbound$blockPathType(BlockGetter level, BlockPos pos, CallbackInfoReturnable<PathType> cir) {
        PathType type = level.getBlockState(pos).getBlockPathType(level, pos, null);
        if (type != null) cir.setReturnValue(type);
    }

    // Right after a neighbour's path type is read (getPathTypeFromState left the context's position on the neighbour).
    @Inject(method = "checkNeighbourBlocks", at = @At(value = "INVOKE_ASSIGN", target = "Lnet/minecraft/world/level/pathfinder/PathfindingContext;getPathTypeFromState(III)Lnet/minecraft/world/level/pathfinder/PathType;"), cancellable = true)
    private static void versionunbound$adjacentBlockPathType(PathfindingContext context, int x, int y, int z, PathType blockPathType, CallbackInfoReturnable<PathType> cir,
            @Local(name = "pathType") PathType pathType) {
        BlockPos pos = ((PathfindingContextAccessor) context).versionunbound$currentEvalPos();
        BlockState blockState = context.level().getBlockState(pos);
        PathType adjBlockPathType = blockState.getAdjacentBlockPathType(context.level(), pos, null, pathType);
        if (adjBlockPathType != null) cir.setReturnValue(adjBlockPathType);
    }
}
