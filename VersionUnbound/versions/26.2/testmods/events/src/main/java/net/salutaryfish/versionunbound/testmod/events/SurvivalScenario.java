package net.salutaryfish.versionunbound.testmod.events;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.living.LivingBreatheEvent;
import net.neoforged.neoforge.event.entity.living.LivingDrownEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.salutaryfish.versionunbound.testmod.events.mixin.EnderDragonInvoker;
import net.salutaryfish.versionunbound.testmod.events.mixin.ExperienceOrbAccessor;
import net.salutaryfish.versionunbound.testmod.events.mixin.LivingEntityInvoker;

/**
 * Experience, air and riding, on the server, with entities that aren't added to the level: {@code LivingExperienceDropEvent}
 * for a zombie remembered as hurt by a player (one who isn't online, so the event has none; with the player in
 * {@link #runWithPlayer}), changed and canceled, and for an ender dragon's two awards as it dies; {@code LivingBreatheEvent}
 * and {@code LivingDrownEvent} for mobs ticked by hand in a water block and out of it; {@code EntityMountEvent} for a
 * zombie getting on and off a chicken, canceled both ways. The water block and the experience orbs go again in the same
 * tick. The listeners only look at the scenario's own mobs: every living entity breathes every tick.
 */
final class SurvivalScenario {
    private final Checks checks;
    private final Checks playerChecks;
    private final Map<Entity, List<Experience>> experience = Collections.synchronizedMap(new IdentityHashMap<>());
    private final Map<Entity, Integer> droppedExperience = Collections.synchronizedMap(new IdentityHashMap<>());
    private final Map<Entity, Breath> breaths = Collections.synchronizedMap(new IdentityHashMap<>());
    private final Map<Entity, Consumer<LivingBreatheEvent>> breathChanges = Collections.synchronizedMap(new IdentityHashMap<>());
    private final Map<Entity, Drowning> drownings = Collections.synchronizedMap(new IdentityHashMap<>());
    private final Map<Entity, List<EntityMountEvent>> mounts = Collections.synchronizedMap(new IdentityHashMap<>());
    private final Set<Entity> canceled = Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));
    private final Set<Entity> tracked = Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));

    SurvivalScenario(Checks checks, Checks playerChecks) {
        this.checks = checks;
        this.playerChecks = playerChecks;
    }

    void experience(LivingExperienceDropEvent event) {
        if (!tracked.contains(event.getEntity())) return;
        experience.computeIfAbsent(event.getEntity(), key -> new ArrayList<>()).add(new Experience(event.getAttackingPlayer(), event.getOriginalExperience()));
        Integer dropped = droppedExperience.get(event.getEntity());
        if (dropped != null) event.setDroppedExperience(dropped);
        if (canceled.contains(event.getEntity())) event.setCanceled(true);
    }

    void breathe(LivingBreatheEvent event) {
        if (!tracked.contains(event.getEntity())) return;
        breaths.put(event.getEntity(), new Breath(event.canBreathe(), event.getConsumeAirAmount(), event.getRefillAirAmount()));
        Consumer<LivingBreatheEvent> change = breathChanges.get(event.getEntity());
        if (change != null) change.accept(event);
    }

    void drown(LivingDrownEvent event) {
        if (!tracked.contains(event.getEntity())) return;
        drownings.put(event.getEntity(), new Drowning(event.isDrowning(), event.getDamageAmount(), event.getBubbleCount()));
        if (canceled.contains(event.getEntity())) event.setCanceled(true);
    }

    void mount(EntityMountEvent event) {
        if (!tracked.contains(event.getEntityMounting())) return;
        mounts.computeIfAbsent(event.getEntityMounting(), key -> new ArrayList<>()).add(event);
        if (canceled.contains(event.getEntityMounting())) event.setCanceled(true);
    }

    /** Runs around {@code center}, in the air in a loaded chunk. */
    void run(ServerLevel level, BlockPos center) {
        experience(level, center);
        air(level, center.offset(4, 0, 0));
        riding(level, center.offset(0, 0, 4));
    }

    private void experience(ServerLevel level, BlockPos center) {
        Zombie plain = rememberedZombie(level, center);
        int plainOrbs = dropExperience(level, plain);
        checks.check("LivingExperienceDropEvent fires when a mob drops experience, with its reward and no player who isn't there",
                experience.get(plain), List.of(new Experience(null, 5)));
        checks.check("a mob drops the experience LivingExperienceDropEvent leaves", plainOrbs, 5);
        Zombie generous = rememberedZombie(level, center);
        droppedExperience.put(generous, 12);
        checks.check("LivingExperienceDropEvent changes the experience a mob drops", dropExperience(level, generous), 12);
        Zombie stingy = rememberedZombie(level, center);
        canceled.add(stingy);
        int stingyOrbs = dropExperience(level, stingy);
        checks.check("canceling LivingExperienceDropEvent drops no experience", experience.containsKey(stingy) && stingyOrbs == 0);

        // Its death time just before the end: the tick makes both awards, 8% and 20% of 500 (no dragon fight here).
        EnderDragon dragon = mob(level, EntityTypes.ENDER_DRAGON, center);
        ((EnderDragonInvoker) dragon).events$setDragonDeathTime(199);
        ((EnderDragonInvoker) dragon).events$tickDeath();
        int dragonOrbs = collectOrbs(level, dragon.position());
        checks.check("LivingExperienceDropEvent fires for both of the dying ender dragon's awards",
                experience.get(dragon), List.of(new Experience(null, 40), new Experience(null, 100)));
        checks.check("the ender dragon drops the experience LivingExperienceDropEvent leaves", dragonOrbs, 140);
    }

    /** The player gets credit for a zombie they hurt. */
    void runWithPlayer(ServerPlayer player, ServerLevel level, BlockPos base) {
        Zombie zombie = mob(level, EntityTypes.ZOMBIE, base.offset(-3, 0, -3));
        zombie.setLastHurtByPlayer(player, 100);
        dropExperience(level, zombie);
        playerChecks.check("LivingExperienceDropEvent names the player who last hurt the mob", experience.get(zombie), List.of(new Experience(player, 5)));
    }

    /** In a column of two water blocks, deep enough for a zombie's eyes; out of the water a block above it. */
    private void air(ServerLevel level, BlockPos water) {
        level.setBlock(water, Blocks.WATER.defaultBlockState(), 3);
        level.setBlock(water.above(), Blocks.WATER.defaultBlockState(), 3);
        try {
            // Below the maximum, so there is air to get back (the event's refill amount is what would fit).
            Chicken underwater = breathe(level, water, EntityTypes.CHICKEN, 100);
            checks.check("LivingBreatheEvent: a chicken under water can't breathe, and loses the air it says",
                    breaths.get(underwater), new Breath(false, 1, 4));
            checks.check("a chicken under water loses one air", underwater.getAirSupply(), 99);
            Chicken outside = breathe(level, water.above(3), EntityTypes.CHICKEN, 100);
            checks.check("LivingBreatheEvent: a chicken out of the water breathes, and gets back the air it says",
                    breaths.get(outside).equals(new Breath(true, 1, 4)) && outside.getAirSupply() == 104);
            Zombie zombie = breathe(level, water, EntityTypes.ZOMBIE, 100);
            checks.check("LivingBreatheEvent: an undead under water breathes, without getting air back", breaths.get(zombie), new Breath(true, 1, 0));
            Chicken helped = breathe(level, water, EntityTypes.CHICKEN, 100, event -> {
                event.setCanBreathe(true);
                event.setRefillAirAmount(10);
            });
            checks.check("LivingBreatheEvent lets a chicken breathe under water, with the air it says", helped.getAirSupply(), 110);

            Chicken drowning = breathe(level, water, EntityTypes.CHICKEN, -19);
            checks.check("LivingDrownEvent fires once the air is used up, drowning at -20 with vanilla's damage and bubbles",
                    drownings.get(drowning), new Drowning(true, 2.0F, 8));
            checks.check("a drowning chicken takes the damage, and its air starts again at 0",
                    drowning.getHealth() == drowning.getMaxHealth() - 2.0F && drowning.getAirSupply() == 0);
            Chicken spared = mob(level, EntityTypes.CHICKEN, water);
            spared.setAirSupply(-19);
            canceled.add(spared);
            spared.baseTick();
            checks.check("canceling LivingDrownEvent spares the chicken", drownings.containsKey(spared)
                    && spared.getHealth() == spared.getMaxHealth() && spared.getAirSupply() == -20);
            Chicken holding = breathe(level, water, EntityTypes.CHICKEN, 0);
            Chicken fresh = breathe(level, water, EntityTypes.CHICKEN, 5);
            checks.check("LivingDrownEvent fires at no air left, not drowning before -20, and not with air left",
                    drownings.get(holding).equals(new Drowning(false, 2.0F, 8)) && holding.getAirSupply() == -1
                            && holding.getHealth() == holding.getMaxHealth() && !drownings.containsKey(fresh));
        } finally {
            level.removeBlock(water.above(), false);
            level.removeBlock(water, false);
        }
    }

    private void riding(ServerLevel level, BlockPos center) {
        Zombie rider = mob(level, EntityTypes.ZOMBIE, center);
        Chicken mount = mob(level, EntityTypes.CHICKEN, center);
        checks.check("EntityMountEvent fires when an entity gets on another",
                rider.startRiding(mount) && rider.getVehicle() == mount && isMount(mounts.get(rider), mount, true));
        canceled.add(rider);
        rider.stopRiding();
        checks.check("canceling EntityMountEvent keeps an entity from getting off",
                rider.getVehicle() == mount && mounts.get(rider).size() == 2 && isMount(mounts.get(rider).subList(1, 2), mount, false));
        canceled.remove(rider);
        rider.stopRiding();
        checks.check("EntityMountEvent fires when an entity gets off", !rider.isPassenger() && isMount(mounts.get(rider).subList(2, 3), mount, false));

        Zombie refused = mob(level, EntityTypes.ZOMBIE, center);
        Zombie forced = mob(level, EntityTypes.ZOMBIE, center);
        canceled.add(refused);
        canceled.add(forced);
        checks.check("canceling EntityMountEvent keeps an entity from getting on, even when forced",
                !refused.startRiding(mob(level, EntityTypes.CHICKEN, center)) && !refused.isPassenger()
                        && !forced.startRiding(mob(level, EntityTypes.CHICKEN, center), true, false) && !forced.isPassenger()
                        && mounts.get(refused).size() == 1 && mounts.get(forced).size() == 1);
    }

    private static boolean isMount(List<EntityMountEvent> events, Entity mount, boolean mounting) {
        return events != null && events.size() == 1 && events.get(0).getEntityBeingMounted() == mount && events.get(0).isMounting() == mounting
                && events.get(0).isDismounting() != mounting;
    }

    private Zombie rememberedZombie(ServerLevel level, BlockPos pos) {
        Zombie zombie = mob(level, EntityTypes.ZOMBIE, pos);
        zombie.setLastHurtByPlayer(UUID.randomUUID(), 100);
        return zombie;
    }

    /** Has the mob drop its experience, and returns how much landed in orbs (which go again). */
    private static int dropExperience(ServerLevel level, LivingEntity entity) {
        ((LivingEntityInvoker) entity).events$dropExperience(level, null);
        return collectOrbs(level, entity.position());
    }

    private static int collectOrbs(ServerLevel level, Vec3 pos) {
        int total = 0;
        for (ExperienceOrb orb : level.getEntitiesOfClass(ExperienceOrb.class, AABB.ofSize(pos, 2, 2, 2))) {
            total += orb.getValue() * ((ExperienceOrbAccessor) orb).events$count();
            orb.discard();
        }
        return total;
    }

    private <T extends LivingEntity> T breathe(ServerLevel level, BlockPos pos, EntityType<T> type, int air) {
        return breathe(level, pos, type, air, null);
    }

    /** One base tick for a new mob at {@code pos} with the given air; {@code change} edits its LivingBreatheEvent. */
    private <T extends LivingEntity> T breathe(ServerLevel level, BlockPos pos, EntityType<T> type, int air, Consumer<LivingBreatheEvent> change) {
        T entity = mob(level, type, pos);
        entity.setAirSupply(air);
        if (change != null) breathChanges.put(entity, change);
        entity.baseTick();
        return entity;
    }

    private <T extends Entity> T mob(ServerLevel level, EntityType<T> type, BlockPos pos) {
        T entity = type.create(level, EntitySpawnReason.COMMAND);
        entity.setPos(Vec3.atBottomCenterOf(pos));
        tracked.add(entity);
        return entity;
    }

    private record Experience(Player attackingPlayer, int original) {}

    private record Breath(boolean canBreathe, int consume, int refill) {}

    private record Drowning(boolean drowning, float damage, int bubbles) {}
}
