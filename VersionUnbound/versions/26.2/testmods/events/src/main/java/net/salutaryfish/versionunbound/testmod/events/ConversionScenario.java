package net.salutaryfish.versionunbound.testmod.events;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.cow.MushroomCow;
import net.minecraft.world.entity.animal.fox.Fox;
import net.minecraft.world.entity.animal.frog.Tadpole;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingConversionEvent;
import net.salutaryfish.versionunbound.testmod.events.mixin.AbstractPiglinAccessor;
import net.salutaryfish.versionunbound.testmod.events.mixin.BreedGoalInvoker;
import net.salutaryfish.versionunbound.testmod.events.mixin.HoglinAccessor;
import net.salutaryfish.versionunbound.testmod.events.mixin.MobInvoker;
import net.salutaryfish.versionunbound.testmod.events.mixin.SkeletonInvoker;
import net.salutaryfish.versionunbound.testmod.events.mixin.TadpoleInvoker;
import net.salutaryfish.versionunbound.testmod.events.mixin.ZombieInvoker;
import net.salutaryfish.versionunbound.testmod.events.mixin.ZombieVillagerInvoker;
import org.jspecify.annotations.Nullable;

/**
 * Conversions and births, on the server. {@code LivingConversionEvent.Pre} and {@code Post} for each conversion
 * NeoForge's patches cover: a zombie and a husk drowning, a skeleton freezing, a zombie villager cured, a villager killed
 * by a zombie, a hoglin and a piglin in the overworld, a pig and a villager struck by lightning, a mooshroom sheared and
 * a tadpole growing up. Each is also canceled once (the mob stays; where it has a conversion timer,
 * {@code setConversionTimer} sets it), and where the outcome shows it, the checks see where in the conversion
 * {@code Post} fires. {@code BabyEntitySpawnEvent} for two cows breeding (the child as is, replaced, and canceled) and
 * for two foxes, which have their own breed goal. The old mobs and parents aren't added to the level; whatever joins it
 * near them while the scenario runs (the outcomes, children, experience orbs and the mooshroom's mushrooms) goes again
 * at the end.
 * Runs on hard difficulty (zombies only convert villagers on normal and hard, and always on hard), which is put back
 * afterwards. The listeners only look at the scenario's own mobs.
 */
final class ConversionScenario {
    // How far from the scenario's spot a joining entity counts as one of its own.
    private static final double NEAR = 16.0;

