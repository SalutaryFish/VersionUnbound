package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.event.EventHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * NeoForge's patch to {@code FishingHook}: {@code ProjectileImpactEvent} when a flying hook hits something
 * (cancellable). NeoForge's {@code checkCollision} calls {@code onHit} where vanilla calls {@code hitTargetOrDeflectSelf},
 * so a hook isn't deflected; this mixin does the same. Not yet: the rest of the patch (the rods' item abilities, with
 * the item extension in Phase 2 step 5, and {@code ItemFishedEvent}).
 */
@Mixin(FishingHook.class)
abstract class FishingHookMixin extends Projectile {
    private FishingHookMixin(EntityType<? extends Projectile> type, Level level) {
        super(type, level);
    }

    @WrapOperation(method = "checkCollision", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/projectile/FishingHook;hitTargetOrDeflectSelf(Lnet/minecraft/world/phys/HitResult;)Lnet/minecraft/world/entity/projectile/ProjectileDeflection;"))
    private ProjectileDeflection versionunbound$projectileImpact(FishingHook self, HitResult hitResult, Operation<ProjectileDeflection> original) {
        if (hitResult.getType() == HitResult.Type.MISS || !EventHooks.onProjectileImpact(self, hitResult)) this.onHit(hitResult);
        return ProjectileDeflection.NONE;
    }
}
