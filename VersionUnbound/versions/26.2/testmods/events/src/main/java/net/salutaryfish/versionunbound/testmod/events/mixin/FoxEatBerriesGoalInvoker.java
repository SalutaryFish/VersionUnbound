package net.salutaryfish.versionunbound.testmod.events.mixin;

import net.minecraft.world.entity.animal.fox.Fox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** A fox picking berries once it reached its bush, for {@code GriefingScenario}. */
@Mixin(Fox.FoxEatBerriesGoal.class)
public interface FoxEatBerriesGoalInvoker {
    @Invoker("onReachedTarget")
    void events$onReachedTarget();
}
