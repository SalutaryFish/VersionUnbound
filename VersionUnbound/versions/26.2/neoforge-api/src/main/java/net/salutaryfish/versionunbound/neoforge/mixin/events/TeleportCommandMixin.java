package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.Set;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.commands.LookAt;
import net.minecraft.server.commands.TeleportCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Relative;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

/**
 * NeoForge's patch to {@code TeleportCommand}: {@code EntityTeleportEvent.TeleportCommand} before each entity
 * {@code /tp} moves, which can change the destination or cancel the teleport.
 */
@Mixin(TeleportCommand.class)
abstract class TeleportCommandMixin {
    @WrapMethod(method = "performTeleport")
    private static void versionunbound$entityTeleport(CommandSourceStack source, Entity victim, ServerLevel level, double x, double y, double z, Set<Relative> relatives,
            float yRot, float xRot, @Nullable LookAt lookAt, Operation<Void> original) {
        EntityTeleportEvent.TeleportCommand event = EventHooks.onEntityTeleportCommand(victim, level, x, y, z);
        if (event.isCanceled()) {
            return;
        }
        original.call(source, victim, level, event.getTargetX(), event.getTargetY(), event.getTargetZ(), relatives, yRot, xRot, lookAt);
    }
}
