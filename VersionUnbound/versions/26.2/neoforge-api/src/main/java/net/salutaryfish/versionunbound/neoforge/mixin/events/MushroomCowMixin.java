package net.salutaryfish.versionunbound.neoforge.mixin.events;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.cow.MushroomCow;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.EventHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NeoForge's patch to {@code MushroomCow}: {@code LivingConversionEvent.Pre} when a mooshroom is sheared, after the
 * shearing sound (canceling it keeps the mooshroom and drops no mushrooms), and {@code Post} first thing in the
 * conversion callback.
 */
@Mixin(MushroomCow.class)
abstract class MushroomCowMixin {
    @Inject(
            method = "shear",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;playSound(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V", shift = At.Shift.AFTER),
            cancellable = true)
    private void versionunbound$canLivingConvert(ServerLevel level, SoundSource soundSource, ItemStack tool, CallbackInfo ci) {
        if (!EventHooks.canLivingConvert((LivingEntity) (Object) this, EntityTypes.COW, timer -> {})) {
            ci.cancel();
        }
    }

    @Inject(method = "lambda$shear$0", at = @At("HEAD"))
    private void versionunbound$onLivingConvert(ServerLevel level, ItemStack tool, Cow cow, CallbackInfo ci) {
        EventHooks.onLivingConvert((LivingEntity) (Object) this, cow);
    }
}
