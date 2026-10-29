package net.salutaryfish.versionunbound.testmod.events.mixin;

import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** A ridden horse's jump (a camel's dash overrides it), for the jump checks in {@code LivingScenario}. */
@Mixin(AbstractHorse.class)
public interface AbstractHorseInvoker {
    @Invoker("executeRidersJump")
    void events$executeRidersJump(float amount, Vec3 input);
}
