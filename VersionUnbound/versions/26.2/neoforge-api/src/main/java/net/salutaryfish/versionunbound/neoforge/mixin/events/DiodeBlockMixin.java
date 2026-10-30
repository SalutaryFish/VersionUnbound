package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.llamalad7.mixinextras.sugar.Local;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.EventHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NeoForge's patch to {@code DiodeBlock} (repeaters and comparators): {@code BlockEvent.NeighborNotifyEvent} for the
 * block a diode updates in front of it; canceling it skips the update.
 */
@Mixin(DiodeBlock.class)
abstract class DiodeBlockMixin {
    @Inject(
            method = "updateNeighborsInFront",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/redstone/ExperimentalRedstoneUtils;initialOrientation(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/Direction;Lnet/minecraft/core/Direction;)Lnet/minecraft/world/level/redstone/Orientation;"),
            cancellable = true)
    private void versionunbound$neighborNotify(Level level, BlockPos pos, BlockState state, CallbackInfo ci, @Local Direction direction) {
        if (EventHooks.onNeighborNotify(level, pos, level.getBlockState(pos), EnumSet.of(direction.getOpposite()), false).isCanceled())
            ci.cancel();
    }
}
