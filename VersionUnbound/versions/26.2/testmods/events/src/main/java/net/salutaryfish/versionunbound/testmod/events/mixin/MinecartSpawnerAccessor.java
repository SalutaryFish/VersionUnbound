package net.salutaryfish.versionunbound.testmod.events.mixin;

import net.minecraft.world.entity.vehicle.minecart.MinecartSpawner;
import net.minecraft.world.level.BaseSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A spawner minecart's spawner, for {@code SpawnScenario}. */
@Mixin(MinecartSpawner.class)
public interface MinecartSpawnerAccessor {
    @Accessor("spawner")
    BaseSpawner events$getSpawner();
}
