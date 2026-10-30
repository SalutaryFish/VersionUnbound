package net.salutaryfish.versionunbound.neoforge.mixin.events;

import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.skeleton.Stray;
import net.neoforged.neoforge.event.EventHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NeoForge's patch to {@code Skeleton}: {@code LivingConversionEvent.Pre} before a frozen skeleton turns into a stray
 * (canceling it keeps the skeleton; the timer has run out, so it fires again next tick unless a listener sets it), and
 * {@code Post} first thing in the conversion callback.
 */
@Mixin(Skeleton.class)
abstract class SkeletonMixin {
    @Shadow
    private int conversionTime;

    @Inject(method = "doFreezeConversion", at = @At("HEAD"), cancellable = true)
    private void versionunbound$canLivingConvert(CallbackInfo ci) {
        if (!EventHooks.canLivingConvert((LivingEntity) (Object) this, EntityTypes.STRAY, timer -> this.conversionTime = timer)) {
            ci.cancel();
        }
    }

    @Inject(method = "lambda$doFreezeConversion$0", at = @At("HEAD"))
    private void versionunbound$onLivingConvert(Stray stray, CallbackInfo ci) {
        EventHooks.onLivingConvert((LivingEntity) (Object) this, stray);
    }
}
