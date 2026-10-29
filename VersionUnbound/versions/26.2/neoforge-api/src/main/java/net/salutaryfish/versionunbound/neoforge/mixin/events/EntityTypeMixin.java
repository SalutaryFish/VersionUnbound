package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NeoForge's patch to {@code EntityType}: a mob whose spawn {@code FinalizeSpawnEvent} cancelled is discarded, and
 * {@code create} (and so {@code spawn}) returns null. The call it follows is vanilla's {@code finalizeSpawn}, which
 * {@code CoremodMixinPlugin} has rewritten to {@code EventHooks.finalizeMobSpawn} by the time this injects.
 */
@Mixin(EntityType.class)
abstract class EntityTypeMixin<T extends Entity> {
    @Inject(method = "create(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/PostSpawnProcessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/EntitySpawnReason;ZZ)Lnet/minecraft/world/entity/Entity;",
            at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/event/EventHooks;finalizeMobSpawn(Lnet/minecraft/world/entity/Mob;Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/world/DifficultyInstance;Lnet/minecraft/world/entity/EntitySpawnReason;Lnet/minecraft/world/entity/SpawnGroupData;)Lnet/minecraft/world/entity/SpawnGroupData;", shift = At.Shift.AFTER),
            cancellable = true)
    private void versionunbound$spawnCancelled(CallbackInfoReturnable<T> cir, @Local Mob mob) {
        if (mob.isSpawnCancelled()) {
            // Neo: Discard mob, spawn was cancelled
            mob.discard();
            // return null, mob was killed, context should be lost
            // fixes llamas for wandering trader spawning if wandering trader was cancelled
            cir.setReturnValue(null);
        }
    }
}
