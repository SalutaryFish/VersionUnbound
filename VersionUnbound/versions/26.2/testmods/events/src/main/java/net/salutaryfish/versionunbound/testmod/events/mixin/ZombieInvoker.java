package net.salutaryfish.versionunbound.testmod.events.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * A zombie's (or husk's) drowning conversion, run at once, and its conversion timer, for {@code ConversionScenario}.
 */
@Mixin(Zombie.class)
public interface ZombieInvoker {
    @Invoker("doUnderWaterConversion")
    void events$doUnderWaterConversion(ServerLevel level);

    @Accessor("conversionTime")
    int events$conversionTime();
}
