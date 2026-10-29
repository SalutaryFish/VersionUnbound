package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.ServerLevelAccessor;
import net.neoforged.neoforge.event.EventHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * NeoForge's patch to {@code SpawnPlacements}: {@code MobSpawnEvent.SpawnPlacementCheck} decides on the result of the
 * type's spawn predicate (or {@code true} without one). The peaceful check before it returns without the event, as on
 * NeoForge. The static methods the patch adds are in {@code SpawnPlacementsPatch}.
 */
@Mixin(SpawnPlacements.class)
abstract class SpawnPlacementsMixin {
    @ModifyReturnValue(method = "checkSpawnRules", at = @At(value = "RETURN", ordinal = 1))
    private static boolean versionunbound$spawnPlacementCheck(boolean vanillaResult, EntityType<?> type, ServerLevelAccessor level, EntitySpawnReason spawnReason, BlockPos pos, RandomSource random) {
        return EventHooks.checkSpawnPlacements(type, level, spawnReason, pos, random, vanillaResult);
    }
}
