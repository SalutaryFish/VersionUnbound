package net.salutaryfish.versionunbound.testmod.events.mixin;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** An entity's own random, which {@code TeleportScenario} seeds so that a shulker's random teleport is known. */
@Mixin(Entity.class)
public interface EntityAccessor {
    @Accessor("random")
    RandomSource events$getRandom();
}
