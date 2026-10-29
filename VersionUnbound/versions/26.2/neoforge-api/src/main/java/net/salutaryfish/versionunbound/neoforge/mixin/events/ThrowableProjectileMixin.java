package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.event.EventHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * NeoForge's patch to {@code ThrowableProjectile}: {@code ProjectileImpactEvent} before a thrown projectile moves to
 * what it hit. NeoForge's patch only changes that check, so a canceled impact moves the projectile on as if it had
 * missed, but the hit check at the end of {@code tick} still sees the hit; this mixin does the same.
 */
@Mixin(ThrowableProjectile.class)
abstract class ThrowableProjectileMixin {
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/HitResult;getType()Lnet/minecraft/world/phys/HitResult$Type;", ordinal = 0))
    private HitResult.Type versionunbound$projectileImpact(HitResult result, Operation<HitResult.Type> original) {
        HitResult.Type type = original.call(result);
        return type != HitResult.Type.MISS && EventHooks.onProjectileImpact((Projectile) (Object) this, result) ? HitResult.Type.MISS : type;
    }
}
