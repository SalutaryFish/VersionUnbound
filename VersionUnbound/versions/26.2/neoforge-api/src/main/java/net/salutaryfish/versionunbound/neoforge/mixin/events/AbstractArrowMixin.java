package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import java.util.Collection;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.event.EventHooks;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * NeoForge's patch to {@code AbstractArrow}: {@code ProjectileImpactEvent} in {@code stepMoveAndHit}, for the block an
 * arrow hits and for the first (closest) of the entities it hits. A canceled impact ends the step, as NeoForge's
 * {@code break} does: without the hit and without {@code needsSync}. Not yet: fluid types putting out a burning arrow
 * (the entity extension's {@code canFluidExtinguish}, Phase 2 step 5).
 */
@Mixin(AbstractArrow.class)
abstract class AbstractArrowMixin {
    // The block: a canceled impact counts as a miss, which skips the hit and ends the step.
    @WrapOperation(method = "stepMoveAndHit", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/BlockHitResult;getType()Lnet/minecraft/world/phys/HitResult$Type;"))
    private HitResult.Type versionunbound$blockImpact(BlockHitResult blockHitResult, Operation<HitResult.Type> original) {
        HitResult.Type type = original.call(blockHitResult);
        return type != HitResult.Type.MISS && EventHooks.onProjectileImpact((AbstractArrow) (Object) this, blockHitResult) ? HitResult.Type.MISS : type;
    }

    // The entities, sorted by distance, so the first is NeoForge's firstEntityHit. Any deflection but NONE ends the step.
    @WrapOperation(method = "stepMoveAndHit", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/projectile/arrow/AbstractArrow;hitTargetsOrDeflectSelf(Ljava/util/Collection;)Lnet/minecraft/world/entity/projectile/ProjectileDeflection;"))
    private ProjectileDeflection versionunbound$entityImpact(AbstractArrow self, Collection<EntityHitResult> entitiesHit, Operation<ProjectileDeflection> original,
            @Share("impactCanceled") LocalBooleanRef canceled) {
        if (EventHooks.onProjectileImpact(self, entitiesHit.iterator().next())) {
            canceled.set(true);
            return ProjectileDeflection.REVERSE;
        }
        return original.call(self, entitiesHit);
    }

    @WrapWithCondition(method = "stepMoveAndHit", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/projectile/arrow/AbstractArrow;needsSync:Z", opcode = Opcodes.PUTFIELD, ordinal = 1))
    private boolean versionunbound$syncUnlessCanceled(AbstractArrow self, boolean needsSync, @Share("impactCanceled") LocalBooleanRef canceled) {
        return !canceled.get();
    }
}
