package net.salutaryfish.versionunbound.neoforge.mixin.patches;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * NeoForge's patch to {@code BlockBehaviour.onExplosionHit}: whether the block drops loot asks the state's
 * {@code canDropFromExplosion}, and its removal is the state's {@code onBlockExploded} (whose default is vanilla's
 * {@code setBlock} to air and {@code wasExploded}).
 */
@Mixin(BlockBehaviour.class)
abstract class BlockBehaviourExplosionMixin {
    @WrapOperation(method = "onExplosionHit", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;dropFromExplosion(Lnet/minecraft/world/level/Explosion;)Z"))
    private boolean versionunbound$canDropFromExplosion(Block block, Explosion explosion, Operation<Boolean> original,
            @Local(argsOnly = true) BlockState state, @Local(argsOnly = true) ServerLevel level, @Local(argsOnly = true) BlockPos pos) {
        return state.canDropFromExplosion(level, pos, explosion);
    }

    @WrapOperation(method = "onExplosionHit", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    private boolean versionunbound$onBlockExploded(ServerLevel level, BlockPos pos, BlockState air, int flags, Operation<Boolean> original,
            @Local(argsOnly = true) BlockState state, @Local(argsOnly = true) Explosion explosion) {
        state.onBlockExploded(level, pos, explosion);
        return true;
    }

    // onBlockExploded's default calls wasExploded itself.
    @WrapWithCondition(method = "onExplosionHit", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;wasExploded(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/Explosion;)V"))
    private boolean versionunbound$wasExplodedInOnBlockExploded(Block block, ServerLevel level, BlockPos pos, Explosion explosion) {
        return false;
    }
}
