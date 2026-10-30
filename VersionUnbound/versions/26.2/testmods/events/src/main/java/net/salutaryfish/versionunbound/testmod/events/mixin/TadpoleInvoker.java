package net.salutaryfish.versionunbound.testmod.events.mixin;

import net.minecraft.world.entity.animal.frog.Tadpole;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** A tadpole growing into a frog, at once, for {@code ConversionScenario}. */
@Mixin(Tadpole.class)
public interface TadpoleInvoker {
    @Invoker("ageUp")
    void events$ageUp();
}
