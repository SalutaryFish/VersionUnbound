package net.salutaryfish.versionunbound.neoforge.mixin.patches;

import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.world.level.chunk.LevelChunk;
import net.salutaryfish.versionunbound.neoforge.patches.ChunkHolderCurrentlyLoading;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

/** NeoForge's patch to {@code GenerationChunkHolder}: the {@code currentlyLoading} field (see {@link ChunkHolderCurrentlyLoading}). */
@Mixin(GenerationChunkHolder.class)
abstract class GenerationChunkHolderMixin implements ChunkHolderCurrentlyLoading {
    // Added under NeoForge's name, for mods that read it.
    public @Nullable LevelChunk currentlyLoading; // Forge: Used to bypass future chain when loading chunks.

    @Override
    public @Nullable LevelChunk versionunbound$currentlyLoading() {
        return this.currentlyLoading;
    }

    @Override
    public void versionunbound$setCurrentlyLoading(@Nullable LevelChunk chunk) {
        this.currentlyLoading = chunk;
    }
}
