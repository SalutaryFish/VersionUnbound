package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import java.util.ArrayList;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.redstone.Orientation;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;
import net.salutaryfish.versionunbound.neoforge.patches.LevelBlockSnapshots;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NeoForge's patch to {@code Level}, for the events it fires.
 *
 * <p>The block snapshot capture (see {@link LevelBlockSnapshots}), for {@code BlockEvent.EntityPlaceEvent}: while
 * {@code captureBlockSnapshots} is set, the server's {@code setBlock} records a snapshot before the chunk takes the new
 * state, and returns once it has, without what vanilla does next; {@code markAndNotifyBlock} is NeoForge's copy of
 * that, which {@code CommonHooks.onPlaceItemIntoWorld} calls for each snapshot once the event lets the placement stand.
 * NeoForge's {@code setBlock} calls {@code markAndNotifyBlock} itself; here vanilla's body stays (other mods' mixins
 * target it), so a mixin into {@code markAndNotifyBlock} only sees the released snapshots and direct calls. Not ported
 * from that hunk: the block state extension's {@code onBlockStateChange} at the end of {@code markAndNotifyBlock}
 * (Phase 2 step 5).
 *
 * <p>{@code BlockEvent.NeighborNotifyEvent} in vanilla's empty {@code updateNeighborsAt}. {@code ServerLevel} overrides
 * it without calling it, so this fires only on the client (for pistons, which move blocks on both sides), though the
 * event's javadoc says it fires only on the server; NeoForge 1.21.1's patch fired on the client too.
 */
@Mixin(Level.class)
abstract class LevelMixin implements LevelBlockSnapshots {
    private static final String SET_BLOCK = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z";

    // Added under NeoForge's names, for mods that use them.
    public boolean restoringBlockSnapshots = false;
    public boolean captureBlockSnapshots = false;
    public ArrayList<BlockSnapshot> capturedBlockSnapshots = new ArrayList<>();

    @Override
    public boolean versionunbound$captureBlockSnapshots() {
        return this.captureBlockSnapshots;
    }

    @Override
    public void versionunbound$setCaptureBlockSnapshots(boolean capture) {
        this.captureBlockSnapshots = capture;
    }

    @Override
    public ArrayList<BlockSnapshot> versionunbound$capturedBlockSnapshots() {
        return this.capturedBlockSnapshots;
    }

    @Override
    public boolean versionunbound$restoringBlockSnapshots() {
        return this.restoringBlockSnapshots;
    }

    @Override
    public void versionunbound$setRestoringBlockSnapshots(boolean restoring) {
        this.restoringBlockSnapshots = restoring;
    }

    // Forge - prevent mutable BlockPos leaks
    @ModifyVariable(method = SET_BLOCK, at = @At("HEAD"), argsOnly = true)
    private BlockPos versionunbound$immutablePos(BlockPos pos) {
        return pos.immutable();
    }

    @WrapOperation(method = SET_BLOCK, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/LevelChunk;setBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Lnet/minecraft/world/level/block/state/BlockState;"))
    private @Nullable BlockState versionunbound$captureBlockSnapshot(LevelChunk chunk, BlockPos pos, BlockState blockState, int updateFlags, Operation<BlockState> original,
            @Share("blockSnapshot") LocalRef<BlockSnapshot> captured) {
        Level level = (Level) (Object) this;
        BlockSnapshot blockSnapshot = null;
        if (this.captureBlockSnapshots && !level.isClientSide()) {
            blockSnapshot = BlockSnapshot.create(level.dimension(), level, pos, updateFlags);
            this.capturedBlockSnapshots.add(blockSnapshot);
        }
        BlockState oldState = original.call(chunk, pos, blockState, updateFlags);
        if (oldState == null && blockSnapshot != null) {
            this.capturedBlockSnapshots.remove(blockSnapshot);
        }
        captured.set(blockSnapshot);
        return oldState;
    }

    // Don't notify clients or update physics while capturing blockstates: the rest of setBlock is markAndNotifyBlock's.
    @Inject(method = SET_BLOCK, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;", ordinal = 0), cancellable = true)
    private void versionunbound$holdBackUpdates(BlockPos pos, BlockState blockState, int updateFlags, int updateLimit, CallbackInfoReturnable<Boolean> cir,
            @Share("blockSnapshot") LocalRef<BlockSnapshot> captured) {
        if (captured.get() != null) cir.setReturnValue(true);
    }

    // Split off from original setBlockState(BlockPos, BlockState, int, int) method in order to directly send client and physic updates
    @Override
    public void markAndNotifyBlock(BlockPos pos, @Nullable LevelChunk chunk, BlockState oldState, BlockState blockState, int updateFlags, int updateLimit) {
        Level level = (Level) (Object) this;
        Block block = blockState.getBlock();
        BlockState newState = level.getBlockState(pos);
        if (newState == blockState) {
            if (oldState != newState) {
                level.setBlocksDirty(pos, oldState, newState);
            }

            if ((updateFlags & 2) != 0
                && (!level.isClientSide() || (updateFlags & 4) == 0)
                && (level.isClientSide() || chunk.getFullStatus() != null && chunk.getFullStatus().isOrAfter(FullChunkStatus.BLOCK_TICKING))) {
                level.sendBlockUpdated(pos, oldState, blockState, updateFlags);
            }

            if ((updateFlags & 1) != 0) {
                level.updateNeighborsAt(pos, oldState.getBlock());
                if (!level.isClientSide() && blockState.hasAnalogOutputSignal()) {
                    level.updateNeighbourForOutputSignal(pos, block);
                }
            }

            if ((updateFlags & 16) == 0 && updateLimit > 0) {
                int neighbourUpdateFlags = updateFlags & -34;
                oldState.updateIndirectNeighbourShapes(level, pos, neighbourUpdateFlags, updateLimit - 1);
                blockState.updateNeighbourShapes(level, pos, neighbourUpdateFlags, updateLimit - 1);
                blockState.updateIndirectNeighbourShapes(level, pos, neighbourUpdateFlags, updateLimit - 1);
            }

            level.updatePOIOnBlockStateChange(pos, oldState, newState);
            // VersionUnbound: NeoForge calls blockState.onBlockStateChange(this, pos, oldState) here, a block state
            // extension method (Phase 2 step 5).
        }
    }

    // NeoForge ignores whether the event was canceled here.
    @Inject(
            method = "updateNeighborsAt(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Block;Lnet/minecraft/world/level/redstone/Orientation;)V",
            at = @At("HEAD"))
    private void versionunbound$neighborNotify(BlockPos pos, Block sourceBlock, @Nullable Orientation orientation, CallbackInfo ci) {
        Level level = (Level) (Object) this;
        EventHooks.onNeighborNotify(level, pos, level.getBlockState(pos), EnumSet.allOf(Direction.class), false).isCanceled();
    }
}
