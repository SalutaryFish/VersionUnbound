package net.salutaryfish.versionunbound.testmod.events.mixin;

import net.minecraft.world.entity.ExperienceOrb;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** How many orbs of its value an experience orb stands for (orbs merge as they're awarded). */
@Mixin(ExperienceOrb.class)
public interface ExperienceOrbAccessor {
    @Accessor("count")
    int events$count();
}
