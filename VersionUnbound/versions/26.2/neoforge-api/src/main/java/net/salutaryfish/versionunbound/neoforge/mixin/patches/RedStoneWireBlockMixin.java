package net.salutaryfish.versionunbound.neoforge.mixin.patches;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * NeoForge's patch to {@code RedStoneWireBlock.getConnectingSide}: whether the wire connects to a block asks the block
 * state's {@code canRedstoneConnectTo} (the block's {@code canConnectRedstone}, whose default is vanilla's
 * {@code shouldConnectTo}) with the block's position, in place of the static {@code shouldConnectTo}. NeoForge rewrites
 * the final return as an if-chain with the same three questions in the same order; here vanilla's expression stays and
 * each of its three calls is answered by the extension.
 */
@Mixin(RedStoneWireBlock.class)
abstract class RedStoneWireBlockMixin {
    private static final String GET_CONNECTING_SIDE = "getConnectingSide(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;Z)Lnet/minecraft/world/level/block/state/properties/RedstoneSide;";

    // The block above the neighbour (the wire climbing it), with no direction.
    @WrapOperation(method = GET_CONNECTING_SIDE, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/RedStoneWireBlock;shouldConnectTo(Lnet/minecraft/world/level/block/state/BlockState;)Z", ordinal = 0))
    private boolean versionunbound$connectsAbove(BlockState state, Operation<Boolean> original, @Local(argsOnly = true) BlockGetter level,
            @Local(name = "relativePos") BlockPos relativePos) {
        return state.canRedstoneConnectTo(level, relativePos.above(), null);
    }

    // The neighbour itself, from the wire's direction.
    @WrapOperation(method = GET_CONNECTING_SIDE, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/RedStoneWireBlock;shouldConnectTo(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;)Z"))
    private boolean versionunbound$connectsBeside(BlockState state, Direction direction, Operation<Boolean> original, @Local(argsOnly = true) BlockGetter level,
            @Local(name = "relativePos") BlockPos relativePos) {
        return state.canRedstoneConnectTo(level, relativePos, direction);
    }

    // The block below the neighbour (the wire going down), with no direction.
    @WrapOperation(method = GET_CONNECTING_SIDE, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/RedStoneWireBlock;shouldConnectTo(Lnet/minecraft/world/level/block/state/BlockState;)Z", ordinal = 1))
    private boolean versionunbound$connectsBelow(BlockState state, Operation<Boolean> original, @Local(argsOnly = true) BlockGetter level,
            @Local(name = "relativePos") BlockPos relativePos) {
        return state.canRedstoneConnectTo(level, relativePos.below(), null);
    }
}
