package net.salutaryfish.versionunbound.neoforge.patches;

import java.util.Stack;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.damagesource.DamageContainer;

/**
 * The stack of {@link DamageContainer}s NeoForge's patch adds to {@link LivingEntity} (its protected
 * {@code damageContainers} field, which {@code LivingEntityMixin} adds under that name): one container per
 * {@code hurtServer} call in progress. Our code reaches it through this interface, which the mixin implements.
 */
public interface LivingEntityDamageContainers {
    Stack<DamageContainer> versionunbound$damageContainers();

    /** LivingEntity's {@code dead} flag (set once {@code die} has run), which NeoForge's {@code actuallyHurt} checks. */
    boolean versionunbound$isDead();

    /**
     * LivingEntity's protected {@code getDamageAfterArmorAbsorb} and {@code getDamageAfterMagicAbsorb}, for the player's
     * {@code actuallyHurt} (a mixin can't shadow methods its target only inherits).
     */
    float versionunbound$getDamageAfterArmorAbsorb(DamageSource source, float damage);

    float versionunbound$getDamageAfterMagicAbsorb(DamageSource source, float damage);

    /**
     * The container of the damage being dealt. NeoForge's {@code actuallyHurt} assumes {@code hurtServer} pushed one;
     * when something calls {@code actuallyHurt} directly, a fresh container stands in instead of NeoForge's
     * {@code EmptyStackException}.
     */
    default DamageContainer versionunbound$currentDamageContainer(DamageSource source, float damage) {
        Stack<DamageContainer> containers = this.versionunbound$damageContainers();
        return containers.isEmpty() ? new DamageContainer(source, damage) : containers.peek();
    }
}