    private final Checks checks;
    private final Set<Entity> tracked = Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));
    // Each mob's conversion events, in order, as the records below.
    private final Map<Entity, List<Object>> events = Collections.synchronizedMap(new IdentityHashMap<>());
    // Mobs whose LivingConversionEvent.Pre is canceled, with the timer the listener sets.
    private final Map<Entity, Integer> canceled = Collections.synchronizedMap(new IdentityHashMap<>());
    // What Post records about the outcome, per mob, to show where in the conversion it fires.
    private final Map<Entity, Function<LivingEntity, Object>> observers = Collections.synchronizedMap(new IdentityHashMap<>());
    // BabyEntitySpawnEvent per first parent, as it came; children put in place of the proposed one; canceled births.
    private final Map<Entity, List<Birth>> births = Collections.synchronizedMap(new IdentityHashMap<>());
    private final Map<Entity, AgeableMob> replacements = Collections.synchronizedMap(new IdentityHashMap<>());
    private final Set<Entity> canceledBirths = Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));
    private final List<Entity> joined = new CopyOnWriteArrayList<>();
    private volatile boolean active;
    private ServerLevel level;
    private Vec3 position;

    ConversionScenario(Checks checks) {
        this.checks = checks;
    }

    void pre(LivingConversionEvent.Pre event) {
        if (!tracked.contains(event.getEntity())) return;
        record(event.getEntity(), new Pre(event.getOutcome()));
        Integer timer = canceled.get(event.getEntity());
        if (timer != null) {
            event.setConversionTimer(timer);
            event.setCanceled(true);
        }
    }

    void post(LivingConversionEvent.Post event) {
        if (!tracked.contains(event.getEntity())) return;
        Function<LivingEntity, Object> observer = observers.get(event.getEntity());
        record(event.getEntity(), new Post(event.getOutcome(), observer == null ? null : observer.apply(event.getOutcome())));
    }

    void babySpawn(BabyEntitySpawnEvent event) {
        if (!tracked.contains(event.getParentA())) return;
        births.computeIfAbsent(event.getParentA(), key -> new ArrayList<>())
                .add(new Birth(event.getParentA(), event.getParentB(), event.getChild(), event.getCausedByPlayer()));
        AgeableMob replacement = replacements.get(event.getParentA());
        if (replacement != null) event.setChild(replacement);
        if (canceledBirths.contains(event.getParentA())) event.setCanceled(true);
    }

    // Only near the scenario's spot: on a brand-new world the chunks around the forced one are still being generated at
    // server started, a mob's tick here can finish them (a zombie's turtle egg goal reads blocks 24 out, and waits for
    // chunks that are on their way), and their worldgen entities join the level far below.
    void joined(EntityJoinLevelEvent event) {
        if (active && event.getEntity().position().closerThan(position, NEAR)) joined.add(event.getEntity());
    }

    /** Runs at {@code position}, in a loaded chunk. */
    void run(ServerLevel level, Vec3 position) {
        this.level = level;
        this.position = position;
        MinecraftServer server = level.getServer();
        Difficulty difficulty = server.getWorldData().getDifficulty();
        server.setDifficulty(Difficulty.HARD, true);
        active = true;
        try {
            drowning();
            curing();
            overworld();
            lightning();
            shearingAndGrowing();
            breeding();
        } finally {
            active = false;
            server.setDifficulty(difficulty, true);
            joined.forEach(Entity::discard);
        }
    }

    private void drowning() {
        checks.check("LivingConversionEvent.Pre and Post fire when a zombie drowns into a drowned", () -> {
            Zombie zombie = mob(EntityTypes.ZOMBIE);
            return converts(zombie, EntityTypes.DROWNED, null, () -> ((ZombieInvoker) zombie).events$doUnderWaterConversion(level)) != null;
        });
        checks.check("canceling LivingConversionEvent.Pre keeps a drowning zombie, and setConversionTimer sets its timer", () -> {
            Zombie zombie = mob(EntityTypes.ZOMBIE);
            return refused(zombie, EntityTypes.DROWNED, 40, () -> ((ZombieInvoker) zombie).events$doUnderWaterConversion(level))
                    && ((ZombieInvoker) zombie).events$conversionTime() == 40;
        });
        checks.check("LivingConversionEvent fires when a husk drowns into a zombie (canceled: it stays, with the timer set)", () -> {
            Husk husk = mob(EntityTypes.HUSK);
            Husk kept = mob(EntityTypes.HUSK);
            return converts(husk, EntityTypes.ZOMBIE, null, () -> ((ZombieInvoker) husk).events$doUnderWaterConversion(level)) != null
                    && refused(kept, EntityTypes.ZOMBIE, 40, () -> ((ZombieInvoker) kept).events$doUnderWaterConversion(level))
                    && ((ZombieInvoker) kept).events$conversionTime() == 40;
        });
        checks.check("LivingConversionEvent fires when a frozen skeleton turns into a stray (canceled: it stays, with the timer set)", () -> {
            Skeleton skeleton = mob(EntityTypes.SKELETON);
            Skeleton kept = mob(EntityTypes.SKELETON);
            return converts(skeleton, EntityTypes.STRAY, null, () -> ((SkeletonInvoker) skeleton).events$doFreezeConversion()) != null
                    && refused(kept, EntityTypes.STRAY, 40, () -> ((SkeletonInvoker) kept).events$doFreezeConversion())
                    && ((SkeletonInvoker) kept).events$conversionTime() == 40;
        });
    }

    private void curing() {
        // A cure started with a time of 1 runs out on the next tick. Post comes after the villager's nausea.
        checks.check("LivingConversionEvent fires when a zombie villager is cured, Post at the end (canceled: it stays, with the timer set)", () -> {
            ZombieVillager zombie = mob(EntityTypes.ZOMBIE_VILLAGER);
            ZombieVillager kept = mob(EntityTypes.ZOMBIE_VILLAGER);
            observers.put(zombie, villager -> villager.hasEffect(MobEffects.NAUSEA));
            ((ZombieVillagerInvoker) zombie).events$startConverting(null, 1);
            ((ZombieVillagerInvoker) kept).events$startConverting(null, 1);
            return converts(zombie, EntityTypes.VILLAGER, true, zombie::tick) != null
                    && refused(kept, EntityTypes.VILLAGER, 100, kept::tick)
                    && ((ZombieVillagerInvoker) kept).events$villagerConversionTime() == 100;
        });
        // Post comes after the villager's experience is copied.
        checks.check("LivingConversionEvent fires when a zombie kills a villager, for the villager (canceled: the villager stays dead)", () -> {
            Zombie killer = mob(EntityTypes.ZOMBIE);
            Villager villager = mob(EntityTypes.VILLAGER);
            Villager kept = mob(EntityTypes.VILLAGER);
            villager.setVillagerXp(7);
            observers.put(villager, zombie -> ((ZombieVillager) zombie).getVillagerXp());
            AtomicBoolean perished = new AtomicBoolean(true);
            AtomicBoolean keptPerished = new AtomicBoolean();
            return converts(villager, EntityTypes.ZOMBIE_VILLAGER, 7, () -> perished.set(killer.killedEntity(level, villager, level.damageSources().mobAttack(killer)))) != null
                    && !perished.get()
                    && refused(kept, EntityTypes.ZOMBIE_VILLAGER, 0, () -> keptPerished.set(killer.killedEntity(level, kept, level.damageSources().mobAttack(killer))))
                    && keptPerished.get();
        });
    }

    private void overworld() {
        // Their time in the overworld goes past 300 in the next AI step. Post comes after the outcome's nausea.
        checks.check("LivingConversionEvent fires when a hoglin turns into a zoglin in the overworld (canceled: it stays, with the timer set)", () -> {
            Hoglin hoglin = mob(EntityTypes.HOGLIN);
            Hoglin kept = mob(EntityTypes.HOGLIN);
            hoglin.setTimeInOverworld(300);
            kept.setTimeInOverworld(300);
            observers.put(hoglin, zoglin -> zoglin.hasEffect(MobEffects.NAUSEA));
            return converts(hoglin, EntityTypes.ZOGLIN, true, () -> ((MobInvoker) hoglin).events$customServerAiStep(level)) != null
                    && refused(kept, EntityTypes.ZOGLIN, 7, () -> ((MobInvoker) kept).events$customServerAiStep(level))
                    && ((HoglinAccessor) kept).events$timeInOverworld() == 7;
        });
        checks.check("LivingConversionEvent fires when a piglin turns into a zombified piglin in the overworld (canceled: it stays, with the timer set)", () -> {
            Piglin piglin = mob(EntityTypes.PIGLIN);
            Piglin kept = mob(EntityTypes.PIGLIN);
            piglin.setTimeInOverworld(300);
            kept.setTimeInOverworld(300);
            observers.put(piglin, zombified -> zombified.hasEffect(MobEffects.NAUSEA));
            return converts(piglin, EntityTypes.ZOMBIFIED_PIGLIN, true, () -> ((MobInvoker) piglin).events$customServerAiStep(level)) != null
                    && refused(kept, EntityTypes.ZOMBIFIED_PIGLIN, 7, () -> ((MobInvoker) kept).events$customServerAiStep(level))
                    && ((AbstractPiglinAccessor) kept).events$timeInOverworld() == 7;
        });
    }

    private void lightning() {
        LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.COMMAND);
        // Post comes at the end, after the zombified piglin is made persistent.
        checks.check("LivingConversionEvent fires when lightning strikes a pig, Post at the end (canceled: the pig stays, and burns)", () -> {
            Pig pig = mob(EntityTypes.PIG);
            Pig kept = mob(EntityTypes.PIG);
            observers.put(pig, zombified -> ((Mob) zombified).isPersistenceRequired());
            return converts(pig, EntityTypes.ZOMBIFIED_PIGLIN, true, () -> pig.thunderHit(level, bolt)) != null
                    && refused(kept, EntityTypes.ZOMBIFIED_PIGLIN, 0, () -> kept.thunderHit(level, bolt)) && kept.getRemainingFireTicks() > 0;
        });
        // Post comes before the witch is made persistent.
        checks.check("LivingConversionEvent fires when lightning strikes a villager, Post before the witch is persistent (canceled: the villager stays, and burns)", () -> {
            Villager villager = mob(EntityTypes.VILLAGER);
            Villager kept = mob(EntityTypes.VILLAGER);
            observers.put(villager, witch -> ((Mob) witch).isPersistenceRequired());
            LivingEntity witch = converts(villager, EntityTypes.WITCH, false, () -> villager.thunderHit(level, bolt));
            return witch != null && ((Mob) witch).isPersistenceRequired()
                    && refused(kept, EntityTypes.WITCH, 0, () -> kept.thunderHit(level, bolt)) && kept.getRemainingFireTicks() > 0;
        });
    }

    private void shearingAndGrowing() {
        // Post comes first in the conversion, before the mushrooms drop.
        checks.check("LivingConversionEvent fires when a mooshroom is sheared, Post before the mushrooms drop (canceled: it stays, and drops none)", () -> {
            MushroomCow mooshroom = mob(EntityTypes.MOOSHROOM);
            MushroomCow kept = mob(EntityTypes.MOOSHROOM);
            observers.put(mooshroom, cow -> joined.stream().filter(ItemEntity.class::isInstance).count());
            return converts(mooshroom, EntityTypes.COW, 0L, () -> mooshroom.shear(level, SoundSource.PLAYERS, new ItemStack(Items.SHEARS))) != null
                    && joined.stream().anyMatch(ItemEntity.class::isInstance)
                    && refused(kept, EntityTypes.COW, 0, () -> kept.shear(level, SoundSource.PLAYERS, new ItemStack(Items.SHEARS)));
        });
        // Post comes first, before the frog's spawn is finalized and it's made persistent.
        checks.check("LivingConversionEvent fires when a tadpole grows into a frog, Post first (canceled: it stays)", () -> {
            Tadpole tadpole = mob(EntityTypes.TADPOLE);
            Tadpole kept = mob(EntityTypes.TADPOLE);
            observers.put(tadpole, frog -> ((Mob) frog).isPersistenceRequired());
            LivingEntity frog = converts(tadpole, EntityTypes.FROG, false, () -> ((TadpoleInvoker) tadpole).events$ageUp());
            return frog != null && ((Mob) frog).isPersistenceRequired()
                    && refused(kept, EntityTypes.FROG, 0, () -> ((TadpoleInvoker) kept).events$ageUp());
        });
    }

    private void breeding() {
        checks.check("BabyEntitySpawnEvent fires when two animals breed, with both parents and the child, which joins the level", () -> {
            Cow cow = mob(EntityTypes.COW);
            Cow partner = mob(EntityTypes.COW);
            cow.spawnChildFromBreeding(level, partner);
            List<Birth> seen = births.getOrDefault(cow, List.of());
            return seen.size() == 1 && seen.getFirst().parentA() == cow && seen.getFirst().parentB() == partner && seen.getFirst().cause() == null
                    && seen.getFirst().child() instanceof Cow child && joined.contains(child) && child.isBaby()
                    && cow.getAge() == 6000 && partner.getAge() == 6000;
        });
        checks.check("BabyEntitySpawnEvent can replace the child", () -> {
            Cow cow = mob(EntityTypes.COW);
            Cow partner = mob(EntityTypes.COW);
            Cow replacement = EntityTypes.COW.create(level, EntitySpawnReason.BREEDING);
            replacements.put(cow, replacement);
            cow.spawnChildFromBreeding(level, partner);
            List<Birth> seen = births.getOrDefault(cow, List.of());
            return seen.size() == 1 && seen.getFirst().child() != null && !joined.contains(seen.getFirst().child())
                    && joined.contains(replacement) && replacement.isBaby();
        });
        checks.check("canceling BabyEntitySpawnEvent spawns no child, and the parents are reset as after a birth", () -> {
            Cow cow = mob(EntityTypes.COW);
            Cow partner = mob(EntityTypes.COW);
            cow.setInLove(null);
            partner.setInLove(null);
            canceledBirths.add(cow);
            int before = joined.size();
            cow.spawnChildFromBreeding(level, partner);
            return births.getOrDefault(cow, List.of()).size() == 1 && joined.size() == before
                    && cow.getAge() == 6000 && partner.getAge() == 6000 && !cow.isInLove() && !partner.isInLove();
        });
        checks.check("BabyEntitySpawnEvent fires when two foxes breed, through their own breed goal (canceled: no cub)", () -> {
            Fox fox = mob(EntityTypes.FOX);
            Fox partner = mob(EntityTypes.FOX);
            breed(fox, partner);
            List<Birth> seen = births.getOrDefault(fox, List.of());
            boolean born = seen.size() == 1 && seen.getFirst().parentB() == partner && seen.getFirst().child() instanceof Fox cub
                    && joined.contains(cub) && cub.isBaby();
            Fox kept = mob(EntityTypes.FOX);
            Fox keptPartner = mob(EntityTypes.FOX);
            canceledBirths.add(kept);
            int before = joined.size();
            breed(kept, keptPartner);
            return born && births.getOrDefault(kept, List.of()).size() == 1 && joined.size() == before
                    && kept.getAge() == 6000 && keptPartner.getAge() == 6000;
        });
    }

    private static void breed(Fox fox, Fox partner) {
        for (WrappedGoal wrapped : fox.getGoalSelector().getAvailableGoals()) {
            if (wrapped.getGoal() instanceof BreedGoal goal) {
                ((BreedGoalInvoker) goal).events$setPartner(partner);
                ((BreedGoalInvoker) goal).events$breed();
                return;
            }
        }
        throw new IllegalStateException("a fox without a breed goal");
    }

    /**
     * Runs a conversion and returns the outcome, if it went as it should: {@code Pre} with the outcome's type, then
     * {@code Post} with an outcome of that type that joined the level and what the mob's observer saw of it then, and the
     * old mob gone. Otherwise null.
     */
    private @Nullable LivingEntity converts(LivingEntity mob, EntityType<?> type, @Nullable Object seen, Runnable conversion) {
        conversion.run();
        List<Object> list = take(mob);
        if (list.size() == 2 && list.get(0).equals(new Pre(type)) && list.get(1) instanceof Post post && post.outcome().getType() == type
                && joined.contains(post.outcome()) && Objects.equals(post.seen(), seen) && mob.isRemoved()) {
            return post.outcome();
        }
        return null;
    }

    /** Runs a conversion the listener cancels (setting the timer): only {@code Pre}, the mob stays, and nothing joins the level. */
    private boolean refused(LivingEntity mob, EntityType<?> type, int timer, Runnable conversion) {
        canceled.put(mob, timer);
        int before = joined.size();
        conversion.run();
        return take(mob).equals(List.of(new Pre(type))) && !mob.isRemoved() && joined.size() == before;
    }

    private <T extends LivingEntity> T mob(EntityType<T> type) {
        T mob = type.create(level, EntitySpawnReason.COMMAND);
        mob.setPos(position);
        tracked.add(mob);
        return mob;
    }

    private void record(Entity entity, Object event) {
        events.computeIfAbsent(entity, key -> Collections.synchronizedList(new ArrayList<>())).add(event);
    }

    private List<Object> take(Entity entity) {
        List<Object> list = events.remove(entity);
        return list == null ? List.of() : List.copyOf(list);
    }

    private record Pre(EntityType<?> outcome) {}

    private record Post(LivingEntity outcome, @Nullable Object seen) {}

    private record Birth(Mob parentA, Mob parentB, @Nullable AgeableMob child, @Nullable Player cause) {}
}
