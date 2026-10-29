package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * NeoForge's patch to {@code Mob}, for {@code LivingChangeTargetEvent} when a mob's target is set (cancellable; the event
 * can replace the target). Brain-driven mobs set theirs in {@code StartAttacking} ({@code StartAttackingMixin}).
 */
@Mixin(Mob.class)
abstract class MobMixin {
    // After vanilla's asValidTarget, before the field is set.
    @WrapOperation(method = "setTarget", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/Mob;target:Lnet/minecraft/world/entity/LivingEntity;", opcode = Opcodes.PUTFIELD))
    private void versionunbound$changeTarget(Mob mob, LivingEntity target, Operation<Void> original) {
        LivingChangeTargetEvent changeTargetEvent = CommonHooks.onLivingChangeTarget(mob, target, LivingChangeTargetEvent.LivingTargetType.MOB_TARGET);
        if (!changeTargetEvent.isCanceled()) {
            original.call(mob, changeTargetEvent.getNewAboutToBeSetTarget());
        }
    }
}
