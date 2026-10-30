package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.salutaryfish.versionunbound.neoforge.patches.PlayerExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * NeoForge's patch to {@code DoorBlock.playerWillDestroy}: whether breaking the top half keeps the bottom half's drops
 * asks NeoForge's position-sensitive {@code hasCorrectToolForDrops} ({@code PlayerEvent.HarvestCheck}). NeoForge's
 * patch also asks {@code isCreative()} where vanilla 26.2 asks {@code preventsBlockDrops()} (the patch keeps 1.21.1's
 * check); kept as NeoForge has it.
 */
@Mixin(DoorBlock.class)
abstract class DoorBlockMixin {
    @WrapOperation(method = "playerWillDestroy", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;preventsBlockDrops()Z"))
    private boolean versionunbound$creative(Player player, Operation<Boolean> original) {
        return player.isCreative();
    }

    @WrapOperation(method = "playerWillDestroy", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;hasCorrectToolForDrops(Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean versionunbound$harvestCheck(Player player, BlockState state, Operation<Boolean> original, Level level, BlockPos pos) {
        return ((PlayerExtension) player).hasCorrectToolForDrops(state, level, pos);
    }
}
