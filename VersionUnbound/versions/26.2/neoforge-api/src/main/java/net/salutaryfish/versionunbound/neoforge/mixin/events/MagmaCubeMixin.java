package net.salutaryfish.versionunbound.neoforge.mixin.events;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.cubemob.MagmaCube;
import net.neoforged.neoforge.common.CommonHooks;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NeoForge's patch to {@code MagmaCube}: {@code LivingJumpEvent} for a magma cube's own jump, which doesn't go through
 * {@code LivingEntity.jumpFromGround}.
 */
@Mixin(MagmaCube.class)
abstract class MagmaCubeMixin {
    @Inject(method = "jumpFromGround", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/monster/cubemob/MagmaCube;needsSync:Z", opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER))
    private void versionunbound$livingJump(CallbackInfo ci) {
        CommonHooks.onLivingJump((LivingEntity) (Object) this);
    }
}
