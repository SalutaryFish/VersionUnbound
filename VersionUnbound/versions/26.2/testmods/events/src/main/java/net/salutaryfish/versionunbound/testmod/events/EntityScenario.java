package net.salutaryfish.versionunbound.testmod.events;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.neoforged.neoforge.event.entity.EntityEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;

/**
 * The entity events that come from {@code Entity} itself, on the server, with mobs that aren't added to the level:
 * {@code EntityEvent.Size} and {@code EntityConstructing} in the constructor (in that order, after the dimensions and
 * eye height are set; a new size becomes the entity's), {@code Size} when the dimensions are refreshed (a pose change:
 * the pose, the old and new dimensions; a new size becomes the entity's, bounding box included), and
 * {@code LivingEvent.LivingVisibilityEvent} in {@code getVisibilityPercent} (vanilla's visibility as the modifier, the
 * looking entity or none, a changed modifier, a negative one clamped to 0, and a mob's targeting range following it).
 * The constructor's events can't be told apart by entity before the entity exists, so the listeners record them for
 * this level only while the scenario constructs a mob; the other listeners only look at the scenario's own mobs.
 */
final class EntityScenario {
    private final Checks checks;
    private volatile ServerLevel constructingIn;
    private volatile EntityDimensions constructorSize;
    private final List<Object> constructorEvents = new CopyOnWriteArrayList<>();
    private final Map<Entity, List<Sized>> sizes = Collections.synchronizedMap(new IdentityHashMap<>());
    private final Map<Entity, EntityDimensions> newSizes = Collections.synchronizedMap(new IdentityHashMap<>());
    private final Map<Entity, List<Seen>> seen = Collections.synchronizedMap(new IdentityHashMap<>());
    private final Map<Entity, Double> visibilityChanges = Collections.synchronizedMap(new IdentityHashMap<>());
    private final Set<Entity> tracked = Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));

    EntityScenario(Checks checks) {
        this.checks = checks;
    }

    void size(EntityEvent.Size event) {
        Entity entity = event.getEntity();
        if (entity.level() == constructingIn) {
            constructorEvents.add(new Sized(entity, event.getPose(), event.getOldSize(), event.getNewSize()));
            if (constructorSize != null) event.setNewSize(constructorSize);
        }
        if (!tracked.contains(entity)) return;
        sizes.computeIfAbsent(entity, e -> new CopyOnWriteArrayList<>()).add(new Sized(entity, event.getPose(), event.getOldSize(), event.getNewSize()));
        EntityDimensions newSize = newSizes.get(entity);
        if (newSize != null) event.setNewSize(newSize);
    }

    void constructing(EntityEvent.EntityConstructing event) {
        Entity entity = event.getEntity();
        if (entity.level() != constructingIn) return;
        constructorEvents.add(new Constructed(entity, entity.getEntityData() != null, entity.getBbWidth(), entity.getEyeHeight()));
    }

    void visibility(LivingEvent.LivingVisibilityEvent event) {
        if (!tracked.contains(event.getEntity())) return;
        seen.computeIfAbsent(event.getEntity(), e -> new CopyOnWriteArrayList<>()).add(new Seen(event.getLookingEntity(), event.getVisibilityModifier()));
        Double change = visibilityChanges.get(event.getEntity());
        if (change != null) event.modifyVisibility(change);
    }

    void run(ServerLevel level) {
        constructor(level);
        refresh(level);
        visibility(level);
    }

    private void constructor(ServerLevel level) {
        EntityDimensions chickenSize = EntityTypes.CHICKEN.getDimensions();
        Chicken chicken = construct(level, null, () -> EntityTypes.CHICKEN.create(level, EntitySpawnReason.COMMAND));
        List<Object> events = List.copyOf(constructorEvents);
        checks.check("EntityEvent.Size fires in the constructor, with the standing pose and the type's dimensions as both sizes",
                events.size() == 2 && events.get(0).equals(new Sized(chicken, Pose.STANDING, chickenSize, chickenSize)));
        checks.check("EntityEvent.EntityConstructing fires once at the end of the constructor, after Size",
                events.size() == 2 && events.get(1).equals(new Constructed(chicken, true, chickenSize.width(), chickenSize.eyeHeight())));

        EntityDimensions big = EntityDimensions.fixed(2.0F, 3.0F);
        Chicken resized = construct(level, big, () -> EntityTypes.CHICKEN.create(level, EntitySpawnReason.COMMAND));
        Object constructed = constructorEvents.isEmpty() ? null : constructorEvents.getLast();
        checks.check("a new size from EntityEvent.Size in the constructor becomes the entity's dimensions and eye height, before EntityConstructing",
                resized.getBbWidth() == 2.0F && resized.getBbHeight() == 3.0F && resized.getEyeHeight() == big.eyeHeight()
                        && new Constructed(resized, true, 2.0F, big.eyeHeight()).equals(constructed));
    }

    private <T extends Entity> T construct(ServerLevel level, EntityDimensions size, Supplier<T> factory) {
        constructorEvents.clear();
        constructorSize = size;
        constructingIn = level;
        try {
            return factory.get();
        } finally {
            constructingIn = null;
            constructorSize = null;
        }
    }

    private void refresh(ServerLevel level) {
        Zombie zombie = mob(level, EntityTypes.ZOMBIE);
        zombie.setPos(0.5, level.getMaxY() - 40, 0.5);
        EntityDimensions standing = zombie.getDimensions(Pose.STANDING);
        EntityDimensions sleeping = zombie.getDimensions(Pose.SLEEPING);
        // The pose is synced data; setting it refreshes the dimensions.
        zombie.setPose(Pose.SLEEPING);
        checks.check("EntityEvent.Size fires when an entity's dimensions are refreshed, with its pose and its old and new dimensions",
                sizes.getOrDefault(zombie, List.of()).equals(List.of(new Sized(zombie, Pose.SLEEPING, standing, sleeping))) && zombie.getBbHeight() == sleeping.height());

        EntityDimensions flat = EntityDimensions.fixed(1.5F, 0.5F);
        newSizes.put(zombie, flat);
        sizes.remove(zombie);
        zombie.setPose(Pose.STANDING);
        checks.check("a new size from EntityEvent.Size replaces the refreshed dimensions, eye height and bounding box",
                sizes.getOrDefault(zombie, List.of()).equals(List.of(new Sized(zombie, Pose.STANDING, sleeping, standing)))
                        && zombie.getBbWidth() == 1.5F && zombie.getBbHeight() == 0.5F && zombie.getEyeHeight() == flat.eyeHeight()
                        && Math.abs(zombie.getBoundingBox().getXsize() - 1.5) < 1.0E-6 && Math.abs(zombie.getBoundingBox().getYsize() - 0.5) < 1.0E-6);
    }

    private void visibility(ServerLevel level) {
        double y = level.getMaxY() - 40;
        Zombie zombie = mob(level, EntityTypes.ZOMBIE);
        zombie.setPos(0.5, y, 0.5);
        Chicken chicken = mob(level, EntityTypes.CHICKEN);
        chicken.setPos(10.5, y, 0.5);

        double plain = chicken.getVisibilityPercent(zombie);
        checks.check("LivingVisibilityEvent fires in getVisibilityPercent, with the looking entity and vanilla's visibility",
                plain == 1.0 && take(chicken).equals(List.of(new Seen(zombie, 1.0))));
        // Sneaking makes an entity discrete: vanilla's 0.8.
        chicken.setShiftKeyDown(true);
        visibilityChanges.put(chicken, 0.5);
        double halved = chicken.getVisibilityPercent(zombie);
        checks.check("the visibility is what LivingVisibilityEvent's modifier ends up as",
                Math.abs(halved - 0.4) < 1.0E-9 && take(chicken).equals(List.of(new Seen(zombie, 0.8))));
        visibilityChanges.put(chicken, -1.0);
        checks.check("a negative visibility from LivingVisibilityEvent becomes 0", chicken.getVisibilityPercent(zombie) == 0.0 && take(chicken).size() == 1);
        visibilityChanges.remove(chicken);
        chicken.setShiftKeyDown(false);
        double alone = chicken.getVisibilityPercent(null);
        checks.check("LivingVisibilityEvent fires without a looking entity", alone == 1.0 && take(chicken).equals(List.of(new Seen(null, 1.0))));

        // A mob's targeting range is scaled by the target's visibility: 16 blocks reach the chicken 10 blocks away, 8 don't.
        TargetingConditions conditions = TargetingConditions.forCombat().range(16.0).ignoreLineOfSight();
        boolean visible = conditions.test(level, zombie, chicken);
        visibilityChanges.put(chicken, 0.5);
        boolean hidden = conditions.test(level, zombie, chicken);
        checks.check("a mob's targeting conditions use the visibility from LivingVisibilityEvent", visible && !hidden && take(chicken).size() == 2);
    }

    private <T extends Entity> T mob(ServerLevel level, EntityType<T> type) {
        T mob = type.create(level, EntitySpawnReason.COMMAND);
        tracked.add(mob);
        return mob;
    }

    private List<Seen> take(Entity entity) {
        List<Seen> events = seen.remove(entity);
        return events == null ? List.of() : List.copyOf(events);
    }

    private record Sized(Entity entity, Pose pose, EntityDimensions oldSize, EntityDimensions newSize) {}

    private record Constructed(Entity entity, boolean dataBuilt, float width, float eyeHeight) {}

    private record Seen(Entity lookingEntity, double modifier) {}
}
