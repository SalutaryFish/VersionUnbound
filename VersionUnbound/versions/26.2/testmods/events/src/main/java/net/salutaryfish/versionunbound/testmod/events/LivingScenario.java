package net.salutaryfish.versionunbound.testmod.events;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;

/**
 * Server-side checks of more living events, set up once the server has started: target changes (set, canceled,
 * redirected), knockback (and canceled), an equipment change (seen on the entity's next tick), and item use (start,
 * changed duration, canceled, stop, and a use that ticks and finishes while the server runs). The end-of-run checks are
 * in {@link #finish()}.
 */
final class LivingScenario {
    private final Checks checks;
    private final Map<Entity, LivingChangeTargetEvent> targets = Collections.synchronizedMap(new IdentityHashMap<>());
    private final Map<Entity, Float> knockbacks = Collections.synchronizedMap(new IdentityHashMap<>());
    private final Map<Entity, Integer> starts = Collections.synchronizedMap(new IdentityHashMap<>());
    private final Map<Entity, Integer> useTicks = Collections.synchronizedMap(new IdentityHashMap<>());
    private final Map<Entity, Boolean> stops = Collections.synchronizedMap(new IdentityHashMap<>());
    private final Map<Entity, Boolean> finishes = Collections.synchronizedMap(new IdentityHashMap<>());
    private final List<Entity> spawned = new ArrayList<>();
    private Entity stubborn;
    private Entity redirected;
    private LivingEntity redirectTo;
    private Entity steady;
    private Entity equipped;
    private boolean helmetSeen;
    private Entity refused;
    private Entity quick;

    LivingScenario(Checks checks) {
        this.checks = checks;
    }

    void changeTarget(LivingChangeTargetEvent event) {
        targets.put(event.getEntity(), event);
        if (event.getEntity() == stubborn) event.setCanceled(true);
        if (event.getEntity() == redirected) event.setNewAboutToBeSetTarget(redirectTo);
    }

    void knockBack(LivingKnockBackEvent event) {
        knockbacks.put(event.getEntity(), event.getStrength());
        if (event.getEntity() == steady) event.setCanceled(true);
    }

    void equipmentChange(LivingEquipmentChangeEvent event) {
        if (event.getEntity() == equipped && event.getSlot() == EquipmentSlot.HEAD && event.getTo().is(Items.DIAMOND_HELMET)) helmetSeen = true;
    }

    void useItem(LivingEntityUseItemEvent event) {
        Entity entity = event.getEntity();
        switch (event) {
            case LivingEntityUseItemEvent.Start start -> {
                starts.put(entity, start.getDuration());
                if (entity == refused) start.setCanceled(true);
                if (entity == quick) start.setDuration(2);
            }
            case LivingEntityUseItemEvent.Tick tick -> useTicks.merge(entity, 1, Integer::sum);
            case LivingEntityUseItemEvent.Stop stop -> stops.put(entity, true);
            case LivingEntityUseItemEvent.Finish finish -> finishes.put(entity, true);
            default -> {}
        }
    }

