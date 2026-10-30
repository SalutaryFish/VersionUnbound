package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.ChorusFlowerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.CommonHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NeoForge's patch to {@code ChorusFlowerBlock}: {@code CropGrowEvent.Pre} (default: grow) for a flower that isn't at
 * its full age, for the position above, and {@code Post} for the flower once it has grown up, branched or died. Here
 * the event decides with the age check, and {@code Post} fires at the end of the method, which every growth branch
 * reaches. Not ported from this patch: the soil checks (the block extension's {@code canSustainPlant}) in
 * {@code randomTick} and {@code canSurvive}.
 */
@Mixin(ChorusFlowerBlock.class)
abstract class ChorusFlowerBlockMixin {
    @Definition(id = "currentAge", local = @Local(type = int.class, name = "currentAge"))
    @Expression("currentAge < 5")
    @ModifyExpressionValue(method = "randomTick", at = @At("MIXINEXTRAS:EXPRESSION"))
    private boolean versionunbound$canCropGrow(boolean canGrow, @Local(argsOnly = true) BlockState state, @Local(argsOnly = true) ServerLevel level,
            @Local(name = "above") BlockPos above, @Share("grows") LocalBooleanRef grows) {
        boolean grow = canGrow && CommonHooks.canCropGrow(level, above, state, true);
        grows.set(grow);
        return grow;
    }

    @Inject(method = "randomTick", at = @At("TAIL"))
    private void versionunbound$fireCropGrowPost(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci,
            @Share("grows") LocalBooleanRef grows) {
        if (grows.get()) CommonHooks.fireCropGrowPost(level, pos, state);
    }
}
