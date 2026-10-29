package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.event.EventHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * NeoForge's patch to {@code ShulkerBullet}: {@code ProjectileImpactEvent} when the bullet hits something (cancellable).
 */
@Mixin(ShulkerBullet.class)
abstract class ShulkerBulletMixin {
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/projectile/ShulkerBullet;hitTargetOrDeflectSelf(Lnet/minecraft/world/phys/HitResult;)Lnet/minecraft/world/entity/projectile/ProjectileDeflection;"))
    private ProjectileDeflection versionunbound$projectileImpact(ShulkerBullet self, HitResult hitResult, Operation<ProjectileDeflection> original) {
        return EventHooks.onProjectileImpact(self, hitResult) ? ProjectileDeflection.NONE : original.call(self, hitResult);
    }
}
