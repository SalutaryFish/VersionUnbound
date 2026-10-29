package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.Collection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.portal.TeleportTransition;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.EventHooks;
import net.salutaryfish.versionunbound.neoforge.patches.EntityDropsCapture;
import net.salutaryfish.versionunbound.neoforge.patches.EntityExtension;
import org.jspecify.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NeoForge's patch to {@code Entity}: a passenger's tick fires the entity tick events too (entities that aren't
 * passengers are ticked by their level, see {@code ServerLevelMixin} and {@code ClientLevelMixin}), drop capture
 * ({@link EntityDropsCapture}), which {@code LivingDropsEvent} uses, {@code EntityInvulnerabilityCheckEvent}, which
 * gets vanilla's answer and decides, {@code EntityTravelToDimensionEvent} for every teleport (cancellable),
 * {@code EntityMountEvent} when an entity starts or stops riding (cancellable), and the flag behind
 * {@code isAddedToLevel} ({@link EntityExtension}), with which a server entity that a level tracks loads the chunk it
 * moves into.
 */
@Mixin(Entity.class)
abstract class EntityMixin implements EntityDropsCapture, EntityExtension.AddedToLevel {
    // Neo: Short-lived holder of dropped item entities. Used mainly for Neo hooks and event logic.
    @Unique
    private @Nullable Collection<ItemEntity> versionunbound$captureDrops;

    /**
     * Neo: Internal use for keeping track of entities that are tracked by a world, to
     * allow guarantees that entity position changes will force a chunk load, avoiding
     * potential issues with entity desyncing and bad chunk data.
     */
    @Unique
    private boolean versionunbound$isAddedToLevel;

    @Override
    public boolean versionunbound$isAddedToLevel() {
        return this.versionunbound$isAddedToLevel;
    }

    @Override
    public void versionunbound$setAddedToLevel(boolean added) {
        this.versionunbound$isAddedToLevel = added;
    }

    @Override
    public @Nullable Collection<ItemEntity> captureDrops() {
        return this.versionunbound$captureDrops;
    }

    @Override
    public @Nullable Collection<ItemEntity> captureDrops(@Nullable Collection<ItemEntity> value) {
        Collection<ItemEntity> ret = this.versionunbound$captureDrops;
        this.versionunbound$captureDrops = value;
        return ret;
    }

    // Neo: Permit cancellation of Entity#tick via EntityTickEvent.Pre
    @WrapOperation(method = "rideTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;tick()V"))
    private void versionunbound$entityTick(Entity entity, Operation<Void> original) {
        if (!EventHooks.fireEntityTickPre(entity).isCanceled()) {
            original.call(entity);
            EventHooks.fireEntityTickPost(entity);
        }
    }

    @ModifyReturnValue(method = "isInvulnerableToBase", at = @At("RETURN"))
    private boolean versionunbound$invulnerabilityCheck(boolean isVanillaInvulnerable, @Local(argsOnly = true) DamageSource source) {
        return CommonHooks.isEntityInvulnerableTo((Entity) (Object) this, source, isVanillaInvulnerable);
    }

    // ServerPlayer has its own teleport (ServerPlayerMixin); the other overrides call this one.
    @Inject(method = "teleport", at = @At("HEAD"), cancellable = true)
    private void versionunbound$travelToDimension(TeleportTransition transition, CallbackInfoReturnable<Entity> cir) {
        if (!CommonHooks.onTravelToDimension((Entity) (Object) this, transition.newLevel().dimension())) cir.setReturnValue(null);
    }

    @WrapOperation(method = "spawnAtLocation(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/entity/item/ItemEntity;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean versionunbound$captureDrop(ServerLevel level, Entity entity, Operation<Boolean> original) {
        if (this.versionunbound$captureDrops != null) {
            this.versionunbound$captureDrops.add((ItemEntity) entity);
            return true;
        }
        return original.call(level, entity);
    }

    // After vanilla's checks that the entity may ride at all, before `force || canRide(...)`, so forced mounts fire it too.
    @Definition(id = "force", local = @Local(type = boolean.class, argsOnly = true, ordinal = 0))
    @Expression("force")
    @Inject(method = "startRiding(Lnet/minecraft/world/entity/Entity;ZZ)Z", at = @At("MIXINEXTRAS:EXPRESSION"), cancellable = true)
    private void versionunbound$mount(Entity entityToRide, boolean force, boolean sendEventAndTriggers, CallbackInfoReturnable<Boolean> cir) {
        if (!EventHooks.canMountEntity((Entity) (Object) this, entityToRide, true)) cir.setReturnValue(false);
    }

    @Inject(method = "removeVehicle", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/Entity;vehicle:Lnet/minecraft/world/entity/Entity;", opcode = Opcodes.PUTFIELD), cancellable = true)
    private void versionunbound$dismount(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (!EventHooks.canMountEntity(self, self.getVehicle(), false)) ci.cancel();
    }

    // Neo: ensure target chunk is loaded.
    @Inject(method = "setPosRaw", at = @At("TAIL"))
    private void versionunbound$loadTargetChunk(double x, double y, double z, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (self.isAddedToLevel() && !self.level().isClientSide() && !self.isRemoved()) {
            self.level().getChunk((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4);
        }
    }
}
