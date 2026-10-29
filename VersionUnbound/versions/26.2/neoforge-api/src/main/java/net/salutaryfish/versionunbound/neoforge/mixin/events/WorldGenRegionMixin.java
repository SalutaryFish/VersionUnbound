package net.salutaryfish.versionunbound.neoforge.mixin.events;

import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NeoForge's patch to {@code WorldGenRegion}: a mob whose spawn {@code FinalizeSpawnEvent} cancelled isn't added to the
 * chunk being generated. The chunk keeps its entities as saved data, which doesn't include the cancellation, so the
 * {@code EntityJoinLevelEvent} listener that blocks cancelled spawns can't see it once the chunk loads.
 */
@Mixin(WorldGenRegion.class)
abstract class WorldGenRegionMixin {
    @Inject(method = "addFreshEntity", at = @At("HEAD"), cancellable = true)
    private void versionunbound$spawnCancelled(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof Mob mob && mob.isSpawnCancelled()) cir.setReturnValue(false);
    }
}
