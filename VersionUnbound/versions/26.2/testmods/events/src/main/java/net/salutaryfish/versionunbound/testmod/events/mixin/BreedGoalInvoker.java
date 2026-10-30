package net.salutaryfish.versionunbound.testmod.events.mixin;

import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.animal.Animal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** A breed goal's partner and its birth (foxes have their own goal), for {@code ConversionScenario}. */
@Mixin(BreedGoal.class)
public interface BreedGoalInvoker {
    @Accessor("partner")
    void events$setPartner(Animal partner);

    @Invoker("breed")
    void events$breed();
}
