package net.salutaryfish.versionunbound.neoforge.mixin.events;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.redstone.Orientation;
import net.neoforged.neoforge.event.EventHooks;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NeoForge's patch to {@code Level}, for {@code BlockEvent.NeighborNotifyEvent} in vanilla's empty
 * {@code updateNeighborsAt}. {@code ServerLevel} overrides it without calling it, so this fires only on the client (for
 * pistons, which move blocks on both sides), though the event's javadoc says it fires only on the server; NeoForge
 * 1.21.1's patch fired on the client too.
 */
@Mixin(Level.class)
abstract class LevelMixin {
    // NeoForge ignores whether the event was canceled here.
    @Inject(
            method = "updateNeighborsAt(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Block;Lnet/minecraft/world/level/redstone/Orientation;)V",
            at = @At("HEAD"))
    private void versionunbound$neighborNotify(BlockPos pos, Block sourceBlock, @Nullable Orientation orientation, CallbackInfo ci) {
        Level level = (Level) (Object) this;
        EventHooks.onNeighborNotify(level, pos, level.getBlockState(pos), EnumSet.allOf(Direction.class), false).isCanceled();
    }
}
