package net.salutaryfish.versionunbound.testmod.events;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.EntityInvulnerabilityCheckEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Server-side damage checks, run once the server has started: chickens hurt with 1 point of damage (4 health), each with
 * a listener that does one thing to it (nothing, cancel the incoming damage, raise it, double it in {@code Pre}, make the
 * chicken invulnerable, drop the invulnerability ticks), and an armored zombie for the armor reduction. The events are
 * recorded per entity.
 */
final class DamageScenario {
    private final Checks checks;
    private final Map<Entity, Float> incoming = Collections.synchronizedMap(new IdentityHashMap<>());
    private final Map<Entity, Float> pre = Collections.synchronizedMap(new IdentityHashMap<>());
    private final Map<Entity, LivingDamageEvent.Post> post = Collections.synchronizedMap(new IdentityHashMap<>());
    private Entity canceled;
    private Entity raised;
    private Entity doubled;
    private Entity invulnerable;
    private Entity withoutTicks;

    DamageScenario(Checks checks) {
        this.checks = checks;
    }

    void incoming(LivingIncomingDamageEvent event) {
        incoming.put(event.getEntity(), event.getOriginalAmount());
        if (event.getEntity() == canceled) event.setCanceled(true);
        if (event.getEntity() == raised) event.setAmount(3.0F);
        if (event.getEntity() == withoutTicks) event.setInvulnerabilityTicks(0);
    }

    void pre(LivingDamageEvent.Pre event) {
        pre.put(event.getEntity(), event.getNewDamage());
        if (event.getEntity() == doubled) event.setNewDamage(event.getNewDamage() * 2);
    }

    void post(LivingDamageEvent.Post event) {
        post.put(event.getEntity(), event);
    }

    void invulnerability(EntityInvulnerabilityCheckEvent event) {
        if (event.getEntity() == invulnerable) event.setInvulnerable(true);
    }

    boolean posted(Entity entity) {
        return post.containsKey(entity);
    }

    float healthDamage(Entity entity) {
        return post.containsKey(entity) ? post.get(entity).getHealthDamage() : -1;
    }

    void run(ServerLevel level, double x, double y, double z) {
        DamageSource generic = level.damageSources().generic();
        LivingEntity plain = spawn(level, EntityTypes.CHICKEN, x, y, z);
        LivingEntity canceledChicken = spawn(level, EntityTypes.CHICKEN, x, y, z);
        LivingEntity raisedChicken = spawn(level, EntityTypes.CHICKEN, x, y, z);
        LivingEntity doubledChicken = spawn(level, EntityTypes.CHICKEN, x, y, z);
        LivingEntity invulnerableChicken = spawn(level, EntityTypes.CHICKEN, x, y, z);
        LivingEntity ticklessChicken = spawn(level, EntityTypes.CHICKEN, x, y, z);
        canceled = canceledChicken;
        raised = raisedChicken;
        doubled = doubledChicken;
        invulnerable = invulnerableChicken;
        withoutTicks = ticklessChicken;

        plain.hurtServer(level, generic, 1.0F);
        checks.check("LivingIncomingDamageEvent, LivingDamageEvent.Pre and Post fire with the damage",
                incoming.get(plain) == 1.0F && pre.get(plain) == 1.0F && healthDamage(plain) == 1.0F && plain.getHealth() == plain.getMaxHealth() - 1.0F);
        checks.check("the invulnerability ticks after an attack come from the container", plain.invulnerableTime == 20 && post.get(plain).getPostAttackInvulnerabilityTicks() == 20);
        post.remove(plain);
        boolean hurtAgain = plain.hurtServer(level, generic, 1.0F);
        checks.check("an attack during the invulnerability ticks that isn't stronger does nothing", !hurtAgain && !posted(plain) && plain.getHealth() == plain.getMaxHealth() - 1.0F);

        boolean hurtCanceled = canceledChicken.hurtServer(level, generic, 1.0F);
        checks.check("canceling LivingIncomingDamageEvent stops the damage", !hurtCanceled && incoming.containsKey(canceledChicken)
                && !pre.containsKey(canceledChicken) && canceledChicken.getHealth() == canceledChicken.getMaxHealth());

        raisedChicken.hurtServer(level, generic, 1.0F);
        checks.check("LivingIncomingDamageEvent's amount is the damage vanilla deals", pre.get(raisedChicken) == 3.0F
                && healthDamage(raisedChicken) == 3.0F && raisedChicken.getHealth() == raisedChicken.getMaxHealth() - 3.0F);

        doubledChicken.hurtServer(level, generic, 1.0F);
        checks.check("LivingDamageEvent.Pre's new damage is what the health loses", healthDamage(doubledChicken) == 2.0F
                && doubledChicken.getHealth() == doubledChicken.getMaxHealth() - 2.0F);

        boolean hurtInvulnerable = invulnerableChicken.hurtServer(level, generic, 1.0F);
        checks.check("EntityInvulnerabilityCheckEvent can make an entity invulnerable", !hurtInvulnerable && !incoming.containsKey(invulnerableChicken)
                && invulnerableChicken.getHealth() == invulnerableChicken.getMaxHealth());

        ticklessChicken.hurtServer(level, generic, 1.0F);
        ticklessChicken.hurtServer(level, generic, 1.0F);
        checks.check("LivingIncomingDamageEvent can drop the invulnerability ticks", ticklessChicken.invulnerableTime == 0
                && ticklessChicken.getHealth() == ticklessChicken.getMaxHealth() - 2.0F);

        // Cactus damage doesn't bypass armor; a diamond chestplate takes some of it.
        LivingEntity zombie = spawn(level, EntityTypes.ZOMBIE, x, y, z);
        zombie.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
        zombie.hurtServer(level, level.damageSources().cactus(), 10.0F);
        LivingDamageEvent.Post zombiePost = post.get(zombie);
        checks.check("armor reduces the damage, and the container records it",
                zombiePost != null && zombiePost.getReduction(DamageContainer.Reduction.ARMOR) > 0.0F && zombiePost.getHealthDamage() < 10.0F
                        && Math.abs(zombiePost.getHealthDamage() + zombiePost.getReduction(DamageContainer.Reduction.ARMOR) - 10.0F) < 1.0E-4F);

        for (Entity entity : List.of(plain, canceledChicken, raisedChicken, doubledChicken, invulnerableChicken, ticklessChicken, zombie)) {
            entity.discard();
        }
    }

    private static LivingEntity spawn(ServerLevel level, EntityType<? extends LivingEntity> type, double x, double y, double z) {
        LivingEntity entity = type.create(level, EntitySpawnReason.COMMAND);
        entity.setPos(x, y, z);
        level.addFreshEntity(entity);
        return entity;
    }
}
