package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.salutaryfish.versionunbound.neoforge.patches.ChunkHolderCurrentlyLoading;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NeoForge's patch to {@code ServerChunkCache}, for {@code ChunkEvent.Load}: a chunk that is finishing loading
 * ({@code ChunkStatusTasksMixin}) is returned at once instead of waiting for its future, which is the task asking for it.
 */
@Mixin(ServerChunkCache.class)
abstract class ServerChunkCacheMixin {
    @Shadow
    private @Nullable ChunkHolder getVisibleChunkIfPresent(long key) {
        throw new AssertionError();
    }

    // After the last-chunk cache, before the cache miss is counted. (@Local doesn't find the method's pos local here, after
    // the cache loop, so it's computed again.)
    @Inject(method = "getChunk", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/profiling/ProfilerFiller;incrementCounter(Ljava/lang/String;)V", ordinal = 1), cancellable = true)
    private void versionunbound$loadingChunk(int x, int z, ChunkStatus targetStatus, boolean loadOrGenerate, CallbackInfoReturnable<@Nullable ChunkAccess> cir) {
        ChunkHolder chunkholder = this.getVisibleChunkIfPresent(ChunkPos.pack(x, z));
        if (chunkholder != null && ((ChunkHolderCurrentlyLoading) chunkholder).versionunbound$currentlyLoading() != null)
            cir.setReturnValue(((ChunkHolderCurrentlyLoading) chunkholder).versionunbound$currentlyLoading()); // Neo: If the requested chunk is loading, bypass the future chain to prevent a deadlock.
    }

    @Inject(method = "getChunkNow", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ChunkHolder;getChunkIfPresent(Lnet/minecraft/world/level/chunk/status/ChunkStatus;)Lnet/minecraft/world/level/chunk/ChunkAccess;"), cancellable = true)
    private void versionunbound$loadingChunkNow(int x, int z, CallbackInfoReturnable<@Nullable LevelChunk> cir, @Local ChunkHolder chunkHolder) {
        LevelChunk loading = ((ChunkHolderCurrentlyLoading) chunkHolder).versionunbound$currentlyLoading();
        if (loading != null) cir.setReturnValue(loading); // Forge: If the requested chunk is loading, bypass the future chain to prevent a deadlock.
    }
}
