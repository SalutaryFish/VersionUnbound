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
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.CommonHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NeoForge's patch to {@code SweetBerryBushBlock}: vanilla checks age, roll, then light; NeoForge's checks age, light,
 * then {@code CropGrowEvent.Pre} with the roll as its default, so the event fires for every random tick of a bush that
 * can still grow and has the light. Here the roll lets vanilla through and is shared with the light check, which asks
 * the event. {@code Post} fires after the bush grew, before vanilla's game event.
 */
@Mixin(SweetBerryBushBlock.class)
abstract class SweetBerryBushBlockMixin {
    @Definition(id = "nextInt", method = "Lnet/minecraft/util/RandomSource;nextInt(I)I")
    @Expression("?.nextInt(5) == 0")
    @ModifyExpressionValue(method = "randomTick", at = @At("MIXINEXTRAS:EXPRESSION"))
    private boolean versionunbound$deferRoll(boolean roll, @Share("roll") LocalBooleanRef shared) {
        shared.set(roll);
        return true;
    }

    @Definition(id = "getRawBrightness", method = "Lnet/minecraft/server/level/ServerLevel;getRawBrightness(Lnet/minecraft/core/BlockPos;I)I")
    @Expression("?.getRawBrightness(?, 0) >= 9")
    @ModifyExpressionValue(method = "randomTick", at = @At("MIXINEXTRAS:EXPRESSION"))
    private boolean versionunbound$canCropGrow(boolean bright, @Local(argsOnly = true) BlockState state, @Local(argsOnly = true) ServerLevel level,
            @Local(argsOnly = true) BlockPos pos, @Share("roll") LocalBooleanRef roll) {
        return bright && CommonHooks.canCropGrow(level, pos, state, roll.get());
    }

    @Inject(method = "randomTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z",
            shift = At.Shift.AFTER))
    private void versionunbound$fireCropGrowPost(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        CommonHooks.fireCropGrowPost(level, pos, state);
    }
}
