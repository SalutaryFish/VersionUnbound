package net.salutaryfish.versionunbound.testmod.events;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.equine.Llama;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.LlamaSpit;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;

/**
 * {@code ProjectileImpactEvent} at each of NeoForge's sites. On the server, a few ticks after it starts (when entities
 * in the forced chunk are visible to the projectiles' hit checks): each kind of projectile is ticked once, by hand,
 * toward a zombie right in front of it, once hitting and once with the impact canceled; an arrow also hits a block. With
 * a player ({@link #runWithPlayer}): a fishing hook, which only flies for a player holding a rod. The projectiles
 * aren't added to the level, so nothing else ticks them, and each zombie goes before the next shot.
 */
final class ProjectileScenario {
    private final Checks checks;
    private final Checks playerChecks;
    private final Map<Entity, List<ProjectileImpactEvent>> impacts = Collections.synchronizedMap(new IdentityHashMap<>());
    private final Set<Entity> canceled = Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));

    ProjectileScenario(Checks checks, Checks playerChecks) {
        this.checks = checks;
        this.playerChecks = playerChecks;
    }

    void impact(ProjectileImpactEvent event) {
        impacts.computeIfAbsent(event.getProjectile(), key -> new ArrayList<>()).add(event);
        if (canceled.contains(event.getProjectile())) event.setCanceled(true);
    }

    void run(ServerLevel level, Vec3 pos) {
        // Arrows (AbstractArrow.stepMoveAndHit): an entity, then a block.
        Function<Vec3, Projectile> arrow = start -> new Arrow(level, start.x, start.y, start.z, new ItemStack(Items.ARROW), null);
        Shot arrowHit = shoot(level, pos, arrow, false);
        checks.check("ProjectileImpactEvent fires when an arrow hits an entity, and the entity is hurt", arrowHit.hitTarget() && arrowHit.hurt());
        Shot arrowMiss = shoot(level, pos, arrow, true);
        checks.check("canceling ProjectileImpactEvent lets an arrow through an entity", arrowMiss.hitTarget() && !arrowMiss.hurt() && !arrowMiss.projectile().isRemoved());
        BlockPos wall = BlockPos.containing(pos);
        level.setBlock(wall, Blocks.STONE.defaultBlockState(), 3);
        Arrow stuck = (Arrow) shootAtBlock(arrow, wall, false);
        checks.check("ProjectileImpactEvent fires when an arrow hits a block, and the arrow sticks", hitBlock(stuck, wall) && stuck.shakeTime == 7);
        Arrow bounced = (Arrow) shootAtBlock(arrow, wall, true);
        checks.check("canceling ProjectileImpactEvent keeps an arrow from sticking in a block", hitBlock(bounced, wall) && bounced.shakeTime == 0);
        level.removeBlock(wall, false);

        // Thrown items (ThrowableProjectile). A canceled impact only skips the move to the hit: NeoForge's patch leaves
        // vanilla's later hit check alone, so the snowball still hits and goes.
        Function<Vec3, Projectile> snowball = start -> new Snowball(level, start.x, start.y, start.z, new ItemStack(Items.SNOWBALL));
        Shot thrownHit = shoot(level, pos, snowball, false);
        checks.check("ProjectileImpactEvent fires when a thrown item hits an entity, which moves it to the hit",
                thrownHit.hitTarget() && thrownHit.projectile().isRemoved() && thrownHit.travelled() < 0.9);
        Shot thrownMiss = shoot(level, pos, snowball, true);
        checks.check("canceling ProjectileImpactEvent moves a thrown item on past the hit, which it still makes (as on NeoForge)",
                thrownMiss.hitTarget() && thrownMiss.projectile().isRemoved() && thrownMiss.travelled() > 0.95);

        // Fireballs (AbstractHurtingProjectile), shulker bullets and fireworks go when they hit; llama spit goes only on
        // blocks, and hurts what it hits only for a llama (one that isn't in the level).
        expectHits("a small fireball", level, pos, start -> new SmallFireball(level, start.x, start.y, start.z, new Vec3(0, 0, 1)), Shot::removed);
        Llama llama = EntityTypes.LLAMA.create(level, EntitySpawnReason.COMMAND);
        expectHits("llama spit", level, pos, start -> at(new LlamaSpit(level, llama), start), Shot::hurt);
        expectHits("a shulker bullet", level, pos, start -> at(new ShulkerBullet(EntityTypes.SHULKER_BULLET, level), start), Shot::removed);
        expectHits("a firework", level, pos, start -> new FireworkRocketEntity(level, new ItemStack(Items.FIREWORK_ROCKET), start.x, start.y, start.z, true),
                Shot::removed);
    }

    /** A fishing hook cast at a zombie: it hooks the zombie, and a canceled impact leaves the hook empty. */
    void runWithPlayer(ServerPlayer player, ServerLevel level, BlockPos base) {
        Vec3 pos = Vec3.atBottomCenterOf(base.offset(3, 0, -2));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.FISHING_ROD));
        try {
            Function<Vec3, Projectile> hook = start -> at(new FishingHook(player, level, 0, 0), start);
            Shot hooked = shoot(level, pos, hook, false);
            playerChecks.check("ProjectileImpactEvent fires when a fishing hook hits an entity, which it hooks",
                    hooked.hitTarget() && ((FishingHook) hooked.projectile()).getHookedIn() == hooked.target());
            hooked.projectile().discard();
            Shot missed = shoot(level, pos, hook, true);
            playerChecks.check("canceling ProjectileImpactEvent keeps a fishing hook from hooking the entity",
                    missed.hitTarget() && ((FishingHook) missed.projectile()).getHookedIn() == null);
            missed.projectile().discard();
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }
    }

    /** A hit and a canceled one; {@code hitDid} tells from the shot that the projectile hit. */
    private void expectHits(String what, ServerLevel level, Vec3 pos, Function<Vec3, Projectile> projectile, Predicate<Shot> hitDid) {
        Shot hit = shoot(level, pos, projectile, false);
        checks.check("ProjectileImpactEvent fires when " + what + " hits an entity, and it hits", hit.hitTarget() && hitDid.test(hit));
        Shot miss = shoot(level, pos, projectile, true);
        checks.check("canceling ProjectileImpactEvent for " + what + " keeps it flying, the entity unhurt",
                miss.hitTarget() && !miss.projectile().isRemoved() && !miss.hurt());
    }

    /**
     * Ticks a projectile once, from a block in front of a fresh zombie at {@code pos}, flying straight at it. The zombie
     * is removed again; the result says what the event and the tick did.
     */
    private Shot shoot(ServerLevel level, Vec3 pos, Function<Vec3, Projectile> make, boolean cancel) {
        LivingEntity target = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
        target.setPos(pos);
        level.addFreshEntity(target);
        Vec3 start = pos.add(0, 0.5, -1.0);
        Projectile projectile = make.apply(start);
        try {
            fly(projectile, cancel);
            List<ProjectileImpactEvent> events = impacts.getOrDefault(projectile, List.of());
            boolean hitTarget = events.size() == 1 && events.get(0).getRayTraceResult() instanceof EntityHitResult hit && hit.getEntity() == target;
            boolean hurt = target.getHealth() < target.getMaxHealth() || target.getRemainingFireTicks() > 0;
            return new Shot(projectile, target, hitTarget, hurt, projectile.getZ() - start.z);
        } finally {
            target.discard();
        }
    }

    /** Ticks a projectile once toward the north face of the block from half a block away. */
    private Projectile shootAtBlock(Function<Vec3, Projectile> make, BlockPos block, boolean cancel) {
        Projectile projectile = make.apply(Vec3.atCenterOf(block).add(0, 0, -1.0));
        fly(projectile, cancel);
        return projectile;
    }

    private void fly(Projectile projectile, boolean cancel) {
        projectile.setDeltaMovement(0, 0, 1.0);
        if (cancel) canceled.add(projectile);
        projectile.tick();
    }

    private boolean hitBlock(Projectile projectile, BlockPos block) {
        List<ProjectileImpactEvent> events = impacts.getOrDefault(projectile, List.of());
        return events.size() == 1 && events.get(0).getRayTraceResult() instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
                && hit.getBlockPos().equals(block);
    }

    private static Projectile at(Projectile projectile, Vec3 pos) {
        projectile.setPos(pos);
        return projectile;
    }

    /** One shot: the projectile and its target, whether the event saw the target hit, and how far the projectile moved. */
    private record Shot(Projectile projectile, LivingEntity target, boolean hitTarget, boolean hurt, double travelled) {
        boolean removed() {
            return projectile.isRemoved();
        }
    }
}
