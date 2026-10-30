package net.salutaryfish.versionunbound.neoforge.patches;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.EventHooks;

/**
 * NeoForge's members on {@link Entity}, injected into it by the class tweaker. {@code isAddedToLevel}: whether a level tracks
 * the entity, set when a level adds it and cleared when the level stops tracking it (the call sites are
 * {@code ServerLevelMixin}, {@code PersistentEntitySectionManagerMixin}, {@code ClientLevelMixin} and the levels'
 * {@code EntityCallbacks} mixins). {@code EntityMixin} keeps the flag through {@link AddedToLevel}.
 * <p>
 * {@code onAddedToLevel} and {@code onRemovedFromLevel} are NeoForge's overridable callbacks (declared in its
 * {@code IEntityExtension}); mods' overrides, which call {@code super}, run already. In NeoForge
 * {@code isAddedToLevel} is final.
 * <p>
 * {@code canTrample} is NeoForge's default for farmland trampling (vanilla's condition in {@code FarmlandBlock.fallOn},
 * with the mob griefing game rule asked through {@code EntityMobGriefingEvent}), which its {@code IEntityExtension}
 * declares; mods override it. When the entity extension is ported (Phase 2 step 5), it declares the same methods.
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

    // Neo: Set the default behavior for trampling on Farmland
    default boolean canTrample(ServerLevel level, BlockState state, BlockPos pos, double fallDistance) {
        Entity self = (Entity) this;
        return level.getRandom().nextFloat() < fallDistance - 0.5F
             && self instanceof LivingEntity
             && (self instanceof Player || EventHooks.canEntityGrief(level, self))
             && self.getBbWidth() * self.getBbWidth() * self.getBbHeight() > 0.512F;
    }

    interface AddedToLevel {
        boolean versionunbound$isAddedToLevel();

        void versionunbound$setAddedToLevel(boolean added);
    }
}
