package net.salutaryfish.versionunbound.testmod.events.mixin;

import java.util.UUID;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Starting a zombie villager's cure, and its conversion timer, for {@code ConversionScenario}. */
@Mixin(ZombieVillager.class)
public interface ZombieVillagerInvoker {
    @Invoker("startConverting")
    void events$startConverting(@Nullable UUID player, int time);

    @Accessor("villagerConversionTime")
    int events$villagerConversionTime();
}
