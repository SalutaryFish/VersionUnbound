package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.GrowingPlantBlock;
import net.minecraft.world.level.block.GrowingPlantHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.CommonHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NeoForge's patch to {@code GrowingPlantHeadBlock} (kelp, vines, cave vines): {@code CropGrowEvent.Pre} decides with
 * the growth roll as the default, for the position the plant grows into, and {@code Post} fires there after the plant
 * grew into it, with the new state as the original state too (NeoForge passes the state it reads back; NeoForge 1.21.1
 * did the same). The mixin extends {@code GrowingPlantBlock} to reach its {@code growthDirection} ({@code @Shadow}
 * only finds fields the target declares).
 */
@Mixin(GrowingPlantHeadBlock.class)
abstract class GrowingPlantHeadBlockMixin extends GrowingPlantBlock {
    private GrowingPlantHeadBlockMixin(Properties properties, Direction growthDirection, VoxelShape shape, boolean scheduleFluidTicks) {
        super(properties, growthDirection, shape, scheduleFluidTicks);
    }

    @Definition(id = "nextDouble", method = "Lnet/minecraft/util/RandomSource;nextDouble()D")
    @Expression("?.nextDouble() < ?")
    @ModifyExpressionValue(method = "randomTick", at = @At("MIXINEXTRAS:EXPRESSION"))
    private boolean versionunbound$canCropGrow(boolean grows, @Local(argsOnly = true) BlockState state, @Local(argsOnly = true) ServerLevel level,
            @Local(argsOnly = true) BlockPos pos) {
        return CommonHooks.canCropGrow(level, pos.relative(this.growthDirection), state, grows);
    }

    @Inject(method = "randomTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z",
            shift = At.Shift.AFTER))
    private void versionunbound$fireCropGrowPost(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci,
            @Local(name = "growthPos") BlockPos growthPos) {
        CommonHooks.fireCropGrowPost(level, growthPos, level.getBlockState(growthPos));
    }
}
