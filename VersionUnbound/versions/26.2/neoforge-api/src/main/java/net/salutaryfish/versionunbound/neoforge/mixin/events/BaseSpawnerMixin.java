package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.mojang.datafixers.util.Either;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.SpawnData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.common.extensions.IOwnedSpawner;
import net.neoforged.neoforge.event.EventHooks;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * NeoForge's patch to {@code BaseSpawner} (mob spawner blocks and minecarts): {@code MobSpawnEvent.PositionCheck} in
 * place of vanilla's spawn rule and obstruction checks, {@code FinalizeSpawnEvent} for every mob (vanilla calls
 * {@code finalizeSpawn} only when the spawn data has nothing but an id; the event still fires otherwise), and
 * {@link IOwnedSpawner#getOwner()}, which the two vanilla spawners override
 * ({@code SpawnerBlockEntitySpawnerMixin}, {@code MinecartSpawnerSpawnerMixin}).
 */
@Mixin(BaseSpawner.class)
abstract class BaseSpawnerMixin implements IOwnedSpawner {
    // Vanilla: `if (customSpawnRules.isEmpty() && !mob.checkSpawnRules(...) || !mob.checkSpawnObstruction(level))
    // continue;`. NeoForge: `if (!checkSpawnPositionSpawner(...)) continue;`. The first term becomes false (so the rules
    // aren't checked) and the obstruction check answers with the event's result.
    @Definition(id = "getCustomSpawnRules", method = "Lnet/minecraft/world/level/SpawnData;getCustomSpawnRules()Ljava/util/Optional;")
    @Definition(id = "isEmpty", method = "Ljava/util/Optional;isEmpty()Z")
    @Expression("?.getCustomSpawnRules().isEmpty()")
    @WrapOperation(method = "serverTick", at = @At("MIXINEXTRAS:EXPRESSION"))
    private boolean versionunbound$checkSpawnPosition(Optional<?> customSpawnRules, Operation<Boolean> original,
            @Local(argsOnly = true) ServerLevel level, @Local Mob mob, @Local SpawnData nextSpawnData, @Share("spawnPosition") LocalBooleanRef spawnPosition) {
        spawnPosition.set(EventHooks.checkSpawnPositionSpawner(mob, level, EntitySpawnReason.SPAWNER, nextSpawnData, (BaseSpawner) (Object) this));
        return false;
    }

    @WrapOperation(method = "serverTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Mob;checkSpawnObstruction(Lnet/minecraft/world/level/LevelReader;)Z"))
    private boolean versionunbound$checkedSpawnPosition(Mob mob, LevelReader level, Operation<Boolean> original, @Share("spawnPosition") LocalBooleanRef spawnPosition) {
        return spawnPosition.get();
    }

    // Neo: Patch in FinalizeSpawn for spawners so it may be fired unconditionally, instead of only when vanilla would normally call it.
    // The local hasNoConfiguration is the conditions under which the spawner will normally call Mob#finalizeSpawn.
    // VersionUnbound: the hook calls finalizeSpawn when vanilla would; vanilla's own call is skipped.
    @ModifyVariable(method = "serverTick", at = @At("STORE"), name = "hasNoConfiguration")
    private boolean versionunbound$finalizeSpawn(boolean hasNoConfiguration, @Local(argsOnly = true) ServerLevel level, @Local Mob mob) {
        EventHooks.finalizeMobSpawnSpawner(mob, level, level.getCurrentDifficultyAt(mob.blockPosition()), EntitySpawnReason.SPAWNER, null, this, hasNoConfiguration);
        return false;
    }

    @Override
    public @Nullable Either<BlockEntity, Entity> getOwner() {
        // The vanilla anonymous classes have proper overrides, but we return null here for compatibility.
        return null;
    }
}
