package net.salutaryfish.versionunbound.testmod.registries;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;

/**
 * NeoForge's entity events on the mod bus, which fire right after {@code RegisterEvent}: entity attributes (created for
 * this mod's mob, {@code registries:probe_mob}, a pig underneath; modified for it and for vanilla's zombie) and spawn
 * placements (a new one for the mob with an AND predicate on top, and an OR predicate on vanilla's cow that only says yes
 * during a check, so cows spawn as in vanilla). The events' results are checked at common setup; real mobs and spawn
 * rules once the server has started, with {@code MobSpawnEvent.SpawnPlacementCheck} on the game bus.
 */
final class EntityChecks {
    private static final AtomicInteger CREATION_EVENTS = new AtomicInteger();
    private static final AtomicInteger MODIFICATION_EVENTS = new AtomicInteger();
    private static final AtomicInteger PLACEMENT_EVENTS = new AtomicInteger();
    private static final AtomicBoolean CREATION_REFUSED_ZOMBIE = new AtomicBoolean();
    private static final AtomicBoolean MODIFICATION_LISTED_TYPES = new AtomicBoolean();
    private static final AtomicBoolean MODIFICATION_HAS = new AtomicBoolean();
    private static final AtomicBoolean NEW_PLACEMENT_NEEDS_TYPES = new AtomicBoolean();
    private static final AtomicBoolean ONLY_REPLACE_CHANGES_TYPES = new AtomicBoolean();

    // What the spawn predicates and the SpawnPlacementCheck listener answer; set only during the world checks.
    private static volatile boolean cowOr;
    private static volatile boolean probeOriginal;
    private static volatile boolean probeAnd;
    private static volatile boolean probeVetoed;
    private static volatile Boolean probeDefaultResult;

    private EntityChecks() {}

    static void register(IEventBus modBus) {
        modBus.addListener(EntityAttributeCreationEvent.class, EntityChecks::createAttributes);
        modBus.addListener(EntityAttributeModificationEvent.class, EntityChecks::modifyAttributes);
        modBus.addListener(RegisterSpawnPlacementsEvent.class, EntityChecks::registerSpawnPlacements);
        NeoForge.EVENT_BUS.addListener(MobSpawnEvent.SpawnPlacementCheck.class, EntityChecks::spawnPlacementCheck);
    }

    private static void createAttributes(EntityAttributeCreationEvent event) {
        CREATION_EVENTS.incrementAndGet();
        // The second MAX_HEALTH replaces the pig's.
        event.put(RegistriesMod.PROBE_MOB.get(), Pig.createAttributes().add(Attributes.MAX_HEALTH, 12.0).build());
        try {
            event.put(EntityTypes.ZOMBIE, Zombie.createAttributes().build());
        } catch (IllegalStateException e) {
            CREATION_REFUSED_ZOMBIE.set(true);
        }
    }

    private static void modifyAttributes(EntityAttributeModificationEvent event) {
        MODIFICATION_EVENTS.incrementAndGet();
        MODIFICATION_LISTED_TYPES.set(event.getTypes().contains(EntityTypes.ZOMBIE) && event.getTypes().contains(RegistriesMod.PROBE_MOB.get())
                && !event.getTypes().contains(RegistriesMod.PROBE_ENTITY.get()));
        // has() reads the type's attributes, then the ones added in this event (NeoForge's AttributeSupplier.Builder patch).
        boolean before = event.has(EntityTypes.ZOMBIE, Attributes.FOLLOW_RANGE) && !event.has(EntityTypes.ZOMBIE, Attributes.LUCK);
        event.add(EntityTypes.ZOMBIE, Attributes.LUCK, 3.0);
        event.add(RegistriesMod.PROBE_MOB.get(), Attributes.ARMOR, 4.0);
        MODIFICATION_HAS.set(before && event.has(EntityTypes.ZOMBIE, Attributes.LUCK));
    }

