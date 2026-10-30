package net.salutaryfish.versionunbound.testmod.events;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.HarvestFarmland;
import net.minecraft.world.entity.ai.goal.BreakDoorGoal;
import net.minecraft.world.entity.ai.goal.EatBlockGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.RemoveBlockGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.fox.Fox;
import net.minecraft.world.entity.animal.golem.SnowGolem;
import net.minecraft.world.entity.animal.rabbit.Rabbit;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.entity.monster.illager.Evoker;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.living.LivingDestroyBlockEvent;
import net.salutaryfish.versionunbound.testmod.events.mixin.EnderDragonInvoker;
import net.salutaryfish.versionunbound.testmod.events.mixin.FoxEatBerriesGoalInvoker;
import net.salutaryfish.versionunbound.testmod.events.mixin.HarvestFarmlandInvoker;
import net.salutaryfish.versionunbound.testmod.events.mixin.LivingEntityInvoker;
import net.salutaryfish.versionunbound.testmod.events.mixin.MobInvoker;
import net.salutaryfish.versionunbound.testmod.events.mixin.MoveToBlockGoalAccessor;
import net.salutaryfish.versionunbound.testmod.events.mixin.ProjectileInvoker;
import net.salutaryfish.versionunbound.testmod.events.mixin.RavagerInvoker;
import net.salutaryfish.versionunbound.testmod.events.mixin.WitherBossAccessor;

/**
 * {@code EntityMobGriefingEvent} where NeoForge asks it in place of the mob griefing game rule, and
 * {@code LivingDestroyBlockEvent} with the block extension's {@code canEntityDestroy} (which {@link MobProofBlock}
 * overrides, as mods do). On the server at server started, in the air in a loaded chunk; each site runs through vanilla's
 * own method, once for a mob the listener lets grief and once for one it doesn't (or, where the outcome depends on a
 * roll, only the second). Mobs and projectiles aren't added to the level; what joins it near the scenario's box while the
 * scenario runs (drops) is discarded, and the box is put back as it was. The listeners only record the scenario's own
 * entities.
 */
