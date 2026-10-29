package net.salutaryfish.versionunbound.neoforge.patches;

import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

/**
 * NeoForge's patch to {@link Mob}, injected into it by the class tweaker: the spawn type that {@code finalizeSpawn}
 * records (saved as {@code neoforge:spawn_type}) and the spawn cancellation of {@code FinalizeSpawnEvent}. The javadoc
 * is NeoForge's. {@code MobMixin} keeps the state through {@link SpawnState}; NeoForge's methods are final.
 */
public interface MobExtension {
    /**
     * Returns the type of spawn that created this mob, if applicable.
     * If it could not be determined, this will return null.
     * <p>
     * This is set via {@link Mob#finalizeSpawn}, so you should not call this from within that method, instead using the parameter.
     */
    @Nullable
    default EntitySpawnReason getSpawnType() {
        return ((SpawnState) this).versionunbound$getSpawnType();
    }

    /**
     * This method exists so that spawns can be cancelled from the {@link net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent FinalizeSpawnEvent}
     * without needing to hook up an additional handler for the {@link net.neoforged.neoforge.event.entity.EntityJoinLevelEvent EntityJoinLevelEvent}.
     *
     * @return if this mob will be blocked from spawning during {@link net.minecraft.world.level.Level#addFreshEntity(net.minecraft.world.entity.Entity)}
     * @apiNote Not public-facing API.
     */
    @ApiStatus.Internal
    default boolean isSpawnCancelled() {
        return ((SpawnState) this).versionunbound$isSpawnCancelled();
    }

    /**
     * Marks this mob as being disallowed to spawn during {@link net.minecraft.world.level.Level#addFreshEntity(net.minecraft.world.entity.Entity)}.
     * <p>
     *
     * @throws UnsupportedOperationException if this entity has already been {@link net.minecraft.world.entity.Entity#isAddedToLevel()} added to the level.
     * @apiNote Not public-facing API.
     */
    @ApiStatus.Internal
    default void setSpawnCancelled(boolean cancel) {
        if (((Mob) this).isAddedToLevel()) {
            throw new UnsupportedOperationException("Late invocations of Mob#setSpawnCancelled are not permitted.");
        }
        ((SpawnState) this).versionunbound$setSpawnCancelled(cancel);
    }

    interface SpawnState {
        @Nullable
        EntitySpawnReason versionunbound$getSpawnType();

        boolean versionunbound$isSpawnCancelled();

        void versionunbound$setSpawnCancelled(boolean cancel);
    }
}
