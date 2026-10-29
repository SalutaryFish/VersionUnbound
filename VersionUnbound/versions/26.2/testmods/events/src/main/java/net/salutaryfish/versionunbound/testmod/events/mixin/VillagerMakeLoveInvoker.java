package net.salutaryfish.versionunbound.testmod.events.mixin;

import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.VillagerMakeLove;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Two villagers breeding, for {@code SpawnScenario}. */
@Mixin(VillagerMakeLove.class)
public interface VillagerMakeLoveInvoker {
    @Invoker("breed")
    Optional<Villager> events$breed(ServerLevel level, Villager source, Villager target);
}
