package net.salutaryfish.versionunbound.testmod.events.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * The death drops ({@code LivingEntity.die} calls this), for the player death drop checks in {@code PlayerScenario}.
 */
@Mixin(LivingEntity.class)
public interface LivingEntityInvoker {
    @Invoker("dropAllDeathLoot")
    void events$dropAllDeathLoot(ServerLevel level, DamageSource source);
}
