package net.salutaryfish.versionunbound.testmod.events.mixin;

import net.minecraft.world.entity.monster.hoglin.Hoglin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A hoglin's time in the overworld (its conversion timer), for {@code ConversionScenario}. */
@Mixin(Hoglin.class)
public interface HoglinAccessor {
    @Accessor("timeInOverworld")
    int events$timeInOverworld();
}
