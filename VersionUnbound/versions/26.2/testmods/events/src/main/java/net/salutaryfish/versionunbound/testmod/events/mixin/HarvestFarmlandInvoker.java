package net.salutaryfish.versionunbound.testmod.events.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.HarvestFarmland;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Whether a villager may start harvesting, for {@code GriefingScenario}. */
@Mixin(HarvestFarmland.class)
public interface HarvestFarmlandInvoker {
    @Invoker("checkExtraStartConditions")
    boolean events$checkExtraStartConditions(ServerLevel level, Villager body);
}
