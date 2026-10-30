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
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.CommonHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NeoForge's patch to {@code CactusBlock}: {@code CropGrowEvent.Pre} (default: grow) once there's room above, for the
 * position above, and {@code Post} for the cactus after the growth code, unless that returned early (a cactus three
 * high at full age). Here the event decides with the room check, and {@code Post} fires at the end of the method, which
 * only the growth code reaches. Not ported from this patch: the loaded area check in {@code tick} and the soil check in
 * {@code canSurvive} (extension methods, Phase 2 step 5).
 */
@Mixin(CactusBlock.class)
abstract class CactusBlockMixin {
    @Definition(id = "isEmptyBlock", method = "Lnet/minecraft/server/level/ServerLevel;isEmptyBlock(Lnet/minecraft/core/BlockPos;)Z")
    @Expression("?.isEmptyBlock(?)")
    @ModifyExpressionValue(method = "randomTick", at = @At("MIXINEXTRAS:EXPRESSION"))
    private boolean versionunbound$canCropGrow(boolean empty, @Local(argsOnly = true) BlockState state, @Local(argsOnly = true) ServerLevel level,
            @Local(name = "above") BlockPos above, @Share("grows") LocalBooleanRef grows) {
        boolean grow = empty && CommonHooks.canCropGrow(level, above, state, true);
        grows.set(grow);
        return grow;
    }

    @Inject(method = "randomTick", at = @At("TAIL"))
    private void versionunbound$fireCropGrowPost(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci,
            @Share("grows") LocalBooleanRef grows) {
        if (grows.get()) CommonHooks.fireCropGrowPost(level, pos, state);
    }
}
