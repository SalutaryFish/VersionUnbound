package net.salutaryfish.versionunbound.testmod.events;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.logging.LogUtils;
import java.nio.file.Files;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.GameShuttingDownEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityEvent;
import net.neoforged.neoforge.event.entity.EntityInvulnerabilityCheckEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.item.ItemExpireEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.AnimalTameEvent;
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent;
import net.neoforged.neoforge.event.entity.living.EffectParticleModificationEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingBreatheEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingConversionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDestroyBlockEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDrownEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerDestroyItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerRespawnPositionEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkDataEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.ChunkTicketLevelUpdatedEvent;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.ExplosionKnockbackEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.level.NoteBlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.level.block.CropGrowEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

/**
 * Server-side checks, one {@link Session} per server run. Each event appends a token to the session's trace, and the
 * order is checked when the server has stopped. Commands and ticking entities are set up once the server has started.
 */
final class ServerEvents {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PROBE = "eventsprobe";
    private static final ProbeConfigSpec SERVER_CONFIG = new ProbeConfigSpec();
    private static volatile Session session;

    private ServerEvents() {}

    static void register(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, SERVER_CONFIG);
        modBus.addListener(ModConfigEvent.Loading.class, event -> {
            if (event.getConfig().getType() == ModConfig.Type.SERVER) {
                session().serverConfigLoaded(event.getConfig());
            }
        });

