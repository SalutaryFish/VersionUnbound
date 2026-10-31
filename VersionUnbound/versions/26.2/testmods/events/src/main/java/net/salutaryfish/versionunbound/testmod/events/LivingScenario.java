package net.salutaryfish.versionunbound.testmod.events;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceKey;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.salutaryfish.versionunbound.testmod.events.mixin.AbstractHorseInvoker;

/**
 * Server-side checks of more living events, set up once the server has started: target changes (set, canceled,
 * redirected), knockback (and canceled), an equipment change (seen on the entity's next tick), item use (start,
 * changed duration, canceled, stop, and a use that ticks and finishes while the server runs), teleports of a mob (within
 * the overworld, to the nether, and canceled), healing (changed and canceled), fall damage (as is, with a doubled
 * multiplier, and canceled), and the five kinds of jump. The end-of-run checks are in {@link #finish()}.
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
    private final Map<Entity, ResourceKey<Level>> travels = Collections.synchronizedMap(new IdentityHashMap<>());
    private Entity stayingHome;
    private final Map<Entity, Float> heals = Collections.synchronizedMap(new IdentityHashMap<>());
    private Entity boostedHeal;
    private Entity refusedHeal;
    private final Map<Entity, Double> falls = Collections.synchronizedMap(new IdentityHashMap<>());
    private Entity hardFall;
    private Entity canceledFall;
    private final Map<Entity, Integer> jumps = Collections.synchronizedMap(new IdentityHashMap<>());

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
        if (event.getEntity() == equipped && event.getSlot() == EquipmentSlot.HEAD && event.getTo().is(Items.DIAMOND_HELMET)) {
            helmetSeen = true;
            // Seen: the zombie can go, long before it lands (see the end of run()).
            event.getEntity().discard();
        }
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

    void travelToDimension(EntityTravelToDimensionEvent event) {
        travels.put(event.getEntity(), event.getDimension());
        if (event.getEntity() == stayingHome) event.setCanceled(true);
    }

    void heal(LivingHealEvent event) {
        heals.put(event.getEntity(), event.getAmount());
        if (event.getEntity() == boostedHeal) event.setAmount(event.getAmount() * 2);
        if (event.getEntity() == refusedHeal) event.setCanceled(true);
    }

    void fall(LivingFallEvent event) {
        falls.put(event.getEntity(), event.getDistance());
        if (event.getEntity() == hardFall) event.setDamageMultiplier(event.getDamageMultiplier() * 2);
        if (event.getEntity() == canceledFall) event.setCanceled(true);
    }

    void jump(LivingEvent.LivingJumpEvent event) {
        jumps.merge(event.getEntity(), 1, Integer::sum);
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

        teleports(level, x, y, z);
        healing(level, x, y, z);
        falling(level, x, y, z);

        LivingEntity zombie = spawn(level, EntityTypes.ZOMBIE, x, y, z);
        zombie.jumpFromGround();
        LivingEntity slime = spawn(level, EntityTypes.SLIME, x, y, z);
        slime.jumpFromGround();
        LivingEntity magmaCube = spawn(level, EntityTypes.MAGMA_CUBE, x, y, z);
        magmaCube.jumpFromGround();
        LivingEntity horse = spawn(level, EntityTypes.HORSE, x, y, z);
        ((AbstractHorseInvoker) horse).events$executeRidersJump(1.0F, Vec3.ZERO);
        LivingEntity camel = spawn(level, EntityTypes.CAMEL, x, y, z);
        ((AbstractHorseInvoker) camel).events$executeRidersJump(1.0F, Vec3.ZERO);
        checks.check("LivingJumpEvent fires once for a mob's, a slime's, a magma cube's, a horse's and a camel's jump",
                List.of(zombie, slime, magmaCube, horse, camel).stream().map(entity -> jumps.getOrDefault(entity, 0)).toList(), List.of(1, 1, 1, 1, 1));

        // The mobs are in the air near the top of the world: left there, they would fall onto the land around spawn, and
        // those that die of it (the zombies, the horse) would leave their drops in the dev world, which is kept between
        // runs (PlayerEventScenario's drops check once counted such an item). Only two stay: the helmeted zombie, until
        // its equipment change is seen on its next tick (it goes then), and the quick user, a chicken (no fall damage),
        // whose use runs out over two ticks; finish() removes what's left.
        spawned.removeIf(entity -> {
            if (entity == equipped || entity == quick) return false;
            entity.discard();
            return true;
        });
    }

    // Every teleport fires the event (players have their own, in PlayerScenario); a mob that changes level arrives as a
    // new entity, which is discarded again, so nothing is left in the nether.
    private void teleports(ServerLevel level, double x, double y, double z) {
        ServerLevel nether = level.getServer().getLevel(Level.NETHER);
        LivingEntity walker = spawn(level, EntityTypes.CHICKEN, x, y, z);
        walker.teleport(new TeleportTransition(level, new Vec3(x, y + 1, z), Vec3.ZERO, 0, 0, TeleportTransition.DO_NOTHING));
        checks.check("EntityTravelToDimensionEvent fires for a mob's teleport within a level", travels.get(walker), Level.OVERWORLD);
        LivingEntity traveler = spawn(level, EntityTypes.CHICKEN, x, y, z);
        Entity arrived = traveler.teleport(new TeleportTransition(nether, new Vec3(0.5, 64, 0.5), Vec3.ZERO, 0, 0, TeleportTransition.DO_NOTHING));
        checks.check("EntityTravelToDimensionEvent fires for a mob going to the nether, which then arrives there",
                travels.get(traveler) == Level.NETHER && arrived != null && arrived.level() == nether && traveler.isRemoved());
        if (arrived != null) arrived.discard();
        LivingEntity homebody = spawn(level, EntityTypes.CHICKEN, x, y, z);
        stayingHome = homebody;
        Entity notArrived = homebody.teleport(new TeleportTransition(nether, new Vec3(0.5, 64, 0.5), Vec3.ZERO, 0, 0, TeleportTransition.DO_NOTHING));
        checks.check("canceling EntityTravelToDimensionEvent keeps the mob where it is",
                travels.get(homebody) == Level.NETHER && notArrived == null && !homebody.isRemoved() && homebody.level() == level);
    }

    private void healing(ServerLevel level, double x, double y, double z) {
        LivingEntity patient = wounded(spawn(level, EntityTypes.CHICKEN, x, y, z));
        patient.heal(1.0F);
        checks.check("LivingHealEvent fires with the amount, and the entity heals", heals.get(patient) != null && heals.get(patient) == 1.0F && patient.getHealth() == 2.0F);
        LivingEntity boosted = wounded(spawn(level, EntityTypes.CHICKEN, x, y, z));
        boostedHeal = boosted;
        boosted.heal(1.0F);
        checks.check("LivingHealEvent can change the amount healed", boosted.getHealth(), 3.0F);
        LivingEntity refusing = wounded(spawn(level, EntityTypes.CHICKEN, x, y, z));
        refusedHeal = refusing;
        refusing.heal(1.0F);
        checks.check("canceling LivingHealEvent stops the healing", heals.containsKey(refusing) && refusing.getHealth() == 1.0F);
    }

    // Zombies, since chickens take no fall damage. The damage goes through the damage pipeline (DamageScenario only
    // changes its own chickens).
    private void falling(ServerLevel level, double x, double y, double z) {
        LivingEntity faller = spawn(level, EntityTypes.ZOMBIE, x, y, z);
        boolean hurt = faller.causeFallDamage(10.0, 1.0F, level.damageSources().fall());
        float damage = faller.getMaxHealth() - faller.getHealth();
        checks.check("LivingFallEvent fires with the fall distance, and the fall hurts", falls.get(faller) != null && falls.get(faller) == 10.0 && hurt && damage > 0.0F);
        LivingEntity hard = spawn(level, EntityTypes.ZOMBIE, x, y, z);
        hardFall = hard;
        hard.causeFallDamage(10.0, 1.0F, level.damageSources().fall());
        checks.check("LivingFallEvent's damage multiplier changes the fall damage", hard.getMaxHealth() - hard.getHealth(), 2 * damage);
        LivingEntity soft = spawn(level, EntityTypes.ZOMBIE, x, y, z);
        canceledFall = soft;
        boolean softHurt = soft.causeFallDamage(10.0, 1.0F, level.damageSources().fall());
        checks.check("canceling LivingFallEvent stops the fall damage", falls.containsKey(soft) && !softHurt && soft.getHealth() == soft.getMaxHealth());
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

    private static LivingEntity wounded(LivingEntity entity) {
        entity.setHealth(1.0F);
        return entity;
    }

    private static LivingEntity holding(LivingEntity entity) {
        entity.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GOLDEN_APPLE));
        return entity;
    }
}
