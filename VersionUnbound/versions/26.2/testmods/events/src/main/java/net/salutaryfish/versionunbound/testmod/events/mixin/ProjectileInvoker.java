package net.salutaryfish.versionunbound.testmod.events.mixin;

import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** A projectile's hit, so {@code TeleportScenario} can land an ender pearl where it wants. */
@Mixin(Projectile.class)
public interface ProjectileInvoker {
    @Invoker("onHit")
    void events$onHit(HitResult hitResult);
}
