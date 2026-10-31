package net.salutaryfish.versionunbound.neoforge.patches;

import java.util.Collection;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * NeoForge's public {@code Level.addFreshBlockEntities}, which LevelFreshBlockEntitiesMixin adds under that name (and
 * implements this interface for): block entities added to the level hear {@code onLoad} at the start of the next block
 * entity tick.
 */
public interface LevelFreshBlockEntities {
    void addFreshBlockEntities(Collection<BlockEntity> beList);
}
