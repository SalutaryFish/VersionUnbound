package net.salutaryfish.versionunbound.neoforge.mixin.events;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NeoForge's patch to {@code Camel}: {@code LivingJumpEvent} for a camel's dash, which doesn't go through
 * {@code LivingEntity.jumpFromGround}.
 */
@Mixin(Camel.class)
abstract class CamelMixin {
    @Inject(method = "executeRidersJump", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/animal/camel/Camel;needsSync:Z", opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER))
    private void versionunbound$livingJump(float amount, Vec3 input, CallbackInfo ci) {
        CommonHooks.onLivingJump((LivingEntity) (Object) this);
    }
}
