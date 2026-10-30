package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.CommonHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NeoForge's patch to {@code StemBlock}: {@code CropGrowEvent.Pre} decides with the growth roll as the default, and
 * {@code Post} fires once the stem has grown or tried to grow a fruit (also when there was no room for one), with the
 * stem's state from before. NeoForge's patch stops vanilla from overwriting the {@code state} argument for that; here
 * the state from the roll is shared with the {@code Post} injector at the end of the method, which the growth branch
 * reaches. Not ported from this patch: the loaded area check (an extension method, Phase 2 step 5). The call of
 * {@code CropBlock.getGrowthSpeed} with the state instead of the block gives the same answer (see
 * {@code CropBlockPatch}), so vanilla's call stays.
 */
@Mixin(StemBlock.class)
abstract class StemBlockMixin {
    @Definition(id = "nextInt", method = "Lnet/minecraft/util/RandomSource;nextInt(I)I")
    @Expression("?.nextInt(?) == 0")
    @ModifyExpressionValue(method = "randomTick", at = @At("MIXINEXTRAS:EXPRESSION"))
    private boolean versionunbound$canCropGrow(boolean grows, @Local(argsOnly = true) BlockState state, @Local(argsOnly = true) ServerLevel level,
            @Local(argsOnly = true) BlockPos pos, @Share("grown") LocalRef<BlockState> grown) {
        boolean grow = CommonHooks.canCropGrow(level, pos, state, grows);
        if (grow) grown.set(state);
        return grow;
    }

    @Inject(method = "randomTick", at = @At("TAIL"))
    private void versionunbound$fireCropGrowPost(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci,
            @Share("grown") LocalRef<BlockState> grown) {
        if (grown.get() != null) CommonHooks.fireCropGrowPost(level, pos, grown.get());
    }
}
