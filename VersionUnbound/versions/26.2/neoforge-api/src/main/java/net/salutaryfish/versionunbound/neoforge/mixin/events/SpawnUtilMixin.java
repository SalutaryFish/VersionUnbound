package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import net.minecraft.util.SpawnUtil;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.neoforged.neoforge.event.EventHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * NeoForge's patch to {@code SpawnUtil} (iron golems, wardens, creakings): {@code MobSpawnEvent.PositionCheck} in place
 * of vanilla's {@code mob.checkSpawnRules(...) && mob.checkSpawnObstruction(level)}.
 */
@Mixin(SpawnUtil.class)
abstract class SpawnUtilMixin {
    // The event decides, with both of vanilla's checks as its default; the obstruction check that follows is skipped.
    @WrapOperation(method = "trySpawnMob", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Mob;checkSpawnRules(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/world/entity/EntitySpawnReason;)Z"))
    private static boolean versionunbound$checkSpawnPosition(Mob mob, LevelAccessor level, EntitySpawnReason spawnReason, Operation<Boolean> original, @Share("checked") LocalBooleanRef checked) {
        checked.set(true);
        return EventHooks.checkSpawnPosition(mob, (ServerLevelAccessor) level, spawnReason);
    }

    @WrapOperation(method = "trySpawnMob", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Mob;checkSpawnObstruction(Lnet/minecraft/world/level/LevelReader;)Z"))
    private static boolean versionunbound$checkedSpawnPosition(Mob mob, LevelReader level, Operation<Boolean> original, @Share("checked") LocalBooleanRef checked) {
        if (checked.get()) {
            checked.set(false);
            return true;
        }
        return original.call(mob, level);
    }
}
