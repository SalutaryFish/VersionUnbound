package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.Collection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.EventHooks;
import net.salutaryfish.versionunbound.neoforge.patches.EntityDropsCapture;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * NeoForge's patch to {@code Entity}: a passenger's tick fires the entity tick events too (entities that aren't
 * passengers are ticked by their level, see {@code ServerLevelMixin} and {@code ClientLevelMixin}), drop capture
 * ({@link EntityDropsCapture}), which {@code LivingDropsEvent} uses, and {@code EntityInvulnerabilityCheckEvent}, which
 * gets vanilla's answer and decides.
 */
@Mixin(Entity.class)
abstract class EntityMixin implements EntityDropsCapture {
    // Neo: Short-lived holder of dropped item entities. Used mainly for Neo hooks and event logic.
    @Unique
    private @Nullable Collection<ItemEntity> versionunbound$captureDrops;

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

    @WrapOperation(method = "spawnAtLocation(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/entity/item/ItemEntity;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean versionunbound$captureDrop(ServerLevel level, Entity entity, Operation<Boolean> original) {
        if (this.versionunbound$captureDrops != null) {
            this.versionunbound$captureDrops.add((ItemEntity) entity);
            return true;
        }
        return original.call(level, entity);
    }
}
