package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.datafixers.util.Either;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawner;
import net.neoforged.neoforge.common.extensions.IOwnedSpawner;
import net.neoforged.neoforge.event.EventHooks;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * NeoForge's patch to {@code TrialSpawner}: {@code FinalizeSpawnEvent} for every mob (vanilla calls
 * {@code finalizeSpawn} only when the spawn data has nothing but an id; the event still fires otherwise), and
 * {@link IOwnedSpawner#getOwner()}.
 */
@Mixin(TrialSpawner.class)
abstract class TrialSpawnerMixin implements IOwnedSpawner {
    @Shadow
    @Final
    private TrialSpawner.StateAccessor stateAccessor;

    // Neo: Patch in FinalizeSpawn for spawners so it may be fired unconditionally, instead of only when vanilla would normally call it.
    // The local flag is the conditions under which the spawner will normally call Mob#finalizeSpawn.
    // VersionUnbound: the hook calls finalizeSpawn when vanilla would; vanilla's own call is skipped.
    @ModifyVariable(method = "spawnMob", at = @At("STORE"), name = "hasNoConfiguration")
    private boolean versionunbound$finalizeSpawn(boolean hasNoConfiguration, @Local(argsOnly = true) ServerLevel level, @Local Mob mob) {
        EventHooks.finalizeMobSpawnSpawner(mob, level, level.getCurrentDifficultyAt(mob.blockPosition()), EntitySpawnReason.TRIAL_SPAWNER, null, this, hasNoConfiguration);
        return false;
    }

    @Override
    public @Nullable Either<BlockEntity, Entity> getOwner() {
        if (this.stateAccessor instanceof TrialSpawnerBlockEntity be) {
            return Either.left(be);
        }
        return null;
    }
}
