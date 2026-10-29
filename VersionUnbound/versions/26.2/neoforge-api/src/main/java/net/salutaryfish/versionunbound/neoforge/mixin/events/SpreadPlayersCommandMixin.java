package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Set;
import net.minecraft.server.commands.SpreadPlayersCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Relative;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * NeoForge's patch to {@code SpreadPlayersCommand}: {@code EntityTeleportEvent.SpreadPlayersCommand} before each entity
 * {@code /spreadplayers} moves, which can change the destination or cancel the teleport. The command goes on to the
 * next entity either way.
 */
@Mixin(SpreadPlayersCommand.class)
abstract class SpreadPlayersCommandMixin {
    @WrapOperation(method = "setPlayerPositions", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FFZ)Z"))
    private static boolean versionunbound$entityTeleport(Entity entity, ServerLevel level, double x, double y, double z, Set<Relative> relatives, float yRot, float xRot,
            boolean resetCamera, Operation<Boolean> original) {
        EntityTeleportEvent.SpreadPlayersCommand event = EventHooks.onEntityTeleportSpreadPlayersCommand(entity, level, x, y, z);
        if (!event.isCanceled()) {
            return original.call(entity, level, event.getTargetX(), event.getTargetY(), event.getTargetZ(), relatives, yRot, xRot, resetCamera);
        }
        return false;
    }
}
