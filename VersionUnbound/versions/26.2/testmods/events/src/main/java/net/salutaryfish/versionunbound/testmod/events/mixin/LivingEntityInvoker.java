package net.salutaryfish.versionunbound.testmod.events.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * The death drops ({@code LivingEntity.die} calls this), for the player death drop checks in {@code PlayerScenario}, and
 * the remaining use time, so {@code DestroyItemScenario} can release a bow as if it had been drawn.
 */
@Mixin(LivingEntity.class)
public interface LivingEntityInvoker {
    @Invoker("dropAllDeathLoot")
    void events$dropAllDeathLoot(ServerLevel level, DamageSource source);

    @Invoker("dropExperience")
    void events$dropExperience(ServerLevel level, @Nullable Entity killer);

    @Accessor("useItemRemaining")
    void events$setUseItemRemaining(int ticks);
}