    private static void registerSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        PLACEMENT_EVENTS.incrementAndGet();
        EntityType<Pig> mob = RegistriesMod.PROBE_MOB.get();
        try {
            event.register(mob, null, null, (type, level, reason, pos, random) -> true, RegisterSpawnPlacementsEvent.Operation.OR);
        } catch (NullPointerException e) {
            NEW_PLACEMENT_NEEDS_TYPES.set(true);
        }
        event.register(mob, SpawnPlacementTypes.IN_WATER, Heightmap.Types.OCEAN_FLOOR, (type, level, reason, pos, random) -> probeOriginal,
                RegisterSpawnPlacementsEvent.Operation.OR);
        event.register(mob, (type, level, reason, pos, random) -> probeAnd, RegisterSpawnPlacementsEvent.Operation.AND);
        event.register(EntityTypes.COW, (type, level, reason, pos, random) -> cowOr);
        try {
            event.register(EntityTypes.COW, SpawnPlacementTypes.IN_WATER, null, (type, level, reason, pos, random) -> true, RegisterSpawnPlacementsEvent.Operation.AND);
        } catch (IllegalStateException e) {
            ONLY_REPLACE_CHANGES_TYPES.set(true);
        }
    }

    private static void spawnPlacementCheck(MobSpawnEvent.SpawnPlacementCheck event) {
        if (event.getEntityType() != RegistriesMod.PROBE_MOB.get()) return;
        probeDefaultResult = event.getDefaultResult();
        if (probeVetoed) event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
    }

    /** At common setup: what the events left in vanilla's tables, and what fabricprobe saw. */
    static void checkRegistration(Checks checks) {
        EntityType<Pig> mob = RegistriesMod.PROBE_MOB.get();
        checks.check("EntityAttributeCreationEvent fired once", CREATION_EVENTS.get(), 1);
        checks.check("EntityAttributeCreationEvent refuses a type that has attributes", CREATION_REFUSED_ZOMBIE.get());
        checks.check("a mod's mob has the attributes it created", () -> {
            AttributeSupplier attributes = DefaultAttributes.getSupplier(mob);
            return DefaultAttributes.hasSupplier(mob) && attributes.getBaseValue(Attributes.MAX_HEALTH) == 12.0
                    && attributes.getBaseValue(Attributes.MOVEMENT_SPEED) == 0.25;
        });
        checks.check("EntityAttributeModificationEvent fired once", MODIFICATION_EVENTS.get(), 1);
        checks.check("EntityAttributeModificationEvent lists the types that have attributes", MODIFICATION_LISTED_TYPES.get());
        checks.check("EntityAttributeModificationEvent.has sees the type's attributes and the added ones", MODIFICATION_HAS.get());
        checks.check("modified attributes keep the type's own and add the new ones", () -> {
            AttributeSupplier zombie = DefaultAttributes.getSupplier(EntityTypes.ZOMBIE);
            AttributeSupplier probe = DefaultAttributes.getSupplier(mob);
            return zombie.getBaseValue(Attributes.LUCK) == 3.0 && zombie.getBaseValue(Attributes.FOLLOW_RANGE) == 35.0
                    && zombie.hasAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE)
                    && probe.getBaseValue(Attributes.ARMOR) == 4.0 && probe.getBaseValue(Attributes.MAX_HEALTH) == 12.0;
        });

        checks.check("RegisterSpawnPlacementsEvent fired once", PLACEMENT_EVENTS.get(), 1);
        checks.check("a new spawn placement needs a placement type and a heightmap", NEW_PLACEMENT_NEEDS_TYPES.get());
        checks.check("only REPLACE changes a spawn placement's type", ONLY_REPLACE_CHANGES_TYPES.get());
        checks.check("a new spawn placement has its placement type and heightmap", SpawnPlacements.getPlacementType(mob) == SpawnPlacementTypes.IN_WATER
                && SpawnPlacements.getHeightmapType(mob) == Heightmap.Types.OCEAN_FLOOR
                && SpawnPlacements.getPlacementType(EntityTypes.COW) == SpawnPlacementTypes.ON_GROUND
                && SpawnPlacements.getHeightmapType(EntityTypes.COW) == Heightmap.Types.MOTION_BLOCKING_NO_LEAVES);

        // What the fabricprobe testmod saw from Fabric's side, while Fabric initialized its mods.
        checks.check("Fabric mods see NeoForge mods' entity attributes", Boolean.getBoolean("versionunbound.testprobe.sawNeoForgeAttributes"));
        checks.check("Fabric's default attribute registry works next to NeoForge's", () -> {
            @SuppressWarnings("unchecked")
            var fabricMob = (EntityType<? extends LivingEntity>) BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.fromNamespaceAndPath("versionunbound_fabricprobe", "probe_mob"));
            return fabricMob != EntityTypes.PIG && DefaultAttributes.hasSupplier(fabricMob)
                    && DefaultAttributes.getSupplier(fabricMob).getBaseValue(Attributes.MAX_HEALTH) == 10.0;
        });
    }

    /** Once the server has started: mobs made with the attributes, and the spawn rules with the predicates. */
    static void checkWorld(Checks checks, ServerLevel level) {
        checks.check("a mod's mob spawns with its attributes", () -> {
            Pig mob = RegistriesMod.PROBE_MOB.get().create(level, EntitySpawnReason.COMMAND);
            return mob.getMaxHealth() == 12.0F && mob.getHealth() == 12.0F && mob.getArmorValue() == 4;
        });
        checks.check("a zombie spawns with the attribute added to vanilla's", () -> {
            Zombie zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
            return zombie.getAttributeBaseValue(Attributes.LUCK) == 3.0 && zombie.getAttributeBaseValue(Attributes.FOLLOW_RANGE) == 35.0;
        });

        // At the top of the world, where vanilla's animal rule fails (nothing to stand on).
        BlockPos pos = new BlockPos(0, level.getMaxY() - 1, 0);
        try {
            checks.check("a cow's spawn rule is still vanilla's", !spawns(EntityTypes.COW, level, pos));
            cowOr = true;
            checks.check("an OR predicate lets a cow spawn where vanilla's rule fails", spawns(EntityTypes.COW, level, pos));
            cowOr = false;

            EntityType<Pig> mob = RegistriesMod.PROBE_MOB.get();
            probeAnd = true;
            boolean originalDecides = !spawns(mob, level, pos) && probeDefaultResult == Boolean.FALSE;
            probeOriginal = true;
            originalDecides &= spawns(mob, level, pos) && probeDefaultResult == Boolean.TRUE;
            checks.check("a new spawn placement's predicate decides, and SpawnPlacementCheck sees its result", originalDecides);
            probeAnd = false;
            checks.check("an AND predicate stops a spawn the predicate allows", !spawns(mob, level, pos));
            probeAnd = true;
            probeVetoed = true;
            checks.check("SpawnPlacementCheck overrules the predicates", !spawns(mob, level, pos) && probeDefaultResult == Boolean.TRUE);
        } finally {
            cowOr = probeOriginal = probeAnd = probeVetoed = false;
        }
    }

    private static boolean spawns(EntityType<?> type, ServerLevel level, BlockPos pos) {
        probeDefaultResult = null;
        return SpawnPlacements.checkSpawnRules(type, level, EntitySpawnReason.NATURAL, pos, level.getRandom());
    }
}
