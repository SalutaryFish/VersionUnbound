package net.salutaryfish.versionunbound.testmod.events.mixin;

import net.minecraft.world.entity.monster.Shulker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** A shulker's random teleport, for {@code TeleportScenario}. */
@Mixin(Shulker.class)
public interface ShulkerInvoker {
    @Invoker("teleportSomewhere")
    boolean events$teleportSomewhere();
}
