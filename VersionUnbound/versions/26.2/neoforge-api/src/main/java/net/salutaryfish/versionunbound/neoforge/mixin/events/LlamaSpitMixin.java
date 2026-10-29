package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.projectile.LlamaSpit;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.event.EventHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * NeoForge's patch to {@code LlamaSpit}: {@code ProjectileImpactEvent} when the spit hits something (cancellable).
 * Vanilla also hands a miss to {@code hitTargetOrDeflectSelf}; NeoForge's patch skips it, as this mixin does.
 */
@Mixin(LlamaSpit.class)
abstract class LlamaSpitMixin {
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/projectile/LlamaSpit;hitTargetOrDeflectSelf(Lnet/minecraft/world/phys/HitResult;)Lnet/minecraft/world/entity/projectile/ProjectileDeflection;"))
    private ProjectileDeflection versionunbound$projectileImpact(LlamaSpit self, HitResult hitResult, Operation<ProjectileDeflection> original) {
        if (hitResult.getType() != HitResult.Type.MISS && !EventHooks.onProjectileImpact(self, hitResult)) {
            return original.call(self, hitResult);
        }
        return ProjectileDeflection.NONE;
    }
}
