package net.salutaryfish.versionunbound.neoforge.patches;

import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;

/**
 * NeoForge's patch to {@link PersistentEntitySectionManager}, injected into it by the class tweaker: adding an entity
 * without {@code EntityJoinLevelEvent}, for callers that have fired it already ({@code ServerLevel.addPlayer}).
 * {@code PersistentEntitySectionManagerMixin} implements it through {@link WithoutEvent}.
 */
public interface PersistentEntitySectionManagerExtension<T extends EntityAccess> {
    @SuppressWarnings("unchecked")
    default boolean addNewEntityWithoutEvent(T entity) {
        return ((WithoutEvent<T>) this).versionunbound$addNewEntityWithoutEvent(entity);
    }

    interface WithoutEvent<T extends EntityAccess> {
        boolean versionunbound$addNewEntityWithoutEvent(T entity);
    }
}
