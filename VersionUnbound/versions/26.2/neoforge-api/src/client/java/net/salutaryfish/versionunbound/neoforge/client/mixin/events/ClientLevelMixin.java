package net.salutaryfish.versionunbound.neoforge.client.mixin.events;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NeoForge's patch to {@code ClientLevel}, for the events it fires: level load (at the end of the constructor), entity
 * ticks (cancellable) and entities joining the level (cancellable). Not yet: {@code onAddedToLevel} after an entity is
 * added (the entity extension, Phase 2 step 5).
 */
@Mixin(ClientLevel.class)
abstract class ClientLevelMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void versionunbound$loadLevel(CallbackInfo ci) {
        NeoForge.EVENT_BUS.post(new LevelEvent.Load((ClientLevel) (Object) this));
    }

    @Inject(method = "addEntity", at = @At("HEAD"), cancellable = true)
    private void versionunbound$entityJoinLevel(Entity entity, CallbackInfo ci) {
        if (NeoForge.EVENT_BUS.post(new EntityJoinLevelEvent(entity, (ClientLevel) (Object) this)).isCanceled()) ci.cancel();
    }

    // Neo: Permit cancellation of Entity#tick via EntityTickEvent.Pre
    @WrapOperation(method = "tickNonPassenger", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;tick()V"))
    private void versionunbound$entityTick(Entity entity, Operation<Void> original) {
        if (!EventHooks.fireEntityTickPre(entity).isCanceled()) {
            original.call(entity);
            EventHooks.fireEntityTickPost(entity);
        }
    }
}
