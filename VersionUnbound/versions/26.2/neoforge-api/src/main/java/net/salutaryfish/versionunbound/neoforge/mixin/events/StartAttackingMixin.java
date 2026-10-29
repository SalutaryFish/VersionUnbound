package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.StartAttacking;
import net.minecraft.world.entity.ai.behavior.declarative.MemoryAccessor;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NeoForge's patch to {@code StartAttacking}, the behavior that sets a brain-driven mob's attack target:
 * {@code LivingChangeTargetEvent} before the target is remembered. If the event is canceled or leaves no target, the
 * behavior doesn't start. The patched code is the behavior's inner lambda ({@code lambda$create$3} in the 26.2 jar).
 */
@Mixin(StartAttacking.class)
abstract class StartAttackingMixin {
    @Inject(method = "lambda$create$3", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/behavior/declarative/MemoryAccessor;set(Ljava/lang/Object;)V"), cancellable = true)
    private static void versionunbound$changeTarget(CallbackInfoReturnable<Boolean> cir, @Local(argsOnly = true) Mob body, @Local LivingEntity targetEntity,
            @Share("changeTarget") LocalRef<LivingChangeTargetEvent> event) {
        LivingChangeTargetEvent changeTargetEvent = CommonHooks.onLivingChangeTarget(body, targetEntity, LivingChangeTargetEvent.LivingTargetType.BEHAVIOR_TARGET);
        if (changeTargetEvent.isCanceled() || changeTargetEvent.getNewAboutToBeSetTarget() == null) {
            cir.setReturnValue(false);
        } else {
            event.set(changeTargetEvent);
        }
    }

    @WrapOperation(method = "lambda$create$3", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/behavior/declarative/MemoryAccessor;set(Ljava/lang/Object;)V"))
    private static void versionunbound$rememberNewTarget(MemoryAccessor<?, ?> attackTarget, Object target, Operation<Void> original,
            @Share("changeTarget") LocalRef<LivingChangeTargetEvent> event) {
        original.call(attackTarget, event.get().getNewAboutToBeSetTarget());
    }
}
