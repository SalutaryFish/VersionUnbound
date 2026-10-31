package net.salutaryfish.versionunbound.neoforge.mixin.patches;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NeoForge's patch to {@code LivingEntity.swing}: the swung stack's {@code onEntitySwing} runs first, and answering
 * true cancels the swing.
 */
@Mixin(LivingEntity.class)
abstract class LivingEntitySwingMixin {
    @Inject(method = "swing(Lnet/minecraft/world/InteractionHand;Z)V", at = @At("HEAD"), cancellable = true)
    private void versionunbound$entitySwing(InteractionHand hand, boolean sendToSwingingEntity, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        ItemStack stack = self.getItemInHand(hand);
        if (!stack.isEmpty() && stack.onEntitySwing(self, hand)) ci.cancel();
    }
}
