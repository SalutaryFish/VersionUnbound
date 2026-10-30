package net.salutaryfish.versionunbound.testmod.events.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * One step of a mob's own server AI (a hoglin's or piglin's counts its time in the overworld), for
 * {@code ConversionScenario}.
 */
@Mixin(Mob.class)
public interface MobInvoker {
    @Invoker("customServerAiStep")
    void events$customServerAiStep(ServerLevel level);
}
