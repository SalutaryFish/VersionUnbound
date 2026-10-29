package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatusTasks;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.salutaryfish.versionunbound.neoforge.patches.ChunkHolderCurrentlyLoading;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NeoForge's patch to {@code ChunkStatusTasks}: {@code ChunkEvent.Load} at the end of the full-chunk task
 * ({@code lambda$full$0}, the main-thread task {@code full} schedules), with the holder's {@code currentlyLoading} set
 * around {@code runPostLoad} and around the block entity registration and the event, so that a {@code getChunk} for this
 * chunk returns it instead of waiting for the task it's running in ({@code ServerChunkCacheMixin}). NeoForge clears it
 * in {@code finally} blocks; here the method wrapper clears it whichever way the task ends.
 */
@Mixin(ChunkStatusTasks.class)
abstract class ChunkStatusTasksMixin {
    @Inject(method = "lambda$full$0", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/LevelChunk;runPostLoad()V"))
    private static void versionunbound$loadingPostLoad(ChunkAccess chunk, WorldGenContext context, GenerationChunkHolder holder, CallbackInfoReturnable<ChunkAccess> cir, @Local LevelChunk levelChunk) {
        ((ChunkHolderCurrentlyLoading) holder).versionunbound$setCurrentlyLoading(levelChunk); // Neo: bypass the future chain when getChunk is called, this prevents deadlocks.
    }

    @Inject(method = "lambda$full$0", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/LevelChunk;runPostLoad()V", shift = At.Shift.AFTER))
    private static void versionunbound$loadedPostLoad(ChunkAccess chunk, WorldGenContext context, GenerationChunkHolder holder, CallbackInfoReturnable<ChunkAccess> cir) {
        ((ChunkHolderCurrentlyLoading) holder).versionunbound$setCurrentlyLoading(null); // Neo: Stop bypassing the future chain.
    }

    @Inject(method = "lambda$full$0", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/LevelChunk;registerAllBlockEntitiesAfterLevelLoad()V"))
    private static void versionunbound$loadingBlockEntities(ChunkAccess chunk, WorldGenContext context, GenerationChunkHolder holder, CallbackInfoReturnable<ChunkAccess> cir, @Local LevelChunk levelChunk) {
        ((ChunkHolderCurrentlyLoading) holder).versionunbound$setCurrentlyLoading(levelChunk); // Neo: bypass the future chain when getChunk is called, this prevents deadlocks.
    }

    @Inject(method = "lambda$full$0", at = @At("RETURN"))
    private static void versionunbound$chunkLoad(ChunkAccess chunk, WorldGenContext context, GenerationChunkHolder holder, CallbackInfoReturnable<ChunkAccess> cir, @Local LevelChunk levelChunk) {
        NeoForge.EVENT_BUS.post(new ChunkEvent.Load(levelChunk, !(chunk instanceof ImposterProtoChunk)));
    }

    @WrapMethod(method = "lambda$full$0")
    private static ChunkAccess versionunbound$stopBypass(ChunkAccess chunk, WorldGenContext context, GenerationChunkHolder holder, Operation<ChunkAccess> original) {
        try {
            return original.call(chunk, context, holder);
        } finally {
            ((ChunkHolderCurrentlyLoading) holder).versionunbound$setCurrentlyLoading(null); // Neo: Stop bypassing the future chain.
        }
    }
}
