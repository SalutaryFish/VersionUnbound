package net.salutaryfish.versionunbound.neoforge.client.mixin.events;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NeoForge's patch to {@code ClientLevel.EntityCallbacks}: {@code EntityLeaveLevelEvent} once the level has stopped
 * tracking an entity. Not yet: {@code onRemovedFromLevel} just before it, and multipart entities' parts, which come with
 * the entity extension (Phase 2 step 5).
 */
@Mixin(targets = "net.minecraft.client.multiplayer.ClientLevel$EntityCallbacks")
abstract class ClientLevelEntityCallbacksMixin {
    @Shadow
    @Final
    ClientLevel this$0;

    @Inject(method = "onTrackingEnd(Lnet/minecraft/world/entity/Entity;)V", at = @At("TAIL"))
    private void versionunbound$entityLeaveLevel(Entity entity, CallbackInfo ci) {
        NeoForge.EVENT_BUS.post(new EntityLeaveLevelEvent(entity, this.this$0));
    }
}
