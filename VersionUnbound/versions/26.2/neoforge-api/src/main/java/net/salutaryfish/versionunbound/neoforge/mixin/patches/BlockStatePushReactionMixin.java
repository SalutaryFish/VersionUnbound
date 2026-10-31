package net.salutaryfish.versionunbound.neoforge.mixin.patches;

import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NeoForge's patch to {@code BlockBehaviour.BlockStateBase.getPistonPushReaction}: the block's
 * {@code getPistonPushReaction(BlockState)} answers first; null (its default) leaves the one from the block's properties.
 * The piston's push rules ({@code PistonBaseBlock.isPushable}, {@code PistonStructureResolver}) read it from here.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
abstract class BlockStatePushReactionMixin {
    @Inject(method = "getPistonPushReaction", at = @At("HEAD"), cancellable = true)
    private void versionunbound$blockPushReaction(CallbackInfoReturnable<PushReaction> cir) {
        BlockState self = (BlockState) (Object) this;
        PushReaction reaction = self.getBlock().getPistonPushReaction(self);
        if (reaction != null) cir.setReturnValue(reaction);
    }
}
