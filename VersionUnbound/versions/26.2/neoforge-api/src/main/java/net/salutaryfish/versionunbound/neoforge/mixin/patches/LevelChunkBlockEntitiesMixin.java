package net.salutaryfish.versionunbound.neoforge.mixin.patches;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.ValueInput;
import net.salutaryfish.versionunbound.neoforge.patches.LevelFreshBlockEntities;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NeoForge's patch to {@code LevelChunk}, the block entity extension parts: a block entity added to a loaded chunk, and
 * a chunk's block entities once the level has loaded it, go to the level's fresh block entities (their {@code onLoad},
 * LevelFreshBlockEntitiesMixin); clearing them tells each {@code onChunkUnloaded} first; and the block entities of a
 * chunk the client receives load their data through {@code handleUpdateTag}.
 */
@Mixin(LevelChunk.class)
abstract class LevelChunkBlockEntitiesMixin {
    @Shadow
    @Final
    private Level level;

    @Shadow
    public abstract Map<BlockPos, BlockEntity> getBlockEntities();

    @Inject(method = "addAndRegisterBlockEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/LevelChunk;updateBlockEntityTicker(Lnet/minecraft/world/level/block/entity/BlockEntity;)V", shift = At.Shift.AFTER))
    private void versionunbound$freshBlockEntity(BlockEntity blockEntity, CallbackInfo ci) {
        ((LevelFreshBlockEntities) this.level).addFreshBlockEntities(List.of(blockEntity));
    }

    @Inject(method = "registerAllBlockEntitiesAfterLevelLoad", at = @At("HEAD"))
    private void versionunbound$freshBlockEntities(CallbackInfo ci) {
        ((LevelFreshBlockEntities) this.level).addFreshBlockEntities(this.getBlockEntities().values());
    }

    @Inject(method = "clearAllBlockEntities", at = @At("HEAD"))
    private void versionunbound$chunkUnloaded(CallbackInfo ci) {
        this.getBlockEntities().values().forEach(BlockEntity::onChunkUnloaded);
    }

    @WrapOperation(method = "lambda$replaceWithPacketData$0", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/entity/BlockEntity;loadWithComponents(Lnet/minecraft/world/level/storage/ValueInput;)V"))
    private void versionunbound$handleUpdateTag(BlockEntity blockEntity, ValueInput input, Operation<Void> original) {
        blockEntity.handleUpdateTag(input);
    }
}
