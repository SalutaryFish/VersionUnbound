package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.level.ChunkDataEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NeoForge's patch to {@code ChunkMap}, for the events it fires: a chunk's ticket level changing, a chunk unloading
 * (before the level forgets it), chunk data loaded from disk and about to be saved, and a player starting and stopping
 * to watch a chunk. The lambdas are targeted by their 26.2 names ({@code lambda$scheduleUnload$0} is the unload task,
 * {@code lambda$scheduleChunkLoad$3} the read on the main thread).
 *
 * <p>Not ported from this patch: its two fixes for vanilla's unload leaks ({@code CommonHooks.onChunkUnload}, which
 * drops the chunk's points of interest from {@code PoiManager}'s cache through a {@code SectionStorage.remove} the patch
 * adds, and removing the chunk from {@code chunkTypeCache}), forced natural spawning for chunks with NeoForge's tickets,
 * {@code getPlayersWatching}, {@code scheduleOnMainThreadMailbox} and {@code PartEntity} (multipart entities, Phase 2
 * step 5).
 */
@Mixin(ChunkMap.class)
abstract class ChunkMapMixin {
    @Shadow
    @Final
    private ServerLevel level;

    // Not on the early return (the chunk is neither loaded before nor after).
    @Inject(method = "updateChunkScheduling", at = @At("TAIL"))
    private void versionunbound$ticketLevelUpdated(long node, int level, ChunkHolder chunk, int oldLevel, CallbackInfoReturnable<ChunkHolder> cir) {
        EventHooks.fireChunkTicketLevelUpdated(this.level, node, oldLevel, level, cir.getReturnValue());
    }

    @WrapOperation(method = "lambda$scheduleUnload$0", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;unload(Lnet/minecraft/world/level/chunk/LevelChunk;)V"))
    private void versionunbound$chunkUnload(ServerLevel level, LevelChunk levelChunk, Operation<Void> original) {
        NeoForge.EVENT_BUS.post(new ChunkEvent.Unload(levelChunk));
        original.call(level, levelChunk);
    }

    @WrapOperation(method = "lambda$scheduleChunkLoad$3", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/storage/SerializableChunkData;read(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/ai/village/poi/PoiManager;Lnet/minecraft/world/level/chunk/storage/RegionStorageInfo;Lnet/minecraft/world/level/ChunkPos;)Lnet/minecraft/world/level/chunk/ProtoChunk;"))
    private ProtoChunk versionunbound$chunkDataLoad(SerializableChunkData data, ServerLevel level, PoiManager poiManager, RegionStorageInfo info, ChunkPos pos, Operation<ProtoChunk> original) {
        ProtoChunk chunk = original.call(data, level, poiManager, info, pos);
        NeoForge.EVENT_BUS.post(new ChunkDataEvent.Load(chunk, data));
        return chunk;
    }

    @ModifyExpressionValue(method = "save", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/storage/SerializableChunkData;copyOf(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;)Lnet/minecraft/world/level/chunk/storage/SerializableChunkData;"))
    private SerializableChunkData versionunbound$chunkDataSave(SerializableChunkData data, @Local(argsOnly = true) ChunkAccess chunk) {
        NeoForge.EVENT_BUS.post(new ChunkDataEvent.Save(chunk, this.level, data));
        return data;
    }

    @Inject(method = "markChunkPendingToSend(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/level/chunk/LevelChunk;)V", at = @At("TAIL"))
    private static void versionunbound$chunkWatch(ServerPlayer player, LevelChunk chunk, CallbackInfo ci) {
        EventHooks.fireChunkWatch(player, chunk, player.level());
    }

    @Inject(method = "dropChunk", at = @At("HEAD"))
    private static void versionunbound$chunkUnWatch(ServerPlayer player, ChunkPos pos, CallbackInfo ci) {
        EventHooks.fireChunkUnWatch(player, pos, player.level());
    }
}
