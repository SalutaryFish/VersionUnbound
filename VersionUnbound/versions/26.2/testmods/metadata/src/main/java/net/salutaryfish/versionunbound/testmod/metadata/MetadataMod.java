package net.salutaryfish.versionunbound.testmod.metadata;

import com.mojang.logging.LogUtils;
import java.lang.annotation.ElementType;
import java.nio.file.Files;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.IExtensionPoint;
import net.neoforged.fml.InterModComms;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.event.lifecycle.InterModEnqueueEvent;
import net.neoforged.fml.event.lifecycle.InterModProcessEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforgespi.language.IModInfo;
import org.objectweb.asm.Type;
import org.slf4j.Logger;

/**
 * Checks FML's metadata and context APIs: mod info, the mod list, the loading context, environment and paths,
 * extension points, annotation scan data and inter-mod messages. Each check logs PASS or FAIL; the last event
 * logs a summary line.
 */
@Mod(MetadataMod.MOD_ID)
public class MetadataMod {
    public static final String MOD_ID = "metadata";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final AtomicInteger CHECKS = new AtomicInteger();
    private static final List<String> FAILURES = new CopyOnWriteArrayList<>();

    public interface Greeting extends IExtensionPoint {
        String text();
    }

    public MetadataMod(IEventBus modBus, ModContainer container, Dist dist) {
        // Loading context during construction.
        check("active container during construction", ModLoadingContext.get().getActiveContainer() == container);
        check("active namespace", ModLoadingContext.get().getActiveNamespace(), MOD_ID);

        // Metadata from neoforge.mods.toml.
        IModInfo info = container.getModInfo();
        check("modId", info.getModId(), MOD_ID);
        check("namespace", container.getNamespace(), MOD_ID);
        check("displayName", info.getDisplayName(), "VersionUnbound Metadata");
        check("version", info.getVersion().toString(), "1.2.3");
        check("description starts", info.getDescription().startsWith("Checks FML's mod metadata"));
        check("mod property", info.getModProperties().get("answer") instanceof Number n && n.intValue() == 42);
        check("raw config element", info.getConfig().<String>getConfigElement("displayName").orElse(null), "VersionUnbound Metadata");
        check("license", info.getOwningFile().getLicense(), "LGPL-2.1-only");
        check("owning file mods", info.getOwningFile().getMods().stream().map(IModInfo::getModId).toList(), List.of(MOD_ID));
        var filePath = info.getOwningFile().getFile().getFilePath();
        check("file path is the jar", filePath.getFileName().toString().endsWith(".jar") && Files.isRegularFile(filePath));
        check("dependencies", info.getDependencies().stream().map(IModInfo.ModVersion::getModId).toList(),
                List.of("minecraft", "neoforge", "hello", "no_such_mod"));
        check("hello dependency ordering", info.getDependencies().get(2).getOrdering(), IModInfo.Ordering.AFTER);
        check("optional dependency type", info.getDependencies().get(3).getType(), IModInfo.DependencyType.OPTIONAL);

        // The mod list.
        ModList modList = ModList.get();
        for (String modId : List.of("minecraft", "neoforge", "hello", MOD_ID)) {
            check("isLoaded " + modId, modList.isLoaded(modId));
        }
        check("not loaded: no_such_mod", !modList.isLoaded("no_such_mod"));
        check("container lookup is ours", modList.getModContainerById(MOD_ID).orElse(null) == container);
        check("hello version", modList.getModContainerById("hello").map(c -> c.getModInfo().getVersion().toString()).orElse(null), "0.1.0");
        check("hello mod file", modList.getModFileById("hello") != null && modList.getModFileById("hello").getFile().getFileName().startsWith("hello"));
        check("neoforge version", modList.getModContainerById("neoforge").map(c -> c.getModInfo().getVersion().toString()).orElse(null), "26.2.0.93");
        check("minecraft version", modList.getModContainerById("minecraft").map(c -> c.getModInfo().getVersion().toString()).orElse(null), "26.2");
        var order = modList.getMods().stream().map(IModInfo::getModId).toList();
        check("hello sorted before metadata", order.indexOf("hello") >= 0 && order.indexOf("hello") < order.indexOf(MOD_ID));
        LOGGER.info("[metadata] mod list ({} mods, in load order): {}", modList.size(), order);

        // Environment, loader and paths.
        check("FMLEnvironment dist", FMLEnvironment.getDist(), dist);
        check("FMLLoader dist", FMLLoader.getCurrent().getDist(), dist);
        check("loading mod list", FMLLoader.getCurrent().getLoadingModList().getModFileById(MOD_ID) != null);
        check("mod file by class", FMLLoader.getCurrent().getModFileByClass(MetadataMod.class), info.getOwningFile().getFile());
        check("neoforge version info", FMLLoader.getCurrent().getVersionInfo().neoForgeVersion(), "26.2.0.93");
        check("minecraft version info", FMLLoader.getCurrent().getVersionInfo().mcVersion(), "26.2");
        check("game dir", FMLPaths.GAMEDIR.get(), FMLLoader.getCurrent().getGameDir().toAbsolutePath().normalize());
        check("config dir", FMLPaths.CONFIGDIR.get(), FMLPaths.GAMEDIR.get().resolve("config"));
        check("mods dir", FMLPaths.MODSDIR.get(), FMLPaths.GAMEDIR.get().resolve("mods"));
        check("config dir exists", Files.isDirectory(FMLPaths.CONFIGDIR.get()));
        LOGGER.info("[metadata] production: {}", FMLEnvironment.isProduction());

        // Extension points.
        container.registerExtensionPoint(Greeting.class, () -> "hello from an extension point");
        check("extension point", container.getCustomExtension(Greeting.class).map(Greeting::text).orElse(null), "hello from an extension point");

        // Annotation scan data.
        var marker = Type.getType(ScanMarker.class);
        check("scan data finds @ScanMarker", modList.getAllScanData().stream()
                .flatMap(data -> data.getAnnotations().stream())
                .anyMatch(a -> a.annotationType().equals(marker) && a.clazz().equals(Type.getType(ScanTarget.class))
                        && "found me".equals(a.annotationData().get("value"))));
        check("scan data finds @Mod", info.getOwningFile().getFile().getScanResult().getAnnotatedBy(Mod.class, ElementType.TYPE)
                .anyMatch(a -> a.clazz().equals(Type.getType(MetadataMod.class))));

        modBus.addListener(FMLCommonSetupEvent.class, event -> {
            check("active container in common setup", ModLoadingContext.get().getActiveContainer() == container);
            event.enqueueWork(() -> check("active container in deferred work", ModLoadingContext.get().getActiveContainer() == container));
        });
        modBus.addListener(InterModEnqueueEvent.class, event -> {
            check("IMC send to self", InterModComms.sendTo(MOD_ID, "greet", () -> "hi"));
            check("IMC send to missing mod is refused", !InterModComms.sendTo("no_such_mod", "greet", () -> "hi"));
        });
        modBus.addListener(InterModProcessEvent.class, event -> {
            var messages = event.getIMCStream().toList();
            check("IMC received", messages.size(), 1);
            if (messages.size() == 1) {
                var message = messages.getFirst();
                check("IMC sender", message.senderModId(), MOD_ID);
                check("IMC method", message.method(), "greet");
                check("IMC payload", message.messageSupplier().get(), "hi");
            }
            if (FAILURES.isEmpty()) {
                LOGGER.info("[metadata] all {} checks passed", CHECKS.get());
            } else {
                LOGGER.error("[metadata] {} of {} checks FAILED: {}", FAILURES.size(), CHECKS.get(), FAILURES);
            }
        });
    }

    private static void check(String what, boolean passed) {
        CHECKS.incrementAndGet();
        if (passed) {
            LOGGER.info("[metadata] PASS {}", what);
        } else {
            FAILURES.add(what);
            LOGGER.error("[metadata] FAIL {}", what);
        }
    }

    private static void check(String what, Object actual, Object expected) {
        CHECKS.incrementAndGet();
        if (Objects.equals(actual, expected)) {
            LOGGER.info("[metadata] PASS {}", what);
        } else {
            FAILURES.add(what);
            LOGGER.error("[metadata] FAIL {}: expected {}, got {}", what, expected, actual);
        }
    }
}
