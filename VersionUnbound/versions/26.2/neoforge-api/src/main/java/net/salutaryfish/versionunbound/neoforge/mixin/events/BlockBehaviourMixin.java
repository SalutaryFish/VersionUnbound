package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.EventHooks;
import net.salutaryfish.versionunbound.neoforge.patches.PlayerExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * NeoForge's patch to {@code BlockBehaviour.getDestroyProgress}: {@code PlayerEvent.HarvestCheck} decides the tool
 * modifier, and the player's destroy speed comes from NeoForge's position-sensitive {@code getDestroySpeed}, so
 * {@code PlayerEvent.BreakSpeed} gets the position.
 */
@Mixin(BlockBehaviour.class)
abstract class BlockBehaviourMixin {
    @WrapOperation(method = "getDestroyProgress", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;hasCorrectToolForDrops(Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean versionunbound$harvestCheck(Player player, BlockState state, Operation<Boolean> original, BlockState blockState, Player destroyer, BlockGetter level, BlockPos pos) {
        return EventHooks.doPlayerHarvestCheck(player, state, level, pos);
    }

    @WrapOperation(method = "getDestroyProgress", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getDestroySpeed(Lnet/minecraft/world/level/block/state/BlockState;)F"))
    private float versionunbound$destroySpeed(Player player, BlockState state, Operation<Float> original, BlockState blockState, Player destroyer, BlockGetter level, BlockPos pos) {
        return ((PlayerExtension) player).getDestroySpeed(state, pos);
    }
}
