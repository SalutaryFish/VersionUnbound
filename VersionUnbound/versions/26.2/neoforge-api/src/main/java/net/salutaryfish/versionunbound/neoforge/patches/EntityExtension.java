package net.salutaryfish.versionunbound.neoforge.patches;

import net.minecraft.world.entity.Entity;

/**
 * NeoForge's {@code isAddedToLevel} on {@link Entity}, injected into it by the class tweaker: whether a level tracks
 * the entity, set when a level adds it and cleared when the level stops tracking it (the call sites are
 * {@code ServerLevelMixin}, {@code PersistentEntitySectionManagerMixin}, {@code ClientLevelMixin} and the levels'
 * {@code EntityCallbacks} mixins). {@code EntityMixin} keeps the flag through {@link AddedToLevel}.
 * <p>
 * {@code onAddedToLevel} and {@code onRemovedFromLevel} are NeoForge's overridable callbacks (declared in its
 * {@code IEntityExtension}); mods' overrides, which call {@code super}, run already. In NeoForge
 * {@code isAddedToLevel} is final. When the entity extension is ported (Phase 2 step 5), it declares the same methods.
 */
public interface EntityExtension {
    default boolean isAddedToLevel() {
        return ((AddedToLevel) this).versionunbound$isAddedToLevel();
    }

    default void onAddedToLevel() {
        ((AddedToLevel) this).versionunbound$setAddedToLevel(true);
    }

    default void onRemovedFromLevel() {
        ((AddedToLevel) this).versionunbound$setAddedToLevel(false);
    }

    interface AddedToLevel {
        boolean versionunbound$isAddedToLevel();

        void versionunbound$setAddedToLevel(boolean added);
    }
}
