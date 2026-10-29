package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.event.EventHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * NeoForge's patch to {@code AbstractHurtingProjectile} (fireballs, wither skulls, wind charges):
 * {@code ProjectileImpactEvent} when the projectile hits something; a canceled impact counts as a miss, both for where
 * the projectile moves and for whether it hits.
 */
@Mixin(AbstractHurtingProjectile.class)
abstract class AbstractHurtingProjectileMixin {
    // Where the projectile moves to.
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/HitResult;getType()Lnet/minecraft/world/phys/HitResult$Type;", ordinal = 0))
    private HitResult.Type versionunbound$projectileImpact(HitResult hitResult, Operation<HitResult.Type> original, @Share("impactCanceled") LocalBooleanRef canceled) {
        HitResult.Type type = original.call(hitResult);
        if (type != HitResult.Type.MISS && EventHooks.onProjectileImpact((Projectile) (Object) this, hitResult)) {
            canceled.set(true);
            return HitResult.Type.MISS;
        }
        return type;
    }

    // Whether it hits.
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/HitResult;getType()Lnet/minecraft/world/phys/HitResult$Type;", ordinal = 1))
    private HitResult.Type versionunbound$impacted(HitResult hitResult, Operation<HitResult.Type> original, @Share("impactCanceled") LocalBooleanRef canceled) {
        return canceled.get() ? HitResult.Type.MISS : original.call(hitResult);
    }
}
