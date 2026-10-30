package net.salutaryfish.versionunbound.testmod.events.mixin;

import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A piglin's time in the overworld (its conversion timer), for {@code ConversionScenario}. */
@Mixin(AbstractPiglin.class)
public interface AbstractPiglinAccessor {
    @Accessor("timeInOverworld")
    int events$timeInOverworld();
}
