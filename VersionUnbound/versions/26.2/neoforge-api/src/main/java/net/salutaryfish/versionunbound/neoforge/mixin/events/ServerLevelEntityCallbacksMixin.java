package net.salutaryfish.versionunbound.neoforge.mixin.events;

import net.minecraft.server.level.ServerLevel;
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
 * NeoForge's patch to {@code ServerLevel.EntityCallbacks}: {@code EntityLeaveLevelEvent} once the level has stopped
 * tracking an entity, with {@code onRemovedFromLevel} just before it. Not yet: multipart entities' parts in place of the
 * ender dragon's, which come with the entity extension (Phase 2 step 5).
 */
@Mixin(targets = "net.minecraft.server.level.ServerLevel$EntityCallbacks")
abstract class ServerLevelEntityCallbacksMixin {
    @Shadow
    @Final
    ServerLevel this$0;

    @Inject(method = "onTrackingEnd(Lnet/minecraft/world/entity/Entity;)V", at = @At("TAIL"))
    private void versionunbound$entityLeaveLevel(Entity entity, CallbackInfo ci) {
        entity.onRemovedFromLevel();
        NeoForge.EVENT_BUS.post(new EntityLeaveLevelEvent(entity, this.this$0));
    }
}
