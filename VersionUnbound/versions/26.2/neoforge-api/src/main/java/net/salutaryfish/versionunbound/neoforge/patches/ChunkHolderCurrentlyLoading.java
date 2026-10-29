package net.salutaryfish.versionunbound.neoforge.patches;

import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

/**
 * The chunk NeoForge's patch to {@link GenerationChunkHolder} records while it finishes loading (its public
 * {@code currentlyLoading} field, which {@code GenerationChunkHolderMixin} adds under that name). While it's set,
 * {@code ServerChunkCache.getChunk} and {@code getChunkNow} return it instead of waiting for the chunk's future, which
 * would deadlock a {@code ChunkEvent.Load} listener (or a block entity) asking for its own chunk. Our code reaches the
 * field through this interface, which the mixin implements.
 */
public interface ChunkHolderCurrentlyLoading {
    @Nullable LevelChunk versionunbound$currentlyLoading();

    void versionunbound$setCurrentlyLoading(@Nullable LevelChunk chunk);
}
