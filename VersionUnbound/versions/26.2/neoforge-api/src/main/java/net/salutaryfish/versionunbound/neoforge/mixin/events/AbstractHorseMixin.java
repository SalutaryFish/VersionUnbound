package net.salutaryfish.versionunbound.neoforge.mixin.events;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NeoForge's patch to {@code AbstractHorse}: {@code LivingJumpEvent} for a ridden horse's jump, which doesn't go through
 * {@code LivingEntity.jumpFromGround}.
 */
@Mixin(AbstractHorse.class)
abstract class AbstractHorseMixin {
    @Inject(method = "executeRidersJump", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/animal/equine/AbstractHorse;needsSync:Z", opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER))
    private void versionunbound$livingJump(float amount, Vec3 input, CallbackInfo ci) {
        CommonHooks.onLivingJump((LivingEntity) (Object) this);
    }
}
