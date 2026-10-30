package net.salutaryfish.versionunbound.neoforge.mixin.events;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.entity.animal.frog.Tadpole;
import net.neoforged.neoforge.event.EventHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NeoForge's patch to {@code Tadpole}: {@code LivingConversionEvent.Pre} before a grown tadpole turns into a frog
 * (canceling it keeps the tadpole; it has no timer to set, and its age keeps it trying), and {@code Post} first thing in
 * the conversion callback.
 */
@Mixin(Tadpole.class)
abstract class TadpoleMixin {
    @Inject(method = "ageUp()V", at = @At("HEAD"), cancellable = true)
    private void versionunbound$canLivingConvert(CallbackInfo ci) {
        if (!EventHooks.canLivingConvert((LivingEntity) (Object) this, EntityTypes.FROG, timer -> {})) {
            ci.cancel();
        }
    }

    @Inject(method = "lambda$ageUp$0", at = @At("HEAD"))
    private void versionunbound$onLivingConvert(ServerLevel serverLevel, Frog frog, CallbackInfo ci) {
        EventHooks.onLivingConvert((LivingEntity) (Object) this, frog);
    }
}
