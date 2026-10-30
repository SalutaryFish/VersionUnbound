package net.salutaryfish.versionunbound.testmod.events.mixin;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * An entity's own random, which {@code TeleportScenario} and {@code TameScenario} seed so that a shulker's random teleport
 * and a taming roll are known, and which {@code PlacementScenario} replaces so that an enderman puts its block down at a
 * known place.
 */
@Mixin(Entity.class)
public interface EntityAccessor {
    @Accessor("random")
    RandomSource events$getRandom();

    @Mutable
    @Accessor("random")
    void events$setRandom(RandomSource random);
}
