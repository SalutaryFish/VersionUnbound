package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.CommonHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NeoForge's patch to {@code SugarCaneBlock}: {@code CropGrowEvent.Pre} (default: grow) for every random tick of cane
 * with room above that isn't three high yet, before it ages; {@code Post} only when it grows a new block, for the new
 * block's position, with a fresh cane as the original state, between placing it and resetting the age below. Not
 * ported from this patch: the soil and hydration checks in {@code canSurvive} (extension methods, Phase 2 step 5).
 */
@Mixin(SugarCaneBlock.class)
abstract class SugarCaneBlockMixin {
    @Definition(id = "height", local = @Local(type = int.class, name = "height"))
    @Expression("height < 3")
    @ModifyExpressionValue(method = "randomTick", at = @At("MIXINEXTRAS:EXPRESSION"))
    private boolean versionunbound$canCropGrow(boolean canGrow, @Local(argsOnly = true) BlockState state, @Local(argsOnly = true) ServerLevel level,
            @Local(argsOnly = true) BlockPos pos) {
        return canGrow && CommonHooks.canCropGrow(level, pos, state, true);
    }

    @Inject(method = "randomTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z",
            shift = At.Shift.AFTER))
    private void versionunbound$fireCropGrowPost(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        CommonHooks.fireCropGrowPost(level, pos.above(), ((Block) (Object) this).defaultBlockState());
    }
}
