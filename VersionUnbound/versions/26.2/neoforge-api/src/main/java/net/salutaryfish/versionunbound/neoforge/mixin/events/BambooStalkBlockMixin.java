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
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.CommonHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NeoForge's patch to {@code BambooStalkBlock}: vanilla rolls first; NeoForge's patch moves the roll into
 * {@code CropGrowEvent.Pre}'s default, asked once the bamboo has room, light and isn't at its full height. Here the
 * roll lets vanilla through and is shared with the height check, which asks the event. {@code Post} fires after
 * {@code growBamboo}. Not ported from this patch: the soil checks in {@code getStateForPlacement} and
 * {@code canSurvive} (the block extension's {@code canSustainPlant}, with the other soil checks).
 */
@Mixin(BambooStalkBlock.class)
abstract class BambooStalkBlockMixin {
    @Definition(id = "nextInt", method = "Lnet/minecraft/util/RandomSource;nextInt(I)I")
    @Expression("?.nextInt(3) == 0")
    @ModifyExpressionValue(method = "randomTick", at = @At("MIXINEXTRAS:EXPRESSION"))
    private boolean versionunbound$deferRoll(boolean roll, @Share("roll") LocalBooleanRef shared) {
        shared.set(roll);
        return true;
    }

    @Definition(id = "height", local = @Local(type = int.class, name = "height"))
    @Expression("height < 16")
    @ModifyExpressionValue(method = "randomTick", at = @At("MIXINEXTRAS:EXPRESSION"))
    private boolean versionunbound$canCropGrow(boolean canGrow, @Local(argsOnly = true) BlockState state, @Local(argsOnly = true) ServerLevel level,
            @Local(argsOnly = true) BlockPos pos, @Share("roll") LocalBooleanRef roll) {
        return canGrow && CommonHooks.canCropGrow(level, pos, state, roll.get());
    }

    @Inject(method = "randomTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/BambooStalkBlock;growBamboo(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;I)V",
            shift = At.Shift.AFTER))
    private void versionunbound$fireCropGrowPost(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        CommonHooks.fireCropGrowPost(level, pos, state);
    }
}