    void run(ServerLevel level, double x, double y, double z) {
        LivingEntity prey = spawn(level, EntityTypes.CHICKEN, x, y, z);
        LivingEntity other = spawn(level, EntityTypes.CHICKEN, x, y, z);
        Mob hunter = (Mob) spawn(level, EntityTypes.ZOMBIE, x, y, z);
        hunter.setTarget(prey);
        LivingChangeTargetEvent hunterEvent = targets.get(hunter);
        checks.check("LivingChangeTargetEvent fires when a mob's target is set", hunterEvent != null && hunterEvent.getOriginalAboutToBeSetTarget() == prey
                && hunterEvent.getTargetType() == LivingChangeTargetEvent.LivingTargetType.MOB_TARGET && hunter.getTarget() == prey);
        Mob stubbornZombie = (Mob) spawn(level, EntityTypes.ZOMBIE, x, y, z);
        stubborn = stubbornZombie;
        stubbornZombie.setTarget(prey);
        checks.check("canceling LivingChangeTargetEvent keeps the mob's target", targets.containsKey(stubbornZombie) && stubbornZombie.getTarget() == null);
        Mob redirectedZombie = (Mob) spawn(level, EntityTypes.ZOMBIE, x, y, z);
        redirected = redirectedZombie;
        redirectTo = other;
        redirectedZombie.setTarget(prey);
        checks.check("LivingChangeTargetEvent can replace the new target", redirectedZombie.getTarget() == other);
        // A piglin's brain uses StartAttacking, so making one loads (and patches) that behavior.
        spawn(level, EntityTypes.PIGLIN, x, y, z);

        LivingEntity pushed = spawn(level, EntityTypes.CHICKEN, x, y, z);
        pushed.knockback(0.5, 1.0, 0.0, level.damageSources().generic(), 1.0F, false);
        checks.check("LivingKnockBackEvent fires and the entity is pushed", knockbacks.get(pushed) != null && knockbacks.get(pushed) == 0.5F
                && pushed.getDeltaMovement().horizontalDistanceSqr() > 0.0);
        LivingEntity steadyChicken = spawn(level, EntityTypes.CHICKEN, x, y, z);
        steady = steadyChicken;
        steadyChicken.knockback(0.5, 1.0, 0.0, level.damageSources().generic(), 1.0F, false);
        checks.check("canceling LivingKnockBackEvent stops the push", knockbacks.containsKey(steadyChicken) && steadyChicken.getDeltaMovement().horizontalDistanceSqr() == 0.0);

        // Noticed on the zombie's next tick (see finish()).
        LivingEntity helmeted = spawn(level, EntityTypes.ZOMBIE, x, y, z);
        equipped = helmeted;
        helmeted.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));

        LivingEntity user = holding(spawn(level, EntityTypes.CHICKEN, x, y, z));
        user.startUsingItem(InteractionHand.MAIN_HAND);
        checks.check("LivingEntityUseItemEvent.Start fires with the item's use duration", starts.get(user) != null && starts.get(user) == 32
                && user.isUsingItem() && user.getUseItemRemainingTicks() == 32);
        user.releaseUsingItem();
        checks.check("LivingEntityUseItemEvent.Stop fires when the item is released", stops.containsKey(user) && !user.isUsingItem());
        LivingEntity refusing = holding(spawn(level, EntityTypes.CHICKEN, x, y, z));
        refused = refusing;
        refusing.startUsingItem(InteractionHand.MAIN_HAND);
        checks.check("canceling LivingEntityUseItemEvent.Start keeps the item unused", starts.containsKey(refusing) && !refusing.isUsingItem());
        LivingEntity quickUser = holding(spawn(level, EntityTypes.CHICKEN, x, y, z));
        quick = quickUser;
        quickUser.startUsingItem(InteractionHand.MAIN_HAND);
        checks.check("LivingEntityUseItemEvent.Start can change the use duration", quickUser.isUsingItem() && quickUser.getUseItemRemainingTicks() == 2);
    }

    /** When the server stops: the zombie saw its helmet and the short use ran out, then the entities go. */
    void finish() {
        checks.check("LivingEquipmentChangeEvent fires when an entity's equipment changes", helmetSeen);
        checks.check("LivingEntityUseItemEvent.Tick and Finish fire as a use runs out", useTicks.getOrDefault(quick, 0) >= 1 && finishes.containsKey(quick));
        spawned.forEach(Entity::discard);
    }

    private LivingEntity spawn(ServerLevel level, EntityType<? extends LivingEntity> type, double x, double y, double z) {
        LivingEntity entity = type.create(level, EntitySpawnReason.COMMAND);
        entity.setPos(x, y, z);
        level.addFreshEntity(entity);
        spawned.add(entity);
        return entity;
    }

    private static LivingEntity holding(LivingEntity entity) {
        entity.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GOLDEN_APPLE));
        return entity;
    }
}
