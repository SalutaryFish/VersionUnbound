package net.salutaryfish.versionunbound.testmod.events;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Endermite;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.salutaryfish.versionunbound.testmod.events.mixin.EntityAccessor;
import net.salutaryfish.versionunbound.testmod.events.mixin.ProjectileInvoker;
import net.salutaryfish.versionunbound.testmod.events.mixin.ShulkerInvoker;

/**
 * {@code EntityTeleportEvent} at each of NeoForge's sites. On the server: {@code /tp} (to the destination, redirected,
 * canceled) and {@code /spreadplayers} (onto a platform, canceled), both run as the entity ({@code @s}); chorus fruit
 * eaten by a chicken (redirected onto a block, canceled), an enderman's random teleport (to a block, canceled) and a
 * shulker's (seeded, so its tries are known: redirected, canceled). With a player ({@link #runWithPlayer}): ender
 * pearls landing (to the pearl, redirected with less damage, canceled). Blocks placed for these go again at once, and so
 * do the entities.
 */
final class TeleportScenario {
    private final Checks checks;
    private final Checks playerChecks;
    private final Map<Entity, List<EntityTeleportEvent>> teleports = Collections.synchronizedMap(new IdentityHashMap<>());
    // As the listener first saw them, before redirecting: the event keeps the new target, and the consumed stack is
    // used up after the teleport.
    private final Map<Entity, Vec3> firstTargets = Collections.synchronizedMap(new IdentityHashMap<>());
    private final Map<Entity, Item> consumedItems = Collections.synchronizedMap(new IdentityHashMap<>());
    private final Map<Entity, Vec3> redirects = Collections.synchronizedMap(new IdentityHashMap<>());
    private final Map<Entity, Boolean> cancels = Collections.synchronizedMap(new IdentityHashMap<>());
    private float pearlDamage = -1;
    private final List<Entity> spawned = new ArrayList<>();
    private final List<BlockPos> placed = new ArrayList<>();

    TeleportScenario(Checks checks, Checks playerChecks) {
        this.checks = checks;
        this.playerChecks = playerChecks;
    }

    void teleport(EntityTeleportEvent event) {
        Entity entity = event.getEntity();
        teleports.computeIfAbsent(entity, key -> new ArrayList<>()).add(event);
        firstTargets.putIfAbsent(entity, event.getTarget());
        if (event instanceof EntityTeleportEvent.ItemConsumption consumption) consumedItems.putIfAbsent(entity, consumption.getConsumedItem().getItem());
        if (cancels.containsKey(entity)) event.setCanceled(true);
        Vec3 redirect = redirects.get(entity);
        if (redirect != null) {
            event.setTargetX(redirect.x);
            event.setTargetY(redirect.y);
            event.setTargetZ(redirect.z);
        }
        if (event instanceof EntityTeleportEvent.EnderPearl pearl && pearlDamage >= 0) pearl.setAttackDamage(pearlDamage);
    }

    /** Runs around {@code center}, in the air at the middle of a loaded chunk. */
    void run(ServerLevel level, BlockPos center) {
        try {
            commands(level, center);
            chorusFruit(level, center);
            enderman(level, center);
            shulker(level, center);
        } finally {
            placed.forEach(pos -> level.removeBlock(pos, false));
            placed.clear();
            spawned.forEach(Entity::discard);
            spawned.clear();
        }
    }

