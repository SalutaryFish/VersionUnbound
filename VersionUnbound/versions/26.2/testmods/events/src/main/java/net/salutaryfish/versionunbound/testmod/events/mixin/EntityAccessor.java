package net.salutaryfish.versionunbound.testmod.events.mixin;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * An entity's own random, which {@code TeleportScenario} and {@code TameScenario} seed so that a shulker's random teleport
 * and a taming roll are known.
 */
@Mixin(Entity.class)
public interface EntityAccessor {
    @Accessor("random")
    RandomSource events$getRandom();
}
