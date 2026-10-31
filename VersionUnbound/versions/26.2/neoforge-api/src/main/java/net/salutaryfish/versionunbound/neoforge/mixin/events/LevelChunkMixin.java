package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.salutaryfish.versionunbound.neoforge.patches.LevelBlockSnapshots;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * NeoForge's patch to {@code LevelChunk}, for {@code BlockEvent.EntityPlaceEvent}: while the level captures block
 * snapshots, {@code setBlockState} doesn't run the new state's {@code onPlace}, which {@code CommonHooks.onPlaceItemIntoWorld}
 * runs once the event lets the placement stand (see {@code LevelMixin}). Block entities' {@code onLoad} and
 * {@code onChunkUnloaded} and the update tag are LevelChunkBlockEntitiesMixin's (step 5). Not ported from this patch: the
 * light properties check with the position, the auxiliary light manager, attachments, keeping a removed block entity in
 * {@code getBlockEntity}, and catching a block entity's exception on save.
 */
@Mixin(LevelChunk.class)
abstract class LevelChunkMixin {
    @Shadow
    @Final
    private Level level;

    @WrapWithCondition(method = "setBlockState", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;onPlace(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)V"))
    private boolean versionunbound$placeUnlessCapturing(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        return !((LevelBlockSnapshots) this.level).versionunbound$captureBlockSnapshots();
    }
}
