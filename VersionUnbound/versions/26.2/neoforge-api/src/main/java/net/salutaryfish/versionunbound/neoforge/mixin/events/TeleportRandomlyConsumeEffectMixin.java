package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.consume_effects.TeleportRandomlyConsumeEffect;
import net.salutaryfish.versionunbound.neoforge.patches.LivingEntityRandomTeleport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * NeoForge's patch to {@code TeleportRandomlyConsumeEffect} (chorus fruit): the teleport goes through NeoForge's
 * {@code randomTeleport} overload with the consumed stack, which fires {@code EntityTeleportEvent.ItemConsumption}.
 */
@Mixin(TeleportRandomlyConsumeEffect.class)
abstract class TeleportRandomlyConsumeEffectMixin {
    // Neo: Pass on the consumed stack to fire the appropriate sub-event of EntityTeleportEvent
    @WrapOperation(method = "apply", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;randomTeleport(DDDZ)Z"))
    private boolean versionunbound$withConsumedStack(LivingEntity user, double xx, double yy, double zz, boolean showParticles, Operation<Boolean> original,
            @Local(argsOnly = true) ItemStack stack) {
        return ((LivingEntityRandomTeleport) user).randomTeleport(xx, yy, zz, showParticles, stack);
    }
}
