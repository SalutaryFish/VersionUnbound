package net.salutaryfish.versionunbound.testmod.events.mixin;

import net.minecraft.world.entity.animal.feline.Ocelot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Whether an ocelot trusts the player (its form of being tamed), for the taming checks in {@code TameScenario}. */
@Mixin(Ocelot.class)
public interface OcelotInvoker {
    @Invoker("isTrusting")
    boolean events$isTrusting();
}
