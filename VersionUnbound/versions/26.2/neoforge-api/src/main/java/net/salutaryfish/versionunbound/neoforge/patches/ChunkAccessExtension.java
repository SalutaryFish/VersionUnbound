package net.salutaryfish.versionunbound.neoforge.patches;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.jspecify.annotations.Nullable;

/**
 * NeoForge's patch to {@link ChunkAccess}, injected into it by the class tweaker: the level a chunk belongs to, if it
 * knows it. Only {@code LevelChunk} does (vanilla's own {@code getLevel} overrides this); proto chunks return null, as
 * in NeoForge, so {@code ChunkDataEvent.Load} for them has no level.
 */
public interface ChunkAccessExtension {
    // Neo: Allow for exposing the Level a chunk is tied to if available
    default @Nullable Level getLevel() {
        return null;
    }
}
