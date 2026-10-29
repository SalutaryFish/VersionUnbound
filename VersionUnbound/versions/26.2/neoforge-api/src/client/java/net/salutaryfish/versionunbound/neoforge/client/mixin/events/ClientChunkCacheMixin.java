package net.salutaryfish.versionunbound.neoforge.client.mixin.events;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NeoForge's patch to {@code ClientChunkCache}: {@code ChunkEvent.Unload} before a chunk is dropped, and
 * {@code ChunkEvent.Load} (never a new chunk) once a chunk from the server is in the level.
 */
@Mixin(ClientChunkCache.class)
abstract class ClientChunkCacheMixin {
    @Inject(method = "drop", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientChunkCache$Storage;drop(ILnet/minecraft/world/level/chunk/LevelChunk;)V"))
    private void versionunbound$chunkUnload(ChunkPos pos, CallbackInfo ci, @Local LevelChunk currentChunk) {
        NeoForge.EVENT_BUS.post(new ChunkEvent.Unload(currentChunk));
    }

    @Inject(method = "replaceWithPacketData", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;onChunkLoaded(Lnet/minecraft/world/level/ChunkPos;)V", shift = At.Shift.AFTER))
    private void versionunbound$chunkLoad(CallbackInfoReturnable<LevelChunk> cir, @Local LevelChunk chunk) {
        NeoForge.EVENT_BUS.post(new ChunkEvent.Load(chunk, false));
    }
}
