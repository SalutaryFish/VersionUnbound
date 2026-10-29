package net.salutaryfish.versionunbound.testmod.events;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.goal.RunAroundLikeCrazyGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.feline.Ocelot;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.AnimalTameEvent;
import net.salutaryfish.versionunbound.testmod.events.mixin.EntityAccessor;
import net.salutaryfish.versionunbound.testmod.events.mixin.OcelotInvoker;

/**
 * {@code AnimalTameEvent}, with the player as the tamer: a wolf fed a bone, a cat fed cod, an ocelot fed cod (it comes to
 * trust the player instead), a parrot fed seeds, and a horse the player rides ({@code RunAroundLikeCrazyGoal}'s temper
 * roll, the goal ticked by hand). Each animal's random is seeded so its taming roll is known: a roll that succeeds fires
 * the event and tames the animal, a canceled event fails the taming, and a roll that fails fires no event. The horses are
 * added to the level, since the player rides them, and go again in the same tick; the other animals aren't. The player's
 * main hand and position are put back afterwards.
 */
final class TameScenario {
    // RunAroundLikeCrazyGoal rolls nextInt(adjustedTickDelay(50)) first; the goal doesn't update every tick, so that's 25.
    private static final int HORSE_TICK_BOUND = 25;

    private final Checks playerChecks;
    private final Map<Entity, List<AnimalTameEvent>> tames = Collections.synchronizedMap(new IdentityHashMap<>());
    private final Set<Entity> canceled = Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));
    private final Set<Entity> tracked = Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));

    TameScenario(Checks playerChecks) {
        this.playerChecks = playerChecks;
    }

    void tame(AnimalTameEvent event) {
        if (!tracked.contains(event.getAnimal())) return;
        tames.computeIfAbsent(event.getAnimal(), key -> new ArrayList<>()).add(event);
        if (canceled.contains(event.getAnimal())) event.setCanceled(true);
    }

    void runWithPlayer(ServerPlayer player, ServerLevel level, BlockPos base) {
        ItemStack held = player.getMainHandItem();
        Vec3 home = player.position();
        try {
            fed(player, level, "a wolf fed a bone", EntityTypes.WOLF, Items.BONE, 3, wolf -> {}, Wolf::isTame);
            fed(player, level, "a cat fed cod", EntityTypes.CAT, Items.COD, 3, cat -> {}, Cat::isTame);
            // An ocelot only takes food while its tempt goal runs (the player holding cod starts it).
            fed(player, level, "an ocelot fed cod", EntityTypes.OCELOT, Items.COD, 3, TameScenario::tempt, ocelot -> ((OcelotInvoker) ocelot).events$isTrusting());
            // A silent parrot draws nothing from its random for the eating sound, so the roll is the first draw.
            fed(player, level, "a parrot fed seeds", EntityTypes.PARROT, Items.WHEAT_SEEDS, 10, parrot -> parrot.setSilent(true), Parrot::isTame);
            horses(player, level, base.offset(3, 0, -3));
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, held);
            if (player.isPassenger()) player.stopRiding();
            player.teleport(new TeleportTransition(level, home, Vec3.ZERO, 0, 0, TeleportTransition.DO_NOTHING));
        }
    }

    /** Three animals of a kind fed by the player next to it: the roll succeeds, succeeds with the event canceled, and fails. */
    private <T extends Animal> void fed(ServerPlayer player, ServerLevel level, String name, EntityType<T> type, Item food, int bound,
            Consumer<T> prepare, Predicate<T> tamed) {
        T lucky = feed(player, level, type, food, bound, true, prepare);
        playerChecks.check("AnimalTameEvent fires for " + name + " when the taming roll succeeds, with the animal and the player, and it's tamed",
                isTame(tames.get(lucky), lucky, player) && tamed.test(lucky));
        T refused = animal(level, type, player.blockPosition());
        canceled.add(refused);
        feed(player, refused, food, bound, true, prepare);
        T unlucky = feed(player, level, type, food, bound, false, prepare);
        playerChecks.check("canceling AnimalTameEvent fails the taming of " + name + ", and a failed roll fires no event",
                isTame(tames.get(refused), refused, player) && !tamed.test(refused) && !tames.containsKey(unlucky) && !tamed.test(unlucky));
    }

    private <T extends Animal> T feed(ServerPlayer player, ServerLevel level, EntityType<T> type, Item food, int bound, boolean succeeds, Consumer<T> prepare) {
        T animal = animal(level, type, player.blockPosition());
        feed(player, animal, food, bound, succeeds, prepare);
        return animal;
    }

    private static <T extends Animal> void feed(ServerPlayer player, T animal, Item food, int bound, boolean succeeds, Consumer<T> prepare) {
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(food));
        prepare.accept(animal);
        roll(animal, bound, succeeds);
        animal.mobInteract(player, InteractionHand.MAIN_HAND);
    }

    private static void tempt(Ocelot ocelot) {
        for (WrappedGoal wrapped : ocelot.getGoalSelector().getAvailableGoals()) {
            if (wrapped.getGoal() instanceof TemptGoal tempt && tempt.canUse()) tempt.start();
        }
    }

    /**
     * Horses the player rides, at full temper (the temper roll always succeeds), with the event canceled, and with no temper
     * (it always fails); the first roll, whether the horse acts up this tick, is seeded to succeed. A horse that isn't tamed
     * throws the player off.
     */
    private void horses(ServerPlayer player, ServerLevel level, BlockPos pos) {
        List<Horse> horses = new ArrayList<>();
        try {
            Horse lucky = ride(player, level, pos, 100, horses);
            playerChecks.check("AnimalTameEvent fires for a horse when the temper roll succeeds, with the horse and its rider, and it's tamed",
                    isTame(tames.get(lucky), lucky, player) && lucky.isTamed() && player.getVehicle() == lucky);
            player.stopRiding();
            Horse refused = horse(level, pos, 100, horses);
            canceled.add(refused);
            ride(player, refused);
            Horse unlucky = ride(player, level, pos, 0, horses);
            playerChecks.check("canceling AnimalTameEvent fails the taming of a horse, which throws its rider off, and a failed roll fires no event",
                    isTame(tames.get(refused), refused, player) && !refused.isTamed() && !refused.isVehicle()
                            && !tames.containsKey(unlucky) && !unlucky.isTamed() && !unlucky.isVehicle() && unlucky.getTemper() == 5);
        } finally {
            horses.forEach(Entity::discard);
        }
    }

    private Horse ride(ServerPlayer player, ServerLevel level, BlockPos pos, int temper, List<Horse> horses) {
        Horse horse = horse(level, pos, temper, horses);
        ride(player, horse);
        return horse;
    }

    private static void ride(ServerPlayer player, Horse horse) {
        player.startRiding(horse, true, false);
        roll(horse, HORSE_TICK_BOUND, true);
        new RunAroundLikeCrazyGoal(horse, 1.2).tick();
    }

    private Horse horse(ServerLevel level, BlockPos pos, int temper, List<Horse> horses) {
        Horse horse = animal(level, EntityTypes.HORSE, pos);
        horse.setTemper(temper);
        level.addFreshEntity(horse);
        horses.add(horse);
        return horse;
    }

    private <T extends Animal> T animal(ServerLevel level, EntityType<T> type, BlockPos pos) {
        T animal = type.create(level, EntitySpawnReason.COMMAND);
        animal.setPos(Vec3.atBottomCenterOf(pos));
        tracked.add(animal);
        return animal;
    }

    private static boolean isTame(List<AnimalTameEvent> events, Animal animal, Player tamer) {
        return events != null && events.size() == 1 && events.get(0).getAnimal() == animal && events.get(0).getEntity() == animal
                && events.get(0).getTamer() == tamer;
    }

    /** Seeds the entity's random so that its next {@code nextInt(bound)} is 0 (the taming roll succeeds), or isn't. */
    private static void roll(Entity entity, int bound, boolean zero) {
        RandomSource random = ((EntityAccessor) entity).events$getRandom();
        for (long seed = 0; ; seed++) {
            random.setSeed(seed);
            if ((random.nextInt(bound) == 0) == zero) {
                random.setSeed(seed);
                return;
            }
        }
    }
}