    private void commands(ServerLevel level, BlockPos center) {
        Vec3 home = Vec3.atBottomCenterOf(center);
        Vec3 there = home.add(2.0, 1.0, 0.0);
        LivingEntity moved = spawn(level, EntityTypes.CHICKEN, home);
        command(level, moved, "tp " + coordinates(there));
        checks.check("EntityTeleportEvent.TeleportCommand fires for /tp, and the entity goes to the destination",
                first(moved) instanceof EntityTeleportEvent.TeleportCommand event && event.getTarget().equals(there) && moved.position().equals(there));
        LivingEntity redirected = spawn(level, EntityTypes.CHICKEN, home);
        Vec3 elsewhere = home.add(-2.0, 1.0, 0.0);
        redirects.put(redirected, elsewhere);
        command(level, redirected, "tp " + coordinates(there));
        checks.check("EntityTeleportEvent.TeleportCommand can change where /tp goes", redirected.position(), elsewhere);
        LivingEntity stubborn = spawn(level, EntityTypes.CHICKEN, home);
        cancels.put(stubborn, true);
        command(level, stubborn, "tp " + coordinates(there));
        checks.check("canceling EntityTeleportEvent.TeleportCommand keeps the entity in place", first(stubborn) != null && stubborn.position().equals(home));

        // A 5x5 platform ten blocks down; spreadplayers looks for the ground below maxHeight, within a block of the middle.
        BlockPos platform = center.below(10);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                place(level, platform.offset(dx, 0, dz));
            }
        }
        String spread = String.format(Locale.ROOT, "spreadplayers %d %d 0 1 under %d false @s", center.getX(), center.getZ(), platform.getY() + 3);
        LivingEntity spreadOut = spawn(level, EntityTypes.CHICKEN, home);
        command(level, spreadOut, spread);
        checks.check("EntityTeleportEvent.SpreadPlayersCommand fires for /spreadplayers, and the entity lands on the ground",
                first(spreadOut) instanceof EntityTeleportEvent.SpreadPlayersCommand event && event.getTargetY() == platform.getY() + 1
                        && spreadOut.position().equals(event.getTarget()));
        LivingEntity stayed = spawn(level, EntityTypes.CHICKEN, home);
        cancels.put(stayed, true);
        command(level, stayed, spread);
        checks.check("canceling EntityTeleportEvent.SpreadPlayersCommand keeps the entity in place", first(stayed) != null && stayed.position().equals(home));
    }

    // Chorus fruit tries up to 16 random spots around the eater (all in this chunk); each one it can land on fires the
    // event. Redirected onto a block next to the chicken, the first one succeeds; canceled, every one fails.
    private void chorusFruit(ServerLevel level, BlockPos center) {
        Vec3 home = Vec3.atBottomCenterOf(center);
        BlockPos ground = center.offset(3, -3, 0);
        place(level, ground);
        Vec3 onGround = Vec3.atBottomCenterOf(ground.above());
        LivingEntity eater = spawn(level, EntityTypes.CHICKEN, home);
        redirects.put(eater, onGround);
        new ItemStack(Items.CHORUS_FRUIT).finishUsingItem(level, eater);
        checks.check("EntityTeleportEvent.ItemConsumption fires for chorus fruit, and can change where the entity goes",
                first(eater) instanceof EntityTeleportEvent.ItemConsumption event && consumedItems.get(eater) == Items.CHORUS_FRUIT
                        && event.getEntityLiving() == eater && eater.position().equals(onGround));
        LivingEntity refused = spawn(level, EntityTypes.CHICKEN, home);
        cancels.put(refused, true);
        new ItemStack(Items.CHORUS_FRUIT).finishUsingItem(level, refused);
        checks.check("canceling EntityTeleportEvent.ItemConsumption keeps the entity in place",
                teleports.getOrDefault(refused, List.of()).stream().allMatch(EntityTeleportEvent.ItemConsumption.class::isInstance)
                        && first(refused) != null && refused.position().equals(home));
    }

    // LivingEntity.randomTeleport, called as EnderMan.teleport does, onto a block.
    private void enderman(ServerLevel level, BlockPos center) {
        Vec3 home = Vec3.atBottomCenterOf(center);
        BlockPos ground = center.offset(-3, -3, 0);
        place(level, ground);
        Vec3 onGround = Vec3.atBottomCenterOf(ground.above());
        LivingEntity enderman = spawn(level, EntityTypes.ENDERMAN, home);
        boolean teleported = enderman.randomTeleport(onGround.x, onGround.y, onGround.z, true);
        checks.check("EntityTeleportEvent.EnderEntity fires when an enderman teleports, and it goes there",
                first(enderman) instanceof EntityTeleportEvent.EnderEntity event && event.getTarget().equals(onGround) && teleported && enderman.position().equals(onGround));
        LivingEntity stubborn = spawn(level, EntityTypes.ENDERMAN, home);
        cancels.put(stubborn, true);
        boolean stubbornTeleported = stubborn.randomTeleport(onGround.x, onGround.y, onGround.z, true);
        checks.check("canceling EntityTeleportEvent.EnderEntity keeps the enderman in place",
                first(stubborn) instanceof EntityTeleportEvent.EnderEntity && !stubbornTeleported && stubborn.position().equals(home));
    }

    // A shulker's random teleport, with its random seeded so that its tries are known: a block goes under the first
    // spot, which gives the shulker a surface there. The shulkers aren't added to the level, so they don't tick.
    private void shulker(ServerLevel level, BlockPos center) {
        BlockPos home = center.offset(0, -6, 0);
        long seed = shulkerSeed(level, home);
        BlockPos firstTry = shulkerTries(level, seed, home).get(0);
        place(level, firstTry.below());
        Shulker redirected = shulker(level, home, seed);
        BlockPos elsewhere = firstTry.above(2);
        redirects.put(redirected, Vec3.atLowerCornerOf(elsewhere));
        boolean teleported = ((ShulkerInvoker) redirected).events$teleportSomewhere();
        checks.check("EntityTeleportEvent.EnderEntity fires when a shulker teleports, and can change where it goes",
                first(redirected) instanceof EntityTeleportEvent.EnderEntity && Vec3.atLowerCornerOf(firstTry).equals(firstTargets.get(redirected))
                        && teleported && redirected.position().equals(Vec3.atBottomCenterOf(elsewhere)) && redirected.getAttachFace() == Direction.DOWN);
        Shulker stubborn = shulker(level, home, seed);
        cancels.put(stubborn, true);
        boolean stubbornTeleported = ((ShulkerInvoker) stubborn).events$teleportSomewhere();
        checks.check("canceling EntityTeleportEvent.EnderEntity keeps the shulker in place",
                first(stubborn) != null && !stubbornTeleported && stubborn.position().equals(Vec3.atBottomCenterOf(home)));
    }

    private static Shulker shulker(ServerLevel level, BlockPos pos, long seed) {
        Shulker shulker = EntityTypes.SHULKER.create(level, EntitySpawnReason.COMMAND);
        shulker.setPos(Vec3.atBottomCenterOf(pos));
        ((EntityAccessor) shulker).events$getRandom().setSeed(seed);
        return shulker;
    }

    /**
     * A seed whose five tries all stay in this chunk, with the first in the open (room for the block under it and the
     * redirect two blocks up) and the other four next to nothing solid, the block under the first included, so they fail.
     */
    private static long shulkerSeed(ServerLevel level, BlockPos home) {
        for (long seed = 0; ; seed++) {
            List<BlockPos> tries = shulkerTries(level, seed, home);
            BlockPos first = tries.get(0);
            boolean usable = tries.stream().allMatch(pos -> pos.getX() >> 4 == home.getX() >> 4 && pos.getZ() >> 4 == home.getZ() >> 4)
                    && level.isEmptyBlock(first) && level.isEmptyBlock(first.below()) && level.isEmptyBlock(first.above()) && level.isEmptyBlock(first.above(2))
                    && tries.subList(1, tries.size()).stream().allMatch(pos -> !pos.equals(first.below()) && !pos.equals(first)
                            && Direction.stream().allMatch(direction -> !pos.relative(direction).equals(first.below()) && level.isEmptyBlock(pos.relative(direction))));
            if (usable) return seed;
        }
    }

    // Shulker.teleportSomewhere draws three offsets per try, before checking the spot.
    private static List<BlockPos> shulkerTries(ServerLevel level, long seed, BlockPos home) {
        RandomSource random = ((EntityAccessor) EntityTypes.SHULKER.create(level, EntitySpawnReason.COMMAND)).events$getRandom();
        random.setSeed(seed);
        List<BlockPos> tries = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            tries.add(home.offset(Mth.randomBetweenInclusive(random, -8, 8), Mth.randomBetweenInclusive(random, -8, 8), Mth.randomBetweenInclusive(random, -8, 8)));
        }
        return tries;
    }

    /**
     * Ender pearls the player threw, landing in the air next to it (by calling the pearl's {@code onHit}): one takes the
     * player to the pearl, one is redirected and does less damage, one is canceled. In survival, so the damage counts;
     * the player's game mode, health and position are put back afterwards.
     */
    void runWithPlayer(ServerPlayer player, ServerLevel level, BlockPos base) {
        GameType gameType = player.gameMode.getGameModeForPlayer();
        Vec3 home = player.position();
        player.setGameMode(GameType.SURVIVAL);
        try {
            Vec3 landing = Vec3.atBottomCenterOf(base.offset(3, 0, -3));
            ThrownEnderpearl pearl = land(player, level, landing);
            playerChecks.check("EntityTeleportEvent.EnderPearl fires when a player's pearl lands, and the player goes to the pearl",
                    first(player) instanceof EntityTeleportEvent.EnderPearl event && event.getPearlEntity() == pearl && event.getPlayer() == player
                            && event.getAttackDamage() == 5.0F && event.getTarget().equals(landing) && player.position().equals(landing)
                            && player.getHealth() == player.getMaxHealth() - 5.0F);
            forget(player);

            Vec3 elsewhere = landing.add(-2.0, 0.0, 0.0);
            redirects.put(player, elsewhere);
            pearlDamage = 2.0F;
            land(player, level, landing);
            playerChecks.check("EntityTeleportEvent.EnderPearl can change where the player goes and the damage",
                    player.position().equals(elsewhere) && player.getHealth() == player.getMaxHealth() - 2.0F);
            redirects.remove(player);
            pearlDamage = -1;
            forget(player);

            Vec3 before = player.position();
            cancels.put(player, true);
            ThrownEnderpearl canceledPearl = land(player, level, landing);
            playerChecks.check("canceling EntityTeleportEvent.EnderPearl keeps the player in place, unhurt",
                    first(player) instanceof EntityTeleportEvent.EnderPearl && player.position().equals(before) && player.getHealth() == player.getMaxHealth()
                            && canceledPearl.isRemoved());
        } finally {
            cancels.remove(player);
            redirects.remove(player);
            pearlDamage = -1;
            // A landing pearl may spawn an endermite.
            level.getEntitiesOfClass(Endermite.class, player.getBoundingBox().inflate(16.0)).forEach(Entity::discard);
            player.setGameMode(gameType);
            player.setHealth(player.getMaxHealth());
            player.teleport(new TeleportTransition(level, home, Vec3.ZERO, 0, 0, TeleportTransition.DO_NOTHING));
        }
    }

    /** A pearl of the player's lands at {@code pos}, with the player at full health and without invulnerability ticks. */
    private static ThrownEnderpearl land(ServerPlayer player, ServerLevel level, Vec3 pos) {
        player.setHealth(player.getMaxHealth());
        player.invulnerableTime = 0;
        ThrownEnderpearl pearl = new ThrownEnderpearl(level, player, new ItemStack(Items.ENDER_PEARL));
        pearl.setPos(pos);
        ((ProjectileInvoker) pearl).events$onHit(new BlockHitResult(pos, Direction.UP, BlockPos.containing(pos).below(), false));
        return pearl;
    }

    private void forget(Entity entity) {
        teleports.remove(entity);
        firstTargets.remove(entity);
    }

    private EntityTeleportEvent first(Entity entity) {
        List<EntityTeleportEvent> events = teleports.getOrDefault(entity, List.of());
        return events.isEmpty() ? null : events.get(0);
    }

    private static void command(ServerLevel level, Entity entity, String command) {
        CommandSourceStack source = level.getServer().createCommandSourceStack().withEntity(entity).withPosition(entity.position()).withLevel(level);
        level.getServer().getCommands().performPrefixedCommand(source, command);
    }

    private static String coordinates(Vec3 pos) {
        return String.format(Locale.ROOT, "%.2f %.2f %.2f", pos.x, pos.y, pos.z);
    }

    private void place(ServerLevel level, BlockPos pos) {
        level.setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
        placed.add(pos);
    }

    private LivingEntity spawn(ServerLevel level, EntityType<? extends LivingEntity> type, Vec3 pos) {
        LivingEntity entity = type.create(level, EntitySpawnReason.COMMAND);
        entity.setPos(pos);
        level.addFreshEntity(entity);
        spawned.add(entity);
        return entity;
    }
}
