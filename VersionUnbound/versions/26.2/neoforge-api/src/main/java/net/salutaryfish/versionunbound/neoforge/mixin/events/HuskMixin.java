package net.salutaryfish.versionunbound.neoforge.mixin.events;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.neoforged.neoforge.event.EventHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NeoForge's patch to {@code Husk}: {@code LivingConversionEvent.Pre} before a drowning husk turns into a zombie
 * (canceling it keeps the husk). {@code Post} fires in {@code Zombie.convertToZombieType} ({@link ZombieMixin}).
 */
@Mixin(Husk.class)
abstract class HuskMixin {
    // NeoForge sets Zombie's conversionTime directly (its AT makes it protected); vanilla's setter does just that.
    @Inject(method = "doUnderWaterConversion", at = @At("HEAD"), cancellable = true)
    private void versionunbound$canLivingConvert(ServerLevel level, CallbackInfo ci) {
        Husk husk = (Husk) (Object) this;
        if (!EventHooks.canLivingConvert(husk, EntityTypes.ZOMBIE, husk::setConversionTime)) {
            ci.cancel();
        }
    }
}
