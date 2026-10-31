package net.salutaryfish.versionunbound.neoforge.patches;

import java.util.Collections;
import java.util.Set;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * NeoForge's patch to {@link BlockEntityType}, injected into it by the class tweaker (which opens {@code validBlocks}).
 * Not yet: its constructors (the varargs ones mods use, and {@code onlyOpCanSetNbt}).
 */
public interface BlockEntityTypeExtension {
    /**
     * Neo: Add getter for an immutable view of the set of valid blocks.
     */
    default Set<Block> getValidBlocks() {
        return Collections.unmodifiableSet(((BlockEntityType<?>) this).validBlocks);
    }
}
