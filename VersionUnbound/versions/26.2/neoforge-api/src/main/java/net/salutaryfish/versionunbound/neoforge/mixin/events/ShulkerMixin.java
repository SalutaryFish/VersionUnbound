package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.monster.Shulker;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * NeoForge's patch to {@code Shulker}: {@code EntityTeleportEvent.EnderEntity} once a shulker has found a surface to
 * teleport to. The event can move the destination; canceling it makes the attempt fail, so the shulker tries another
 * spot.
 */
@Mixin(Shulker.class)
abstract class ShulkerMixin {
    @WrapOperation(method = "teleportSomewhere", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/monster/Shulker;findAttachableSurface(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/Direction;"))
    private @Nullable Direction versionunbound$enderTeleport(Shulker self, BlockPos target, Operation<Direction> original,
            @Local(ordinal = 1) LocalRef<BlockPos> targetRef) {
        Direction attachmentDirection = original.call(self, target);
        if (attachmentDirection != null) {
            EntityTeleportEvent.EnderEntity event = EventHooks.onEnderTeleport(self, target.getX(), target.getY(), target.getZ());
            if (event.isCanceled()) attachmentDirection = null;
            targetRef.set(BlockPos.containing(event.getTargetX(), event.getTargetY(), event.getTargetZ()));
        }
        return attachmentDirection;
    }
}