final class GriefingScenario {
    private final Checks checks;
    private final List<Entity> griefing = new CopyOnWriteArrayList<>();
    private final List<Destroy> destroys = new CopyOnWriteArrayList<>();
    private final List<Entity> joined = new CopyOnWriteArrayList<>();
    private volatile BoundingBox joinArea;
    private final Set<Entity> tracked = Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));
    private final Set<Entity> denied = Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));
    private final Set<BlockPos> canceledDestroys = Collections.synchronizedSet(new HashSet<>());
    private volatile boolean active;
    private ServerLevel level;
    private BlockPos center;

    GriefingScenario(Checks checks) {
        this.checks = checks;
    }

    void mobGriefing(EntityMobGriefingEvent event) {
        if (!tracked.contains(event.getEntity())) return;
        griefing.add(event.getEntity());
        if (denied.contains(event.getEntity())) event.setCanGrief(false);
    }

    void destroyBlock(LivingDestroyBlockEvent event) {
        if (!tracked.contains(event.getEntity())) return;
        // The wither's position is a mutable one from BlockPos.betweenClosed.
        destroys.add(new Destroy(event.getEntity(), event.getPos().immutable(), event.getState()));
        if (canceledDestroys.contains(event.getPos())) event.setCanceled(true);
    }

    // Only near the scenario's box (see ConversionScenario.joined: on a brand-new world, worldgen entities of the chunks
    // around can join while it runs).
    void joined(EntityJoinLevelEvent event) {
        BoundingBox near = joinArea;
        if (active && near != null && near.isInside(event.getEntity().blockPosition())) joined.add(event.getEntity());
    }

    /** Runs around {@code center}, the middle of a loaded chunk, in the air. */
    void run(ServerLevel level, BlockPos center) {
        this.level = level;
        this.center = center;
        BoundingBox box = BoundingBox.fromCorners(center.offset(-8, -2, -8), center.offset(7, 4, 7));
        joinArea = box.inflatedBy(8);
        Map<BlockPos, BlockState> before = new HashMap<>();
        BlockPos.betweenClosedStream(box).forEach(pos -> before.put(pos.immutable(), level.getBlockState(pos)));
        active = true;
        try {
            // Explosions first, while the blocks of the other checks aren't there yet.
            explosions(spot(0, 0));
            largeFireball(spot(1, 0));
            smallFireball(spot(2, 0));
            witherRose(spot(0, 1));
            projectiles();
            pickingUp();
            sheep(spot(1, 1));
            goals();
            breakDoor(spot(2, 1));
            fox(spot(0, 2));
            snowGolem(spot(1, 2));
            dragon(spot(2, 2));
            wither(spot(0, 3));
            witherSkull();
            ravager(spot(1, 3));
            silverfish(spot(2, 3));
            crop(spot(0, 4));
            turtleEgg(spot(1, 4));
        } finally {
            active = false;
            joined.forEach(Entity::discard);
            before.forEach((pos, state) -> {
                if (level.getBlockState(pos) != state) level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            });
            canceledDestroys.clear();
        }
    }

    /** One of 15 places, 4 blocks apart in x and 3 in z, all within the chunk. */
    private BlockPos spot(int x, int z) {
        return center.offset(-6 + 4 * x, 0, -6 + 3 * z);
    }

    private void explosions(BlockPos dirt) {
        Vec3 above = Vec3.atCenterOf(dirt.above());
        Zombie bomber = denied(mob(EntityTypes.ZOMBIE, dirt.above()));
        place(dirt, Blocks.DIRT);
        start();
        level.explode(bomber, above.x, above.y, above.z, 2.0F, Level.ExplosionInteraction.MOB);
        checks.check("a mob's explosion asks EntityMobGriefingEvent about its source, and breaks no blocks if it may not grief",
                griefing.equals(List.of(bomber)) && level.getBlockState(dirt).is(Blocks.DIRT));

        Zombie allowed = mob(EntityTypes.ZOMBIE, dirt.above());
        start();
        level.explode(allowed, above.x, above.y, above.z, 2.0F, Level.ExplosionInteraction.MOB);
        checks.check("a mob's explosion breaks blocks if its source may grief", griefing.equals(List.of(allowed)) && level.getBlockState(dirt).isAir());
    }

    // NeoForge asks about the ghast whether the explosion sets fires, and the explosion asks about the fireball whether it
    // breaks blocks (the patch's TODO notes that it fires twice).
    private void largeFireball(BlockPos dirt) {
        BlockHitResult hit = new BlockHitResult(Vec3.atBottomCenterOf(dirt.above()), Direction.UP, dirt, false);
        checks.check("a ghast's fireball asks EntityMobGriefingEvent about the ghast, then its explosion about the fireball", () -> {
            Ghast ghast = denied(mob(EntityTypes.GHAST, dirt.above(3)));
            LargeFireball fireball = fireball(new LargeFireball(level, ghast, new Vec3(0.0, -1.0, 0.0), 2), hit);
            place(dirt, Blocks.DIRT);
            start();
            ((ProjectileInvoker) fireball).events$onHit(hit);
            return griefing.equals(List.of(ghast, fireball)) && level.getBlockState(dirt).isAir() && noFire(dirt);
        });
        checks.check("a ghast's fireball breaks no blocks if the fireball may not grief", () -> {
            Ghast ghast = mob(EntityTypes.GHAST, dirt.above(3));
            LargeFireball fireball = denied(fireball(new LargeFireball(level, ghast, new Vec3(0.0, -1.0, 0.0), 2), hit));
            place(dirt, Blocks.DIRT);
            start();
            ((ProjectileInvoker) fireball).events$onHit(hit);
            return griefing.equals(List.of(ghast, fireball)) && level.getBlockState(dirt).is(Blocks.DIRT);
        });
    }

    private void smallFireball(BlockPos base) {
        checks.check("a blaze's fireball asks EntityMobGriefingEvent about the blaze, and sets no fire if it may not grief", () -> {
            Blaze blaze = denied(mob(EntityTypes.BLAZE, base.above(3)));
            return smallFireballHit(new SmallFireball(level, blaze, new Vec3(0.0, -1.0, 0.0)), base).equals(List.of(blaze)) && level.getBlockState(base.above()).isAir();
        });
        checks.check("a blaze's fireball sets fire if the blaze may grief", () -> {
            Blaze blaze = mob(EntityTypes.BLAZE, base.above(3));
            BlockPos dirt = base.east();
            return smallFireballHit(new SmallFireball(level, blaze, new Vec3(0.0, -1.0, 0.0)), dirt).equals(List.of(blaze)) && level.getBlockState(dirt.above()).is(Blocks.FIRE);
        });
        checks.check("a fireball without a mob for an owner sets fire without asking", () -> {
            BlockPos dirt = base.east(2);
            return smallFireballHit(new SmallFireball(level, dirt.getX() + 0.5, dirt.getY() + 1.0, dirt.getZ() + 0.5, new Vec3(0.0, -1.0, 0.0)), dirt).isEmpty()
                    && level.getBlockState(dirt.above()).is(Blocks.FIRE);
        });
    }

    private List<Entity> smallFireballHit(SmallFireball fireball, BlockPos dirt) {
        BlockHitResult hit = new BlockHitResult(Vec3.atBottomCenterOf(dirt.above()), Direction.UP, dirt, false);
        fireball(fireball, hit);
        place(dirt, Blocks.DIRT);
        start();
        ((ProjectileInvoker) fireball).events$onHit(hit);
        return List.copyOf(griefing);
    }

    private <T extends Projectile> T fireball(T fireball, BlockHitResult hit) {
        fireball.setPos(hit.getLocation());
        tracked.add(fireball);
        return fireball;
    }

    // A wither's victim plants a wither rose where it died, or drops one if it can't.
    private void witherRose(BlockPos pos) {
        place(pos.below(), Blocks.DIRT);
        checks.check("a wither's victim asks EntityMobGriefingEvent about the wither, and drops a wither rose instead of planting it if it may not grief", () -> {
            WitherBoss wither = denied(mob(EntityTypes.WITHER, pos.east(2)));
            Chicken victim = mob(EntityTypes.CHICKEN, pos);
            start();
            int drops = joined.size();
            ((LivingEntityInvoker) victim).events$createWitherRose(wither);
            return griefing.equals(List.of(wither)) && level.getBlockState(pos).isAir() && joined.size() == drops + 1
                    && joined.getLast() instanceof ItemEntity item && item.getItem().is(Items.WITHER_ROSE);
        });
        checks.check("a wither's victim plants a wither rose if the wither may grief", () -> {
            WitherBoss wither = mob(EntityTypes.WITHER, pos.east(2));
            Chicken victim = mob(EntityTypes.CHICKEN, pos);
            start();
            ((LivingEntityInvoker) victim).events$createWitherRose(wither);
            return griefing.equals(List.of(wither)) && level.getBlockState(pos).is(Blocks.WITHER_ROSE);
        });
    }

    private void projectiles() {
        BlockPos pos = center;
        checks.check("a projectile asks EntityMobGriefingEvent about its owner whether it may change a block", () -> {
            Zombie thrower = mob(EntityTypes.ZOMBIE, pos);
            Zombie deniedThrower = denied(mob(EntityTypes.ZOMBIE, pos));
            start();
            boolean allowed = new Snowball(level, thrower, new ItemStack(Items.SNOWBALL)).mayInteract(level, pos);
            boolean refused = !new Snowball(level, deniedThrower, new ItemStack(Items.SNOWBALL)).mayInteract(level, pos);
            return allowed && refused && griefing.equals(List.of(thrower, deniedThrower));
        });
        checks.check("a projectile without an owner may change blocks, without asking", () -> {
            Snowball snowball = new Snowball(level, pos.getX(), pos.getY(), pos.getZ(), new ItemStack(Items.SNOWBALL));
            tracked.add(snowball);
            start();
            return snowball.mayInteract(level, pos) && griefing.isEmpty();
        });
    }

    private void pickingUp() {
        ItemStack diamond = new ItemStack(Items.DIAMOND);
        checks.check("an allay asks EntityMobGriefingEvent before picking up an item it wants, and doesn't want it if it may not grief", () -> {
            Allay allay = mob(EntityTypes.ALLAY, center);
            allay.setItemInHand(InteractionHand.MAIN_HAND, diamond.copy());
            Allay deniedAllay = denied(mob(EntityTypes.ALLAY, center));
            deniedAllay.setItemInHand(InteractionHand.MAIN_HAND, diamond.copy());
            start();
            return allay.wantsToPickUp(level, diamond) && !deniedAllay.wantsToPickUp(level, diamond) && griefing.equals(List.of(allay, deniedAllay));
        });
        checks.check("an allay isn't asked about an item it doesn't want (NeoForge asks last)", () -> {
            Allay allay = mob(EntityTypes.ALLAY, center);
            allay.setItemInHand(InteractionHand.MAIN_HAND, diamond.copy());
            start();
            return !allay.wantsToPickUp(level, new ItemStack(Items.STICK)) && griefing.isEmpty();
        });
        checks.check("a piglin asks EntityMobGriefingEvent before picking up an item, and doesn't want it if it may not grief", () -> {
            Piglin piglin = denied(mob(EntityTypes.PIGLIN, center));
            start();
            return !piglin.wantsToPickUp(level, new ItemStack(Items.GOLD_INGOT)) && griefing.equals(List.of(piglin));
        });
        // A skeleton has no goals that ask, so the one question is Mob.aiStep's, before looking for items to pick up.
        checks.check("a mob that can pick up items asks EntityMobGriefingEvent each step, and one that can't isn't asked", () -> {
            Skeleton looter = mob(EntityTypes.SKELETON, center);
            looter.setCanPickUpLoot(true);
            Skeleton other = mob(EntityTypes.SKELETON, center);
            other.setCanPickUpLoot(false);
            start();
            looter.aiStep();
            other.aiStep();
            return griefing.equals(List.of(looter));
        });
    }

    // Goals and behaviors that won't start without griefing; with it, they depend on rolls and surroundings.
    private void goals() {
        checks.check("a farmer asks EntityMobGriefingEvent before harvesting, and doesn't start if it may not grief", () -> {
            Villager villager = denied(mob(EntityTypes.VILLAGER, center));
            start();
            return !((HarvestFarmlandInvoker) new HarvestFarmland()).events$checkExtraStartConditions(level, villager) && griefing.equals(List.of(villager));
        });
        checks.check("a zombie asks EntityMobGriefingEvent before going for turtle eggs, and doesn't if it may not grief", () -> {
            Zombie zombie = denied(mob(EntityTypes.ZOMBIE, center));
            Goal goal = goal(zombie, RemoveBlockGoal.class::isInstance);
            start();
            return !goal.canUse() && griefing.equals(List.of(zombie));
        });
        checks.check("a rabbit asks EntityMobGriefingEvent before raiding a garden, and doesn't if it may not grief", () -> {
            Rabbit rabbit = denied(mob(EntityTypes.RABBIT, center));
            Goal goal = goal(rabbit, named("RaidGardenGoal"));
            start();
            return !goal.canUse() && griefing.equals(List.of(rabbit));
        });
        checks.check("an enderman asks EntityMobGriefingEvent before taking or leaving a block, and doesn't if it may not grief", () -> {
            EnderMan enderman = denied(mob(EntityTypes.ENDERMAN, center));
            Goal take = goal(enderman, named("EndermanTakeBlockGoal"));
            Goal leave = goal(enderman, named("EndermanLeaveBlockGoal"));
            start();
            boolean took = take.canUse();
            enderman.setCarriedBlock(Blocks.DIRT.defaultBlockState());
            boolean left = leave.canUse();
            return !took && !left && griefing.equals(List.of(enderman, enderman));
        });
        checks.check("an evoker asks EntityMobGriefingEvent before its wololo spell, and doesn't cast it if it may not grief", () -> {
            Evoker evoker = denied(mob(EntityTypes.EVOKER, center));
            Goal goal = goal(evoker, named("EvokerWololoSpellGoal"));
            start();
            return !goal.canUse() && griefing.equals(List.of(evoker));
        });
        // Whether it then merges depends on a roll.
        checks.check("a silverfish asks EntityMobGriefingEvent before looking for a block to hide in", () -> {
            Silverfish silverfish = denied(mob(EntityTypes.SILVERFISH, center));
            Goal goal = goal(silverfish, named("SilverfishMergeWithStoneGoal"));
            start();
            goal.canUse();
            return griefing.equals(List.of(silverfish));
        });
        checks.check("a ravager's roar asks EntityMobGriefingEvent", () -> {
            Ravager ravager = denied(mob(EntityTypes.RAVAGER, center));
            start();
            ((RavagerInvoker) ravager).events$roar();
            return griefing.equals(List.of(ravager));
        });
    }

    // A sheep eats the short grass it stands in, or else turns the grass block under it into dirt; it eats either way.
    private void sheep(BlockPos pos) {
        BlockPos grass = pos.east(2);
        place(pos.below(), Blocks.GRASS_BLOCK);
        place(grass.below(), Blocks.GRASS_BLOCK);
        checks.check("a sheep asks EntityMobGriefingEvent before eating grass, and leaves it if it may not grief", () -> {
            place(pos, Blocks.SHORT_GRASS);
            Sheep shortGrass = denied(mob(EntityTypes.SHEEP, pos));
            Sheep grassBlock = denied(mob(EntityTypes.SHEEP, grass));
            start();
            eat(shortGrass);
            eat(grassBlock);
            return griefing.equals(List.of(shortGrass, grassBlock)) && level.getBlockState(pos).is(Blocks.SHORT_GRASS)
                    && level.getBlockState(grass.below()).is(Blocks.GRASS_BLOCK);
        });
        checks.check("a sheep eats short grass, and turns a grass block into dirt, if it may grief", () -> {
            Sheep shortGrass = mob(EntityTypes.SHEEP, pos);
            Sheep grassBlock = mob(EntityTypes.SHEEP, grass);
            start();
            eat(shortGrass);
            eat(grassBlock);
            return griefing.equals(List.of(shortGrass, grassBlock)) && level.getBlockState(pos).isAir() && level.getBlockState(grass.below()).is(Blocks.DIRT);
        });
    }

    /** The eating goal from its start until it has eaten (the check comes 18 ticks in). */
    private void eat(Sheep sheep) {
        Goal goal = goal(sheep, EatBlockGoal.class::isInstance);
        goal.start();
        for (int i = 0; i < 20; i++) goal.tick();
    }

    // The zombie stands next to a door on its path, bumping into something: the goal's other conditions hold, so whether
    // it starts depends on CommonHooks.canEntityDestroy (EntityMobGriefingEvent, then LivingDestroyBlockEvent).
    private void breakDoor(BlockPos door) {
        BlockState lower = Blocks.OAK_DOOR.defaultBlockState();
        place(door, lower);
        place(door.above(), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        checks.check("a zombie asks EntityMobGriefingEvent before breaking a door, and doesn't if it may not grief", () -> {
            Zombie zombie = denied(doorBreaker(door));
            start();
            return !new BreakDoorGoal(zombie, difficulty -> true).canUse() && griefing.equals(List.of(zombie)) && destroys.isEmpty();
        });
        checks.check("a zombie that may grief asks LivingDestroyBlockEvent about the door, and starts breaking it", () -> {
            Zombie zombie = doorBreaker(door);
            start();
            return new BreakDoorGoal(zombie, difficulty -> true).canUse() && griefing.equals(List.of(zombie))
                    && destroys.equals(List.of(new Destroy(zombie, door, lower)));
        });
        checks.check("canceling LivingDestroyBlockEvent keeps a zombie from breaking a door", () -> {
            Zombie zombie = doorBreaker(door);
            canceledDestroys.add(door);
            start();
            boolean started = new BreakDoorGoal(zombie, difficulty -> true).canUse();
            canceledDestroys.remove(door);
            return !started && destroys.size() == 1;
        });
    }

    private Zombie doorBreaker(BlockPos door) {
        Zombie zombie = mob(EntityTypes.ZOMBIE, door.north());
        zombie.setPos(door.getX() + 0.5, door.getY(), door.getZ() - 0.5);
        zombie.horizontalCollision = true;
        // DoorInteractGoal looks for a door one block above a node of the path.
        zombie.getNavigation().moveTo(new Path(List.of(new Node(door.getX(), door.getY() - 1, door.getZ())), door, false), 1.0);
        return zombie;
    }

    private void fox(BlockPos bush) {
        BlockState ripe = Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(SweetBerryBushBlock.AGE, 3);
        place(bush.below(), Blocks.DIRT);
        checks.check("a fox asks EntityMobGriefingEvent before picking berries, and leaves them if it may not grief", () -> {
            Fox fox = denied(mob(EntityTypes.FOX, bush.west()));
            place(bush, ripe);
            start();
            pickBerries(fox, bush);
            return griefing.equals(List.of(fox)) && level.getBlockState(bush) == ripe;
        });
        checks.check("a fox picks berries if it may grief", () -> {
            Fox fox = mob(EntityTypes.FOX, bush.west());
            place(bush, ripe);
            start();
            pickBerries(fox, bush);
            return griefing.equals(List.of(fox)) && level.getBlockState(bush).getValue(SweetBerryBushBlock.AGE) == 1;
        });
    }

    private void pickBerries(Fox fox, BlockPos bush) {
        Goal goal = goal(fox, Fox.FoxEatBerriesGoal.class::isInstance);
        ((MoveToBlockGoalAccessor) goal).events$setBlockPos(bush);
        ((FoxEatBerriesGoalInvoker) goal).events$onReachedTarget();
    }

    private void snowGolem(BlockPos pos) {
        place(pos.below(), Blocks.STONE);
        checks.check("a snow golem asks EntityMobGriefingEvent each step, and leaves no snow if it may not grief", () -> {
            SnowGolem golem = denied(mob(EntityTypes.SNOW_GOLEM, pos));
            start();
            golem.aiStep();
            return griefing.equals(List.of(golem)) && level.getBlockState(pos).isAir();
        });
        checks.check("a snow golem leaves snow where it stands if it may grief", () -> {
            SnowGolem golem = mob(EntityTypes.SNOW_GOLEM, pos);
            start();
            golem.aiStep();
            return griefing.equals(List.of(golem)) && level.getBlockState(pos).is(Blocks.SNOW);
        });
    }

    // Three blocks in the dragon's way: dirt, dirt whose LivingDestroyBlockEvent is canceled, and the mob-proof block.
    // CommonHooks.canEntityDestroy asks EntityMobGriefingEvent for each, then the block, then LivingDestroyBlockEvent.
    private void dragon(BlockPos dirt) {
        BlockPos kept = dirt.east();
        BlockPos proof = dirt.east(2);
        AABB bb = new AABB(dirt.getX(), dirt.getY(), dirt.getZ(), proof.getX() + 0.99, proof.getY() + 0.99, proof.getZ() + 0.99);
        canceledDestroys.add(kept);
        checks.check("an ender dragon asks EntityMobGriefingEvent for each block in its way, and breaks none if it may not grief", () -> {
            EnderDragon dragon = denied(mob(EntityTypes.ENDER_DRAGON, dirt.above(3)));
            placeDragonBlocks(dirt, kept, proof);
            start();
            boolean hitWall = ((EnderDragonInvoker) dragon).events$checkWalls(level, bb);
            return hitWall && griefing.equals(List.of(dragon, dragon, dragon)) && destroys.isEmpty() && level.getBlockState(dirt).is(Blocks.DIRT);
        });
        checks.check("an ender dragon breaks a block unless LivingDestroyBlockEvent is canceled or the block's canEntityDestroy refuses", () -> {
            EnderDragon dragon = mob(EntityTypes.ENDER_DRAGON, dirt.above(3));
            placeDragonBlocks(dirt, kept, proof);
            start();
            boolean hitWall = ((EnderDragonInvoker) dragon).events$checkWalls(level, bb);
            BlockState dirtState = Blocks.DIRT.defaultBlockState();
            return hitWall && griefing.size() == 3 && destroys.equals(List.of(new Destroy(dragon, dirt, dirtState), new Destroy(dragon, kept, dirtState)))
                    && level.getBlockState(dirt).isAir() && level.getBlockState(kept).is(Blocks.DIRT) && level.getBlockState(proof).is(EventsMod.MOB_PROOF_BLOCK.get());
        });
        canceledDestroys.remove(kept);
    }

    private void placeDragonBlocks(BlockPos dirt, BlockPos kept, BlockPos proof) {
        place(dirt, Blocks.DIRT);
        place(kept, Blocks.DIRT);
        place(proof, EventsMod.MOB_PROOF_BLOCK.get());
    }

    // A hurt wither breaks what's around it: 3 by 3 blocks, 4 high. NeoForge's default canEntityDestroy lets it destroy
    // air too, so LivingDestroyBlockEvent fires for every position but the mob-proof block's.
    private void wither(BlockPos pos) {
        BlockPos dirt = pos.east();
        BlockPos kept = pos.west();
        BlockPos proof = pos.south();
        canceledDestroys.add(kept);
        checks.check("a hurt wither asks EntityMobGriefingEvent before breaking blocks, and breaks none if it may not grief", () -> {
            WitherBoss wither = denied(mob(EntityTypes.WITHER, pos));
            placeWitherBlocks(dirt, kept, proof);
            start();
            witherBreaks(wither);
            return griefing.equals(List.of(wither)) && destroys.isEmpty() && level.getBlockState(dirt).is(Blocks.DIRT);
        });
        checks.check("a hurt wither asks LivingDestroyBlockEvent for each block in reach the block lets it destroy, air included", () -> {
            WitherBoss wither = mob(EntityTypes.WITHER, pos);
            placeWitherBlocks(dirt, kept, proof);
            start();
            witherBreaks(wither);
            Set<BlockPos> expected = new HashSet<>();
            BlockPos.betweenClosed(pos.offset(-1, 0, -1), pos.offset(1, 3, 1)).forEach(p -> expected.add(p.immutable()));
            expected.remove(proof);
            Set<BlockPos> asked = new HashSet<>();
            destroys.forEach(destroy -> asked.add(destroy.pos()));
            return griefing.equals(List.of(wither)) && destroys.size() == expected.size() && asked.equals(expected)
                    && destroys.stream().allMatch(destroy -> destroy.entity() == wither)
                    && destroys.contains(new Destroy(wither, dirt, Blocks.DIRT.defaultBlockState()));
        });
        checks.check("a hurt wither breaks the blocks whose LivingDestroyBlockEvent isn't canceled", level.getBlockState(dirt).isAir()
                && level.getBlockState(kept).is(Blocks.DIRT) && level.getBlockState(proof).is(EventsMod.MOB_PROOF_BLOCK.get()));
        canceledDestroys.remove(kept);
    }

    private void placeWitherBlocks(BlockPos dirt, BlockPos kept, BlockPos proof) {
        place(dirt, Blocks.DIRT);
        place(kept, Blocks.DIRT);
        place(proof, EventsMod.MOB_PROOF_BLOCK.get());
    }

    /** The step of the wither's AI at which it breaks the blocks around it, as the 20 ticks after it was hurt run out. */
    private void witherBreaks(WitherBoss wither) {
        ((WitherBossAccessor) wither).events$setDestroyBlocksTick(1);
        ((MobInvoker) wither).events$customServerAiStep(level);
    }

    // A dangerous (blue) skull's explosion weakens the blocks it may destroy to 0.8.
    private void witherSkull() {
        checks.check("a blue wither skull weakens the blocks whose canEntityDestroy lets it destroy them", () -> {
            WitherSkull dangerous = new WitherSkull(EntityTypes.WITHER_SKULL, level);
            dangerous.setDangerous(true);
            WitherSkull plain = new WitherSkull(EntityTypes.WITHER_SKULL, level);
            FluidState noFluid = Fluids.EMPTY.defaultFluidState();
            BlockState obsidian = Blocks.OBSIDIAN.defaultBlockState();
            float weakened = dangerous.getBlockExplosionResistance(null, level, center, obsidian, noFluid, 1200.0F);
            float proof = dangerous.getBlockExplosionResistance(null, level, center, EventsMod.MOB_PROOF_BLOCK.get().defaultBlockState(), noFluid, 1200.0F);
            float unharmed = plain.getBlockExplosionResistance(null, level, center, obsidian, noFluid, 1200.0F);
            return weakened == 0.8F && proof == 1200.0F && unharmed == 1200.0F;
        });
    }

    // A ravager walking into leaves breaks them.
    private void ravager(BlockPos leaves) {
        BlockState state = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        checks.check("a ravager asks EntityMobGriefingEvent when it walks into leaves, and leaves them if it may not grief", () -> {
            Ravager ravager = denied(ravagerAt(leaves));
            place(leaves, state);
            place(leaves.above(), state);
            start();
            ravager.aiStep();
            return ravager.horizontalCollision && griefing.equals(List.of(ravager)) && level.getBlockState(leaves) == state && level.getBlockState(leaves.above()) == state;
        });
        checks.check("a ravager breaks the leaves it walks into if it may grief", () -> {
            Ravager ravager = ravagerAt(leaves);
            start();
            ravager.aiStep();
            return griefing.equals(List.of(ravager)) && level.getBlockState(leaves).isAir() && level.getBlockState(leaves.above()).isAir();
        });
    }

    /** A ravager just west of the leaves, moving into them. */
    private Ravager ravagerAt(BlockPos leaves) {
        Ravager ravager = mob(EntityTypes.RAVAGER, leaves.west(2));
        ravager.setPos(leaves.getX() - ravager.getBbWidth() / 2.0 - 0.05, leaves.getY(), leaves.getZ() + 0.5);
        ravager.setDeltaMovement(0.3, 0.0, 0.0);
        return ravager;
    }

    // A hurt silverfish wakes its friends: without griefing, their infested blocks turn back into plain ones.
    private void silverfish(BlockPos pos) {
        BlockPos infested = pos.east(2);
        place(infested, Blocks.INFESTED_STONE);
        checks.check("a silverfish waking its friends asks EntityMobGriefingEvent, and restores their blocks if it may not grief", () -> {
            Silverfish silverfish = denied(mob(EntityTypes.SILVERFISH, pos));
            Goal goal = goal(silverfish, named("SilverfishWakeUpFriendsGoal"));
            start();
            goal.tick();
            return griefing.equals(List.of(silverfish)) && level.getBlockState(infested).is(Blocks.STONE);
        });
    }

    private void crop(BlockPos wheat) {
        BlockState state = Blocks.WHEAT.defaultBlockState();
        place(wheat.below(), Blocks.FARMLAND);
        checks.check("a zombie in a crop isn't asked about griefing", () -> {
            place(wheat, state);
            return inCrop(mob(EntityTypes.ZOMBIE, wheat), wheat).isEmpty() && level.getBlockState(wheat) == state;
        });
        checks.check("a ravager in a crop asks EntityMobGriefingEvent, and leaves the crop if it may not grief", () -> {
            Ravager ravager = denied(mob(EntityTypes.RAVAGER, wheat));
            return inCrop(ravager, wheat).equals(List.of(ravager)) && level.getBlockState(wheat) == state;
        });
        checks.check("a ravager tramples a crop if it may grief", () -> {
            Ravager ravager = mob(EntityTypes.RAVAGER, wheat);
            return inCrop(ravager, wheat).equals(List.of(ravager)) && level.getBlockState(wheat).isAir();
        });
    }

    private List<Entity> inCrop(Entity entity, BlockPos crop) {
        start();
        level.getBlockState(crop).entityInside(level, crop, entity, InsideBlockEffectApplier.NOOP, true);
        return List.copyOf(griefing);
    }

    // Breaking an egg is a 1 in 100 roll after the question, so only the refusal is certain.
    private void turtleEgg(BlockPos egg) {
        BlockState state = Blocks.TURTLE_EGG.defaultBlockState();
        place(egg.below(), Blocks.SAND);
        place(egg, state);
        checks.check("a mob stepping on turtle eggs asks EntityMobGriefingEvent, and leaves them if it may not grief", () -> {
            Zombie zombie = denied(mob(EntityTypes.ZOMBIE, egg));
            start();
            Blocks.TURTLE_EGG.stepOn(level, egg, state, zombie);
            return griefing.equals(List.of(zombie)) && level.getBlockState(egg) == state;
        });
        checks.check("an entity that isn't living, on turtle eggs, isn't asked about griefing", () -> {
            Snowball snowball = new Snowball(level, egg.getX() + 0.5, egg.getY(), egg.getZ() + 0.5, new ItemStack(Items.SNOWBALL));
            tracked.add(snowball);
            start();
            Blocks.TURTLE_EGG.stepOn(level, egg, state, snowball);
            return griefing.isEmpty() && level.getBlockState(egg) == state;
        });
    }

    /** Clears what the listeners recorded, before the action a check looks at. */
    private void start() {
        griefing.clear();
        destroys.clear();
    }

    private boolean noFire(BlockPos around) {
        return BlockPos.betweenClosedStream(around.offset(-3, -3, -3), around.offset(3, 3, 3)).noneMatch(pos -> level.getBlockState(pos).is(Blocks.FIRE));
    }

    private <T extends Mob> T mob(EntityType<T> type, BlockPos pos) {
        T mob = Objects.requireNonNull(type.create(level, EntitySpawnReason.COMMAND));
        mob.setPos(Vec3.atBottomCenterOf(pos));
        tracked.add(mob);
        return mob;
    }

    private <T extends Entity> T denied(T entity) {
        denied.add(entity);
        return entity;
    }

    private void place(BlockPos pos, Block block) {
        place(pos, block.defaultBlockState());
    }

    private void place(BlockPos pos, BlockState state) {
        level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }

    private static Goal goal(Mob mob, Predicate<Goal> which) {
        List<Goal> goals = new ArrayList<>();
        for (WrappedGoal wrapped : mob.getGoalSelector().getAvailableGoals()) {
            if (which.test(wrapped.getGoal())) goals.add(wrapped.getGoal());
        }
        if (goals.size() != 1) throw new IllegalStateException("expected one matching goal on " + mob + ", found " + goals);
        return goals.getFirst();
    }

    private static Predicate<Goal> named(String simpleName) {
        return goal -> goal.getClass().getSimpleName().equals(simpleName);
    }

    private record Destroy(Entity entity, BlockPos pos, BlockState state) {}
}
