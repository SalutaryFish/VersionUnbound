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
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.GameShuttingDownEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityInvulnerabilityCheckEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerRespawnPositionEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
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
            if (!event.getLevel().isClientSide()) session().entityJoined(event);
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
        bus.addListener(EntityTravelToDimensionEvent.class, event -> session().players.travelToDimension(event));
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
        private final PlayerScenario players = new PlayerScenario(playerChecks, joins, leaves, damage);
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
                if (serverTicksPre != serverTicksPost) serverTicksAlternate = false;
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

            // Leave the world as it was.
            living.finish();
            ticking.discard();
            frozen.discard();
            rider.discard();
            server.overworld().setChunkForced(0, 0, false);
            checks.check("EntityLeaveLevelEvent fires once when an entity is removed", count(leaves, ticking) == 1 && count(leaves, frozen) == 1 && count(leaves, rider) == 1);
            LOGGER.info("[events] entities joined server levels: {} new, {} loaded from disk", joinedNew.get(), joinedFromDisk.get());
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
            checks.summarize();

            if (players.played()) {
                playerChecks.check("server player ticks fired, Pre and Post in turn",
                        playerTicksPre.get() > 0 && playerTicksAlternate && playerTicksPre.get() == playerTicksPost.get());
                players.finish();
                playerChecks.summarize();
            }
        }
    }
}