        IEventBus bus = NeoForge.EVENT_BUS;
        bus.addListener(RegisterCommandsEvent.class, event -> session().registerCommands(event));
        bus.addListener(CommandEvent.class, event -> session().command(event));
        bus.addListener(ServerAboutToStartEvent.class, event -> session().aboutToStart(event));
        bus.addListener(LevelEvent.Load.class, event -> {
            if (event.getLevel() instanceof ServerLevel level) session().trace("load:" + name(level));
        });
        bus.addListener(LevelEvent.CreateSpawnPosition.class, event -> session().trace("spawn"));
        bus.addListener(ServerStartingEvent.class, event -> session().starting(event));
        bus.addListener(ServerStartedEvent.class, event -> session().started(event));
        bus.addListener(ServerTickEvent.Pre.class, event -> session().serverTick(event, true));
        bus.addListener(ServerTickEvent.Post.class, event -> {
            session().serverTick(event, false);
            session().players.serverTickEnd();
        });
        bus.addListener(LevelTickEvent.Pre.class, event -> {
            if (event.getLevel() instanceof ServerLevel level) session().levelTick(level, true);
        });
        bus.addListener(LevelTickEvent.Post.class, event -> {
            if (event.getLevel() instanceof ServerLevel level) session().levelTick(level, false);
        });
        bus.addListener(EntityTickEvent.Pre.class, event -> {
            if (!event.getEntity().level().isClientSide()) session().entityTickPre(event);
        });
        bus.addListener(EntityTickEvent.Post.class, event -> {
            if (!event.getEntity().level().isClientSide()) session().entityTickPost(event.getEntity());
        });
        bus.addListener(EntityJoinLevelEvent.class, event -> {
            if (!event.getLevel().isClientSide()) {
                session().spawns.joined(event);
                session().conversions.joined(event);
                session().griefing.joined(event);
                session().entityJoined(event);
            }
        });
        bus.addListener(EntityLeaveLevelEvent.class, event -> {
            if (!event.getLevel().isClientSide()) session().leaves.merge(event.getEntity(), 1, Integer::sum);
        });
        bus.addListener(LivingDeathEvent.class, event -> {
            if (!event.getEntity().level().isClientSide()) session().livingDeath(event);
        });
        bus.addListener(LivingDropsEvent.class, event -> {
            if (!event.getEntity().level().isClientSide()) session().livingDrops(event);
        });
        bus.addListener(LivingIncomingDamageEvent.class, event -> {
            if (!event.getEntity().level().isClientSide()) session().damage.incoming(event);
        });
        bus.addListener(LivingDamageEvent.Pre.class, event -> {
            if (!event.getEntity().level().isClientSide()) session().damage.pre(event);
        });
        bus.addListener(LivingDamageEvent.Post.class, event -> {
            if (!event.getEntity().level().isClientSide()) session().damage.post(event);
        });
        bus.addListener(EntityInvulnerabilityCheckEvent.class, event -> {
            if (!event.getEntity().level().isClientSide()) session().damage.invulnerability(event);
        });
        bus.addListener(LivingChangeTargetEvent.class, event -> {
            if (!event.getEntity().level().isClientSide()) session().living.changeTarget(event);
        });
        bus.addListener(LivingKnockBackEvent.class, event -> {
            if (!event.getEntity().level().isClientSide()) session().living.knockBack(event);
        });
        bus.addListener(LivingEquipmentChangeEvent.class, event -> {
            if (!event.getEntity().level().isClientSide()) session().living.equipmentChange(event);
        });
        for (Class<? extends LivingEntityUseItemEvent> phase : List.of(LivingEntityUseItemEvent.Start.class, LivingEntityUseItemEvent.Tick.class,
                LivingEntityUseItemEvent.Stop.class, LivingEntityUseItemEvent.Finish.class)) {
            bus.addListener(phase, event -> {
                if (!event.getEntity().level().isClientSide()) session().living.useItem(event);
            });
        }
        // Items and chunk data: these fire only on the server.
        bus.addListener(LivingHealEvent.class, event -> {
            if (!event.getEntity().level().isClientSide()) session().living.heal(event);
        });
        bus.addListener(LivingFallEvent.class, event -> {
            if (!event.getEntity().level().isClientSide()) session().living.fall(event);
        });
        bus.addListener(LivingEvent.LivingJumpEvent.class, event -> {
            if (!event.getEntity().level().isClientSide()) session().living.jump(event);
        });
        bus.addListener(PlayerDestroyItemEvent.class, event -> {
            if (!event.getEntity().level().isClientSide()) session().destroyedItems.destroyItem(event);
        });
        bus.addListener(ProjectileImpactEvent.class, event -> {
            if (!event.getProjectile().level().isClientSide()) session().projectiles.impact(event);
        });
        // Experience drops and breathing happen only on the server.
        bus.addListener(LivingExperienceDropEvent.class, event -> session().survival.experience(event));
        bus.addListener(LivingBreatheEvent.class, event -> session().survival.breathe(event));
        bus.addListener(LivingDrownEvent.class, event -> session().survival.drown(event));
        bus.addListener(FinalizeSpawnEvent.class, event -> session().spawns.finalizeSpawn(event));
        bus.addListener(MobSpawnEvent.PositionCheck.class, event -> session().spawns.positionCheck(event));
        bus.addListener(MobSpawnEvent.SpawnPlacementCheck.class, event -> session().spawns.placementCheck(event));
        bus.addListener(AnimalTameEvent.class, event -> session().tames.tame(event));
        // Mob effects (Applicable also fires on the client, for the effects the server sends).
        bus.addListener(MobEffectEvent.Applicable.class, event -> {
            if (!event.getEntity().level().isClientSide()) session().effects.applicable(event);
        });
        bus.addListener(MobEffectEvent.Added.class, event -> {
            if (!event.getEntity().level().isClientSide()) session().effects.added(event);
        });
        bus.addListener(MobEffectEvent.Remove.class, event -> {
            if (!event.getEntity().level().isClientSide()) session().effects.remove(event);
        });
        bus.addListener(MobEffectEvent.Expired.class, event -> {
            if (!event.getEntity().level().isClientSide()) session().effects.expired(event);
        });
        bus.addListener(EffectParticleModificationEvent.class, event -> {
            if (!event.getEntity().level().isClientSide()) session().effects.particles(event);
        });
        // Conversions and births (a tadpole fed on the client fires Pre there too).
        bus.addListener(LivingConversionEvent.Pre.class, event -> {
            if (!event.getEntity().level().isClientSide()) session().conversions.pre(event);
        });
        bus.addListener(LivingConversionEvent.Post.class, event -> {
            if (!event.getEntity().level().isClientSide()) session().conversions.post(event);
        });
        bus.addListener(BabyEntitySpawnEvent.class, event -> session().conversions.babySpawn(event));
        bus.addListener(EntityMountEvent.class, event -> {
            if (!event.getLevel().isClientSide()) session().survival.mount(event);
        });
        // Neighbor updates also fire on the client (ClientEvents checks that); trampling and griefing only on the server.
        bus.addListener(BlockEvent.NeighborNotifyEvent.class, event -> {
            if (!event.getLevel().isClientSide()) session().blocks.neighborNotify(event);
        });
        bus.addListener(BlockEvent.FarmlandTrampleEvent.class, event -> session().blocks.farmlandTrample(event));
        bus.addListener(EntityMobGriefingEvent.class, event -> {
            session().blocks.mobGriefing(event);
            session().griefing.mobGriefing(event);
        });
        bus.addListener(LivingDestroyBlockEvent.class, event -> session().griefing.destroyBlock(event));
        // Size and EntityConstructing also fire on the client (ClientEvents checks the local player's), visibility only
        // where a mob looks for a target.
        bus.addListener(EntityEvent.Size.class, event -> session().entities.size(event));
        bus.addListener(EntityEvent.EntityConstructing.class, event -> {
            session().entities.constructing(event);
            session().players.constructing(event);
        });
        bus.addListener(LivingEvent.LivingVisibilityEvent.class, event -> session().entities.visibility(event));
        // Explosion events fire only on the server; note blocks play on both sides (ClientEvents counts the client's).
        bus.addListener(ExplosionEvent.Start.class, event -> session().levelEvents.explosionStart(event));
        bus.addListener(ExplosionEvent.Detonate.class, event -> session().levelEvents.explosionDetonate(event));
        bus.addListener(ExplosionKnockbackEvent.class, event -> session().levelEvents.explosionKnockback(event));
        bus.addListener(NoteBlockEvent.Play.class, event -> session().levelEvents.notePlay(event));
        bus.addListener(NoteBlockEvent.Change.class, event -> session().levelEvents.noteChange(event));
        // Crop growth fires only on the server.
        bus.addListener(CropGrowEvent.Pre.class, event -> session().crops.pre(event));
        bus.addListener(CropGrowEvent.Post.class, event -> session().crops.post(event));
        // Teleport events fire only on the server.
        for (Class<? extends EntityTeleportEvent> kind : List.of(EntityTeleportEvent.TeleportCommand.class, EntityTeleportEvent.SpreadPlayersCommand.class,
                EntityTeleportEvent.EnderEntity.class, EntityTeleportEvent.EnderPearl.class, EntityTeleportEvent.ItemConsumption.class)) {
            bus.addListener(kind, event -> session().teleports.teleport(event));
        }
        bus.addListener(ItemExpireEvent.class, event -> session().items.expire(event));
        bus.addListener(ItemEntityPickupEvent.Pre.class, event -> session().items.pickupPre(event));
        bus.addListener(ItemEntityPickupEvent.Post.class, event -> session().items.pickupPost(event));
        bus.addListener(ItemTossEvent.class, event -> {
            if (!event.getPlayer().level().isClientSide()) session().items.toss(event);
        });
        bus.addListener(ChunkEvent.Load.class, event -> {
            if (event.getLevel() instanceof ServerLevel) session().chunks.load(event);
        });
        bus.addListener(ChunkEvent.Unload.class, event -> {
            if (event.getLevel() instanceof ServerLevel) session().chunks.unload(event);
        });
        bus.addListener(ChunkDataEvent.Load.class, event -> session().chunks.dataLoad(event));
        bus.addListener(ChunkDataEvent.Save.class, event -> session().chunks.dataSave(event));
        bus.addListener(ChunkTicketLevelUpdatedEvent.class, event -> session().chunks.ticketLevelUpdated(event));
        bus.addListener(ChunkWatchEvent.Watch.class, event -> session().chunks.watch(event));
        bus.addListener(ChunkWatchEvent.Sent.class, event -> session().chunks.watch(event));
        bus.addListener(ChunkWatchEvent.UnWatch.class, event -> session().chunks.watch(event));
        bus.addListener(PlayerTickEvent.Pre.class, event -> {
            if (event.getEntity() instanceof ServerPlayer) session().playerTick(true);
        });
        bus.addListener(PlayerTickEvent.Post.class, event -> {
            if (event.getEntity() instanceof ServerPlayer) session().playerTick(false);
        });
        // Players: the scenario runs on the server; client-side copies of these events are ClientEvents' business.
        bus.addListener(PlayerEvent.PlayerLoggedInEvent.class, event -> session().players.loggedIn(event));
        bus.addListener(PlayerEvent.PlayerLoggedOutEvent.class, event -> session().players.loggedOut(event));
        bus.addListener(PlayerInteractEvent.LeftClickBlock.class, event -> {
            if (!event.getLevel().isClientSide()) session().players.leftClickBlock(event);
        });
        bus.addListener(BreakBlockEvent.class, event -> {
            if (!event.getLevel().isClientSide()) session().players.breakBlock(event);
        });
        bus.addListener(PlayerInteractEvent.RightClickBlock.class, event -> {
            if (!event.getLevel().isClientSide()) session().players.rightClickBlock(event);
        });
        bus.addListener(PlayerInteractEvent.RightClickItem.class, event -> {
            if (!event.getLevel().isClientSide()) session().players.rightClickItem(event);
        });
        bus.addListener(PlayerInteractEvent.EntityInteract.class, event -> {
            if (!event.getLevel().isClientSide()) session().players.entityInteract(event);
        });
        bus.addListener(AttackEntityEvent.class, event -> {
            if (!event.getEntity().level().isClientSide()) session().players.attack(event);
        });
        bus.addListener(EntityTravelToDimensionEvent.class, event -> {
            session().players.travelToDimension(event);
            session().living.travelToDimension(event);
        });
        bus.addListener(PlayerEvent.PlayerChangedDimensionEvent.class, event -> session().players.changedDimension(event));
        bus.addListener(PlayerRespawnPositionEvent.class, event -> session().players.respawnPosition(event));
        bus.addListener(PlayerEvent.Clone.class, event -> session().players.cloned(event));
        bus.addListener(PlayerEvent.PlayerRespawnEvent.class, event -> session().players.respawned(event));
        bus.addListener(ServerStoppingEvent.class, event -> session().stopping(event));
        bus.addListener(GameShuttingDownEvent.class, event -> {
            // Also fired when the client closes; only a running server has a session to record it in.
            Session current = session;
            if (current != null) current.trace("shuttingDown");
        });
        bus.addListener(LevelEvent.Save.class, event -> {
            if (event.getLevel() instanceof ServerLevel level) session().trace("save:" + name(level));
        });
        bus.addListener(LevelEvent.Unload.class, event -> {
            if (event.getLevel() instanceof ServerLevel level) session().trace("unload:" + name(level));
        });
        bus.addListener(ServerStoppedEvent.class, event -> {
            session().stopped(event);
            session = null;
        });
    }

    // Commands are built when the server's resources load, before the server exists, so the session starts there.
    private static synchronized Session session() {
        if (session == null) session = new Session();
        return session;
    }

    private static String name(Level level) {
        return level.dimension().identifier().getPath();
    }

    private static final class Session {
        private final Checks checks = new Checks("server checks");
        private final Checks playerChecks = new Checks("server player checks");
        // Join and leave events per entity. Identity maps: Entity.equals compares entity ids, and a respawned player takes
        // over the old player's id.
        private final Map<Entity, Integer> joins = Collections.synchronizedMap(new IdentityHashMap<>());
        private final Map<Entity, Integer> leaves = Collections.synchronizedMap(new IdentityHashMap<>());
        private final AtomicInteger joinedNew = new AtomicInteger();
        private final AtomicInteger joinedFromDisk = new AtomicInteger();
        private Entity deniedJoin;
        private final List<LivingEntity> deaths = new CopyOnWriteArrayList<>();
        private final Map<Entity, List<ItemEntity>> drops = Collections.synchronizedMap(new IdentityHashMap<>());
        private Entity deathCanceled;
        private Entity dropsCanceled;
        private final DamageScenario damage = new DamageScenario(checks);
        private final LivingScenario living = new LivingScenario(checks);
        private final ItemScenario items = new ItemScenario(checks, playerChecks, joins);
        private final ChunkScenario chunks = new ChunkScenario(checks, playerChecks);
        private final DestroyItemScenario destroyedItems = new DestroyItemScenario(playerChecks);
        private final ProjectileScenario projectiles = new ProjectileScenario(checks, playerChecks);
        private final TeleportScenario teleports = new TeleportScenario(checks, playerChecks);
        private final SurvivalScenario survival = new SurvivalScenario(checks, playerChecks);
        private final SpawnScenario spawns = new SpawnScenario(checks, playerChecks, joins);
        private final TameScenario tames = new TameScenario(playerChecks);
        private final EffectScenario effects = new EffectScenario(checks);
        private final ConversionScenario conversions = new ConversionScenario(checks);
        private final BlockScenario blocks = new BlockScenario(checks, playerChecks);
        private final GriefingScenario griefing = new GriefingScenario(checks);
        private final CropScenario crops = new CropScenario(checks);
        private final EntityScenario entities = new EntityScenario(checks);
        private final LevelScenario levelEvents = new LevelScenario(checks, playerChecks);
        private final PlayerScenario players = new PlayerScenario(playerChecks, joins, leaves, damage, items, destroyedItems, projectiles, teleports, survival, spawns,
                tames, blocks, levelEvents);
        private final List<String> trace = new CopyOnWriteArrayList<>();
        private MinecraftServer server;
        private boolean newWorld;

        private Commands.CommandSelection commandSelection;
        private boolean commandBuildContext;
        private final List<String> commandInputs = new CopyOnWriteArrayList<>();
        private final AtomicBoolean ran = new AtomicBoolean();
        private final AtomicBoolean canceledRan = new AtomicBoolean();
        private final AtomicBoolean originalRan = new AtomicBoolean();
        private final AtomicBoolean replacedRan = new AtomicBoolean();

        private ModConfig serverConfig;

        private int serverTicksPre;
        private int serverTicksPost;
        private boolean serverTicksAlternate = true;
        private boolean serverTickHasTime;
        private boolean serverTickServer;
        private final Map<ResourceKey<Level>, int[]> levelTicks = new ConcurrentHashMap<>();
        private boolean levelTicksAlternate = true;

        private ArmorStand ticking;
        private ArmorStand frozen;
        private ArmorStand rider;
        private double frozenY;
        private final Map<Entity, int[]> entityTicks = Collections.synchronizedMap(new IdentityHashMap<>());

        // The server tick the projectile, teleport, survival and spawn scenarios ran at, -1 until then.
        private int scenariosTick = -1;

        private final AtomicInteger playerTicksPre = new AtomicInteger();
        private final AtomicInteger playerTicksPost = new AtomicInteger();
        private boolean playerTicksAlternate = true;

        void trace(String token) {
            trace.add(token);
        }

        void entityJoined(EntityJoinLevelEvent event) {
            joins.merge(event.getEntity(), 1, Integer::sum);
            (event.loadedFromDisk() ? joinedFromDisk : joinedNew).incrementAndGet();
            if (event.getEntity() == deniedJoin) event.setCanceled(true);
        }

        void livingDeath(LivingDeathEvent event) {
            deaths.add(event.getEntity());
            if (event.getEntity() == deathCanceled) {
                // As mods do: an entity that doesn't die needs some health back.
                event.setCanceled(true);
                event.getEntity().setHealth(1.0F);
            }
            players.livingDeath(event);
        }

        void livingDrops(LivingDropsEvent event) {
            drops.put(event.getEntity(), List.copyOf(event.getDrops()));
            if (event.getEntity() == dropsCanceled) event.setCanceled(true);
            players.livingDrops(event);
        }

        private int count(Map<Entity, Integer> events, Entity entity) {
            return events.getOrDefault(entity, 0);
        }

        void serverConfigLoaded(ModConfig config) {
            serverConfig = config;
            trace("config");
        }

        void registerCommands(RegisterCommandsEvent event) {
            trace("commands");
            commandSelection = event.getCommandSelection();
            commandBuildContext = event.getBuildContext() != null;
            CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
            dispatcher.register(Commands.literal(PROBE)
                    .then(Commands.literal("run").executes(context -> run(ran)))
                    .then(Commands.literal("cancelme").executes(context -> run(canceledRan)))
                    .then(Commands.literal("original").executes(context -> run(originalRan)))
                    .then(Commands.literal("replaced").executes(context -> run(replacedRan))));
        }

        private static int run(AtomicBoolean flag) {
            flag.set(true);
            return 1;
        }

        void command(CommandEvent event) {
            ParseResults<CommandSourceStack> parse = event.getParseResults();
            String input = parse.getReader().getString();
            if (!input.startsWith(PROBE)) return;
            commandInputs.add(input);
            if (input.endsWith(" cancelme")) {
                event.setCanceled(true);
            } else if (input.endsWith(" original")) {
                CommandSourceStack source = parse.getContext().getSource();
                event.setParseResults(source.getServer().getCommands().getDispatcher().parse(PROBE + " replaced", source));
            }
        }

        void aboutToStart(ServerAboutToStartEvent event) {
            trace("aboutToStart");
            server = event.getServer();
            newWorld = !server.getWorldData().overworldData().isInitialized();
            checks.check("ServerLifecycleHooks.getCurrentServer() is set by server about to start", ServerLifecycleHooks.getCurrentServer() == server);
            checks.check("no levels exist yet at server about to start", !server.getAllLevels().iterator().hasNext());
            checks.check("the SERVER config is loaded before server about to start", serverConfig != null && SERVER_CONFIG.loaded != null);
            checks.check("the SERVER config lives in the config folder", serverConfig == null ? null : serverConfig.getFullPath(),
                    FMLPaths.CONFIGDIR.get().resolve(EventsMod.MOD_ID + "-server.toml"));
            checks.check("the world gets a serverconfig folder for overrides",
                    Files.isRegularFile(server.getWorldPath(new LevelResource("serverconfig")).resolve("readme.txt")));
        }

        void starting(ServerStartingEvent event) {
            trace("starting");
            checks.check("server starting gets the same server", event.getServer() == server);
        }

        void started(ServerStartedEvent event) {
            trace("started");
            checks.check("RegisterCommandsEvent fired for this server's command selection", commandSelection,
                    server.isDedicatedServer() ? Commands.CommandSelection.DEDICATED : Commands.CommandSelection.INTEGRATED);
            checks.check("RegisterCommandsEvent has a build context", commandBuildContext);

            Commands commands = server.getCommands();
            CommandSourceStack source = server.createCommandSourceStack();
            commands.performPrefixedCommand(source, PROBE + " run");
            checks.check("a command registered in RegisterCommandsEvent runs", ran.get());
            checks.check("CommandEvent sees the command before it runs", commandInputs.contains(PROBE + " run"));
            commands.performPrefixedCommand(source, PROBE + " cancelme");
            checks.check("canceling CommandEvent stops the command", commandInputs.contains(PROBE + " cancelme") && !canceledRan.get());
            commands.performPrefixedCommand(source, PROBE + " original");
            checks.check("CommandEvent can replace the parse results", replacedRan.get() && !originalRan.get());

            // Three armor stands in a chunk that stays loaded: one ticks normally, one has its ticks canceled, and one
            // rides the first, so it ticks as a passenger.
            ServerLevel overworld = server.overworld();
            overworld.setChunkForced(0, 0, true);
            overworld.getChunk(0, 0);
            chunks.started(overworld, newWorld);
            double y = overworld.getMaxY() - 8;
            ticking = spawnArmorStand(overworld, 4.5, y);
            frozen = spawnArmorStand(overworld, 9.5, y);
            frozenY = y;
            rider = spawnArmorStand(overworld, 4.5, y);
            checks.check("an entity can be made a passenger", rider.startRiding(ticking, true, false));
            checks.check("EntityJoinLevelEvent fires once for each new entity", count(joins, ticking) == 1 && count(joins, frozen) == 1 && count(joins, rider) == 1);
            ArmorStand denied = EntityTypes.ARMOR_STAND.create(overworld, EntitySpawnReason.COMMAND);
            denied.setPos(14.5, y, 14.5);
            deniedJoin = denied;
            boolean added = overworld.addFreshEntity(denied);
            checks.check("canceling EntityJoinLevelEvent keeps the entity out of the level",
                    !added && count(joins, denied) == 1 && overworld.getEntity(denied.getUUID()) == null);

            // Chickens in the same chunk: one dies and drops its loot, one has its death canceled, one its drops.
            Chicken dying = spawnChicken(overworld, y);
            Chicken surviving = spawnChicken(overworld, y);
            Chicken dropless = spawnChicken(overworld, y);
            deathCanceled = surviving;
            dropsCanceled = dropless;
            dying.kill(overworld);
            surviving.kill(overworld);
            dropless.kill(overworld);
            // The chunk only becomes visible next tick, so getEntity can't see these yet; joining the level tells instead.
            List<ItemEntity> dropped = drops.getOrDefault(dying, List.of());
            checks.check("LivingDeathEvent and LivingDropsEvent fire, and the drops are added to the level",
                    deaths.contains(dying) && !dropped.isEmpty() && dropped.stream().allMatch(item -> count(joins, item) == 1));
            checks.check("canceling LivingDeathEvent keeps the entity alive, without drops", deaths.contains(surviving) && surviving.isAlive() && !drops.containsKey(surviving));
            List<ItemEntity> withheld = drops.getOrDefault(dropless, List.of());
            checks.check("canceling LivingDropsEvent keeps the drops out of the level",
                    !withheld.isEmpty() && withheld.stream().allMatch(item -> count(joins, item) == 0));
            dropped.forEach(Entity::discard);
            surviving.discard();

            damage.run(overworld, 12.5, y, 9.5);
            living.run(overworld, 6.5, y, 12.5);
            items.run(overworld, 2.5, y, 12.5);
            effects.run(overworld);
            conversions.run(overworld, new Vec3(2.5, y, 6.5));
            blocks.run(overworld, BlockPos.containing(8, y - 24, 8));
            griefing.run(overworld, BlockPos.containing(8, y - 36, 8));
            crops.run(overworld, BlockPos.containing(8, y - 48, 8));
            entities.run(overworld);
            levelEvents.run(overworld, BlockPos.containing(8, y - 60, 8));
        }

        private static Chicken spawnChicken(ServerLevel level, double y) {
            Chicken chicken = EntityTypes.CHICKEN.create(level, EntitySpawnReason.COMMAND);
            chicken.setPos(12.5, y, 4.5);
            level.addFreshEntity(chicken);
            return chicken;
        }

        private static ArmorStand spawnArmorStand(ServerLevel level, double x, double y) {
            ArmorStand stand = EntityTypes.ARMOR_STAND.create(level, EntitySpawnReason.COMMAND);
            stand.setPos(x, y, x);
            level.addFreshEntity(stand);
            return stand;
        }

        void serverTick(ServerTickEvent event, boolean pre) {
            if (pre) {
                if (serverTicksPre == 0) {
                    trace("tick");
                    serverTickServer = event.getServer() == server;
                    event.hasTime();
                    serverTickHasTime = true;
                }
                if (serverTicksPre != serverTicksPost) serverTicksAlternate = false;
                serverTicksPre++;
            } else {
                serverTicksPost++;
                levelEvents.tick();
                if (serverTicksPre != serverTicksPost) serverTicksAlternate = false;
                // Once the forced chunk's entities are visible (to projectiles' hit checks), a few ticks after server
                // started, or more in a new world. Far below the entities spawned at server started, which fall.
                ServerLevel overworld = server.overworld();
                if (scenariosTick < 0 && overworld.getEntity(ticking.getUUID()) == ticking) {
                    scenariosTick = serverTicksPost;
                    LOGGER.info("[events] the forced chunk's entities are visible at server tick {}", scenariosTick);
                    int y = overworld.getMaxY() - 48;
                    projectiles.run(overworld, new Vec3(8.5, y, 4.5));
                    teleports.run(overworld, new BlockPos(8, y, 8));
                    survival.run(overworld, new BlockPos(4, y - 8, 4));
                    spawns.run(overworld, new BlockPos(8, y - 16, 8));
                    levelEvents.runExplosions(overworld, new BlockPos(8, y - 32, 8));
                }
            }
        }

        void levelTick(ServerLevel level, boolean pre) {
            int[] counts = levelTicks.computeIfAbsent(level.dimension(), key -> new int[2]);
            if (pre) {
                if (counts[0] != counts[1]) levelTicksAlternate = false;
                counts[0]++;
            } else {
                counts[1]++;
                if (counts[0] != counts[1]) levelTicksAlternate = false;
            }
        }

        void entityTickPre(EntityTickEvent.Pre event) {
            Entity entity = event.getEntity();
            if (entity != ticking && entity != frozen && entity != rider) return;
            entityTicks.computeIfAbsent(entity, key -> new int[2])[0]++;
            if (entity == frozen) event.setCanceled(true);
        }

        void entityTickPost(Entity entity) {
            if (entity != ticking && entity != frozen && entity != rider) return;
            entityTicks.computeIfAbsent(entity, key -> new int[2])[1]++;
        }

        void playerTick(boolean pre) {
            if (pre) {
                if (playerTicksPre.get() != playerTicksPost.get()) playerTicksAlternate = false;
                playerTicksPre.incrementAndGet();
            } else {
                playerTicksPost.incrementAndGet();
                if (playerTicksPre.get() != playerTicksPost.get()) playerTicksAlternate = false;
            }
        }

        void stopping(ServerStoppingEvent event) {
            trace("stopping");
            levelEvents.stopping();
            checks.check("server ticks fired, Pre and Post in turn", serverTicksPre > 0 && serverTicksAlternate && serverTicksPre == serverTicksPost);
            checks.check("server tick events carry the server and its time budget", serverTickServer && serverTickHasTime);
            int[] overworldTicks = levelTicks.getOrDefault(Level.OVERWORLD, new int[2]);
            checks.check("level ticks fired for every level, Pre and Post in turn",
                    levelTicks.size() >= 3 && levelTicksAlternate && overworldTicks[0] > 0 && overworldTicks[0] == overworldTicks[1]);
            checks.check("each server tick ticks the overworld once", overworldTicks[0], serverTicksPre);

            int[] tickingTicks = entityTicks.getOrDefault(ticking, new int[2]);
            int[] frozenTicks = entityTicks.getOrDefault(frozen, new int[2]);
            int[] riderTicks = entityTicks.getOrDefault(rider, new int[2]);
            checks.check("entity ticks fired, Pre and Post", tickingTicks[0] > 0 && tickingTicks[0] == tickingTicks[1]);
            checks.check("canceling EntityTickEvent.Pre skips the tick", frozenTicks[0] > 0 && frozenTicks[1] == 0 && frozen.getY() == frozenY);
            checks.check("a passenger's ticks fire the entity tick events", rider.isPassenger() && riderTicks[0] > 0 && riderTicks[0] == riderTicks[1]);
            checks.check("the projectile, teleport, survival and spawn scenarios ran", scenariosTick > 0);

            // Leave the world as it was.
            living.finish();
            items.finish(server.overworld());
            ticking.discard();
            frozen.discard();
            rider.discard();
            server.overworld().setChunkForced(0, 0, false);
            checks.check("EntityLeaveLevelEvent fires once when an entity is removed", count(leaves, ticking) == 1 && count(leaves, frozen) == 1 && count(leaves, rider) == 1);
            LOGGER.info("[events] entities joined server levels: {} new, {} loaded from disk", joinedNew.get(), joinedFromDisk.get());
            spawns.logChunkGeneration();
        }

        void stopped(ServerStoppedEvent event) {
            trace("stopped");
            LOGGER.info("[events] server event order: {}", trace);
            checks.check("ServerLifecycleHooks.getCurrentServer() is still set during server stopped", ServerLifecycleHooks.getCurrentServer() == server);

            int aboutToStart = trace.indexOf("aboutToStart");
            int starting = trace.indexOf("starting");
            int started = trace.indexOf("started");
            int stopping = trace.indexOf("stopping");
            int stopped = trace.indexOf("stopped");
            List<String> loads = trace.stream().filter(token -> token.startsWith("load:")).toList();
            List<String> unloads = trace.stream().filter(token -> token.startsWith("unload:")).toList();
            checks.check("commands and the SERVER config come before server about to start",
                    trace.indexOf("commands") >= 0 && trace.indexOf("commands") < aboutToStart && trace.indexOf("config") < aboutToStart);
            checks.check("the overworld loads first, after server about to start", !loads.isEmpty() && loads.get(0).equals("load:overworld")
                    && trace.indexOf("load:overworld") > aboutToStart);
            checks.check("every level loads before server starting", loads.size() >= 3 && trace.lastIndexOf(loads.get(loads.size() - 1)) < starting);
            checks.check(newWorld ? "a new world fires CreateSpawnPosition once, after the overworld loads" : "an existing world doesn't fire CreateSpawnPosition",
                    newWorld ? trace.indexOf("spawn") > trace.indexOf("load:overworld") && trace.indexOf("spawn") == trace.lastIndexOf("spawn")
                            : !trace.contains("spawn"));
            checks.check("server starting, started, the first tick and stopping come in order",
                    starting < started && started < trace.indexOf("tick") && trace.indexOf("tick") < stopping);
            checks.check("every level unloads between server stopping and stopped",
                    unloads.size() == loads.size() && trace.indexOf(unloads.get(0)) > stopping && trace.lastIndexOf(unloads.get(unloads.size() - 1)) < stopped);
            checks.check("each level saves before it unloads", unloads.stream().allMatch(unload -> {
                int save = trace.lastIndexOf("save:" + unload.substring("unload:".length()));
                return save > stopping && save < trace.indexOf(unload);
            }));
            if (server.isDedicatedServer()) {
                checks.check("the dedicated server fires GameShuttingDownEvent after server stopping, before levels unload",
                        trace.indexOf("shuttingDown") > stopping && trace.indexOf("shuttingDown") < trace.indexOf(unloads.get(0)));
            }
            checks.check("server stopped comes last", stopped == trace.size() - 1);
            chunks.stopped();
            checks.summarize();

            if (players.played()) {
                chunks.finishPlayer();
                playerChecks.check("server player ticks fired, Pre and Post in turn",
                        playerTicksPre.get() > 0 && playerTicksAlternate && playerTicksPre.get() == playerTicksPost.get());
                players.finish();
                playerChecks.summarize();
            }
        }
    }
}
