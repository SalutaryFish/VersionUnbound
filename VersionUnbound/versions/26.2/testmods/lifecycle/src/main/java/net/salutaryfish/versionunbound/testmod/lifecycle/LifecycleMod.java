package net.salutaryfish.versionunbound.testmod.lifecycle;

import com.mojang.logging.LogUtils;
import java.nio.file.Files;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLDedicatedServerSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.fml.event.lifecycle.InterModEnqueueEvent;
import net.neoforged.fml.event.lifecycle.InterModProcessEvent;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * Checks when mod loading happens: the order of NeoForge's loading steps, and where they fall relative to Fabric's
 * own mod initialization and Fabric API's registry freeze. Also checks the game event bus and config loading. Each
 * check logs PASS or FAIL; load complete logs a summary line.
 */
@Mod(LifecycleMod.MOD_ID)
public class LifecycleMod {
    public static final String MOD_ID = "lifecycle";
    /** Set by the fabricprobe testmod when Fabric runs its mods' initializers. */
    private static final String FABRIC_INITIALIZED = "versionunbound.testprobe.fabricInitialized";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final AtomicInteger CHECKS = new AtomicInteger();
    private static final List<String> FAILURES = new CopyOnWriteArrayList<>();
    /** The loading steps this mod saw, in order. */
    private static final List<String> STEPS = new CopyOnWriteArrayList<>();

    private final Dist dist;
    private final ProbeConfigSpec configSpec = new ProbeConfigSpec();

    public LifecycleMod(IEventBus modBus, ModContainer container, Dist dist) {
        this.dist = dist;
        STEPS.add("construct");
        check("constructed on a mod loading worker", Thread.currentThread().getName().startsWith("modloading-worker-"));
        check("constructed before Fabric initializes its mods", !Boolean.getBoolean(FABRIC_INITIALIZED));

        container.registerConfig(ModConfig.Type.COMMON, configSpec);
        modBus.addListener(ModConfigEvent.Loading.class, this::configLoading);
        modBus.addListener(FMLCommonSetupEvent.class, this::commonSetup);
        modBus.addListener(FMLClientSetupEvent.class, event -> sidedSetup("client setup"));
        modBus.addListener(FMLDedicatedServerSetupEvent.class, event -> sidedSetup("dedicated server setup"));
        modBus.addListener(InterModEnqueueEvent.class, event -> STEPS.add("enqueue IMC"));
        modBus.addListener(InterModProcessEvent.class, event -> STEPS.add("process IMC"));
        modBus.addListener(FMLLoadCompleteEvent.class, this::loadComplete);
        NeoForge.EVENT_BUS.addListener(ProbeEvent.class, event -> event.heardBy.add("addListener"));
    }

    private void configLoading(ModConfigEvent.Loading event) {
        STEPS.add("config loading");
        var loaded = configSpec.loaded;
        check("config handed to the spec", loaded != null && Boolean.TRUE.equals(loaded.config().get("probe")));
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        STEPS.add("common setup");
        check("common setup after Fabric initializes its mods", Boolean.getBoolean(FABRIC_INITIALIZED));
        check("registries frozen by common setup", registriesFrozen());
        check("config file written", Files.isRegularFile(FMLPaths.CONFIGDIR.get().resolve(MOD_ID + "-common.toml")));

        // The game bus was started at the end of construction, so posting works now.
        check("bindings provider gives NeoForge's game bus", FMLLoader.getCurrent().getBindings().getGameBus() == NeoForge.EVENT_BUS);
        var probe = NeoForge.EVENT_BUS.post(new ProbeEvent());
        check("game bus reaches addListener and @EventBusSubscriber once each",
                probe.heardBy.size() == 2 && Set.copyOf(probe.heardBy).equals(Set.of("addListener", "@EventBusSubscriber")));

        event.enqueueWork(() -> check("enqueued work runs on the sync worker", Thread.currentThread().getName().equals("modloading-sync-worker")));
    }

    private void sidedSetup(String step) {
        STEPS.add(step);
        if (dist.isClient()) {
            check("client setup runs once the window, options, resources and renderers exist", ClientChecks.clientReady());
        }
    }

    private void loadComplete(FMLLoadCompleteEvent event) {
        STEPS.add("load complete");
        check("@EventBusSubscriber got common setup on the mod bus", GameBusSubscriber.SAW_COMMON_SETUP.get());
        String sidedSetup = dist.isClient() ? "client setup" : "dedicated server setup";
        check("loading steps in NeoForge's order", List.copyOf(STEPS),
                List.of("construct", "config loading", "common setup", sidedSetup, "enqueue IMC", "process IMC", "load complete"));

        if (FAILURES.isEmpty()) {
            LOGGER.info("[lifecycle] all {} checks passed", CHECKS.get());
        } else {
            LOGGER.error("[lifecycle] {} of {} checks FAILED: {}", FAILURES.size(), CHECKS.get(), FAILURES);
        }
    }

    /** Frozen registries refuse new entries. On NeoForge, setup always runs after the freeze. */
    private static boolean registriesFrozen() {
        var id = Identifier.fromNamespaceAndPath(MOD_ID, "frozen_probe");
        try {
            Registry.register(BuiltInRegistries.CUSTOM_STAT, id, id);
            return false;
        } catch (IllegalStateException e) {
            return true;
        }
    }

    private static void check(String what, boolean passed) {
        CHECKS.incrementAndGet();
        if (passed) {
            LOGGER.info("[lifecycle] PASS {}", what);
        } else {
            FAILURES.add(what);
            LOGGER.error("[lifecycle] FAIL {}", what);
        }
    }

    private static void check(String what, Object actual, Object expected) {
        CHECKS.incrementAndGet();
        if (Objects.equals(actual, expected)) {
            LOGGER.info("[lifecycle] PASS {}", what);
        } else {
            FAILURES.add(what);
            LOGGER.error("[lifecycle] FAIL {}: expected {}, got {}", what, expected, actual);
        }
    }
}
