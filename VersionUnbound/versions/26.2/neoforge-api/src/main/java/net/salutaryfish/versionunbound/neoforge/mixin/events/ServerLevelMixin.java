package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProgressListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NeoForge's patch to {@code ServerLevel}, for the events it fires: entity ticks (cancellable), level saves, and players
 * joining the level (cancellable; other entities join in {@code PersistentEntitySectionManagerMixin}), and
 * {@code onAddedToLevel} once a player or a new entity is added.
 */
@Mixin(ServerLevel.class)
abstract class ServerLevelMixin {
    // Neo: Permit cancellation of Entity#tick via EntityTickEvent.Pre
    @WrapOperation(method = "tickNonPassenger", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;tick()V"))
    private void versionunbound$entityTick(Entity entity, Operation<Void> original) {
        if (!EventHooks.fireEntityTickPre(entity).isCanceled()) {
            original.call(entity);
            EventHooks.fireEntityTickPost(entity);
        }
    }

    @Inject(method = "addPlayer", at = @At("HEAD"), cancellable = true)
    private void versionunbound$playerJoinLevel(ServerPlayer player, CallbackInfo ci) {
        if (NeoForge.EVENT_BUS.post(new EntityJoinLevelEvent(player, (ServerLevel) (Object) this)).isCanceled()) ci.cancel();
    }

    // The event has fired above. NeoForge calls onAddedToLevel whether or not the add succeeded.
    @WrapOperation(method = "addPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/entity/PersistentEntitySectionManager;addNewEntity(Lnet/minecraft/world/level/entity/EntityAccess;)Z"))
    private boolean versionunbound$addPlayerWithoutEvent(PersistentEntitySectionManager<Entity> entityManager, EntityAccess player, Operation<Boolean> original) {
        boolean added = entityManager.addNewEntityWithoutEvent((Entity) player);
        ((Entity) player).onAddedToLevel();
        return added;
    }

    @WrapOperation(method = "addEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/entity/PersistentEntitySectionManager;addNewEntity(Lnet/minecraft/world/level/entity/EntityAccess;)Z"))
    private boolean versionunbound$entityAddedToLevel(PersistentEntitySectionManager<Entity> entityManager, EntityAccess entity, Operation<Boolean> original) {
        if (original.call(entityManager, entity)) {
            ((Entity) entity).onAddedToLevel();
            return true;
        } else {
            return false;
        }
    }

    // At the end of vanilla's "if (!noSave)" block, once the level data, chunks and entities are saved.
    @Inject(method = "save", at = @At("TAIL"))
    private void versionunbound$levelSave(@Nullable ProgressListener progressListener, boolean flush, boolean noSave, CallbackInfo ci) {
        if (!noSave) {
            NeoForge.EVENT_BUS.post(new LevelEvent.Save((ServerLevel) (Object) this));
        }
    }
}
