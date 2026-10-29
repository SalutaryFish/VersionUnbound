package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.event.EventHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * NeoForge's patch to {@code ThrowableProjectile} (snowballs, eggs, ender pearls, potions):
 * {@code ProjectileImpactEvent} when the projectile hits something; a canceled impact counts as a miss, both for where
 * the projectile moves and for whether it hits.
 */
@Mixin(ThrowableProjectile.class)
abstract class ThrowableProjectileMixin {
    // Where the projectile moves to.
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/HitResult;getType()Lnet/minecraft/world/phys/HitResult$Type;", ordinal = 0))
    private HitResult.Type versionunbound$projectileImpact(HitResult result, Operation<HitResult.Type> original, @Share("impactCanceled") LocalBooleanRef canceled) {
        HitResult.Type type = original.call(result);
        if (type != HitResult.Type.MISS && EventHooks.onProjectileImpact((Projectile) (Object) this, result)) {
            canceled.set(true);
            return HitResult.Type.MISS;
        }
        return type;
    }

    // Whether it hits.
    // VersionUnbound: NeoForge 26.2's patch guards only the move above, so a canceled projectile flies on and still hits
    // here, a step further on. NeoForge 1.21.1 and the event's javadoc ("the impact will not be processed") skip the hit.
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/HitResult;getType()Lnet/minecraft/world/phys/HitResult$Type;", ordinal = 1))
    private HitResult.Type versionunbound$impacted(HitResult result, Operation<HitResult.Type> original, @Share("impactCanceled") LocalBooleanRef canceled) {
        return canceled.get() ? HitResult.Type.MISS : original.call(result);
    }
}
