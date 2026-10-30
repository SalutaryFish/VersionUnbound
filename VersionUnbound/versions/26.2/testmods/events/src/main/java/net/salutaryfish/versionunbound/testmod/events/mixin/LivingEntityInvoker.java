package net.salutaryfish.versionunbound.testmod.events.mixin;

import java.util.List;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.syncher.EntityDataAccessor;
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
 * the remaining use time, so {@code DestroyItemScenario} can release a bow as if it had been drawn. For
 * {@code EffectScenario}: one tick of the effects, and the synced effect particles. For {@code GriefingScenario}: the
 * wither rose a wither's victim leaves.
 */
@Mixin(LivingEntity.class)
public interface LivingEntityInvoker {
    @Invoker("dropAllDeathLoot")
    void events$dropAllDeathLoot(ServerLevel level, DamageSource source);

    @Invoker("dropExperience")
    void events$dropExperience(ServerLevel level, @Nullable Entity killer);

    @Accessor("useItemRemaining")
    void events$setUseItemRemaining(int ticks);

    @Invoker("tickEffects")
    void events$tickEffects();

    @Invoker("createWitherRose")
    void events$createWitherRose(@Nullable LivingEntity killer);

    @Accessor("DATA_EFFECT_PARTICLES")
    static EntityDataAccessor<List<ParticleOptions>> events$dataEffectParticles() {
        throw new AssertionError();
    }
}
