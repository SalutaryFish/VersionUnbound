package net.salutaryfish.versionunbound.testmod.events.mixin;

import net.minecraft.world.entity.monster.Ravager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** A ravager's roar, for {@code GriefingScenario}. */
@Mixin(Ravager.class)
public interface RavagerInvoker {
    @Invoker("roar")
    void events$roar();
}
