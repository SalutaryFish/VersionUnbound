package net.salutaryfish.versionunbound.neoforge.patches;

import java.util.Collection;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import org.jspecify.annotations.Nullable;

/**
 * NeoForge's drop capture on {@link Entity} (its {@code IEntityExtension.captureDrops} methods): while a collection is
 * set, the item entities {@code spawnAtLocation} makes go into it instead of into the level. {@code EntityMixin} adds
 * these methods to {@code Entity} and this interface; our code casts to it, and mods find the methods by name. When the
 * entity extension is ported (Phase 2 step 5), it declares the same methods.
 */
public interface EntityDropsCapture {
    @Nullable
    Collection<ItemEntity> captureDrops();

    /** Sets the collection to capture into, or null to stop, and returns the previous one. */
    @Nullable
    Collection<ItemEntity> captureDrops(@Nullable Collection<ItemEntity> value);
}
