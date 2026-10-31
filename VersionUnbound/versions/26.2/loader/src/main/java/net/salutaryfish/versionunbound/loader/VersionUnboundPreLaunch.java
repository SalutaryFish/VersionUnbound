package net.salutaryfish.versionunbound.loader;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import net.fabricmc.loader.api.metadata.CustomValue;
import net.fabricmc.loader.impl.launch.FabricLauncherBase;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.fml.loading.VersionInfo;
import net.neoforged.fml.loading.moddiscovery.ModFile;
import net.salutaryfish.versionunbound.upgrade.v1_21_1.Upgrade1211;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs before Minecraft's classes load. Starts FML ({@link FMLLoader}), upgrades the NeoForge 1.21.1 jars in mods/
 * to the 26.2 API (Layer B, {@link Upgrade1211}), lets FML read the jars, then puts the accepted jars on the game
 * classpath, applies their access transformers and registers their mixin configs, so both apply when the target
 * classes first load. Last, FML starts scanning the mods for annotations.
 */
public class VersionUnboundPreLaunch implements PreLaunchEntrypoint {
    private static final Logger LOGGER = LoggerFactory.getLogger("VersionUnbound");
    private static final String LAYER_A_MOD_ID = "versionunbound_neoforge";

    @Override
    public void onPreLaunch() {
        FabricLoader fabric = FabricLoader.getInstance();
        ModContainer minecraft = fabric.getModContainer("minecraft").orElseThrow();
        ModContainer layerA = fabric.getModContainer(LAYER_A_MOD_ID).orElseThrow();
        CustomValue.CvObject implemented = layerA.getMetadata().getCustomValue("versionunbound:implements").getAsObject();
        var versions = new VersionInfo(
                implemented.get("neoforge").getAsString(),
                minecraft.getMetadata().getVersion().getFriendlyString(),
                implemented.get("neoform").getAsString());

        var loader = FMLLoader.create(
                FabricLauncherBase.getLauncher().getTargetClassLoader(),
                fabric.getEnvironmentType() == EnvType.CLIENT ? Dist.CLIENT : Dist.DEDICATED_SERVER,
                !fabric.isDevelopmentEnvironment(),
                fabric.getGameDir(),
                versions);

        List<Path> jars;
        try {
            jars = NeoModDiscovery.findModJars(FMLPaths.MODSDIR.get());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to scan " + FMLPaths.MODSDIR.get() + " for NeoForge mods", e);
        }
        // Layer B: 1.21.1 jars are swapped for their upgrades to the 26.2 API before FML reads them. The supertypes it
        // needs come from the game's class files, read as resources: nothing loads yet.
        ClassLoader game = FabricLauncherBase.getLauncher().getTargetClassLoader();
        jars = Upgrade1211.upgrade(jars, fabric.getGameDir().resolve(".cache/versionunbound"), versions.mcVersion(), game::getResourceAsStream);
        LoadingModList mods = loader.discoverMods(minecraft.getOrigin().getPaths().getFirst(), layerA.getOrigin().getPaths(), jars);

        // If FML rejected the mod set, it keeps only the system mods; the errors are thrown when mod loading starts.
        Set<Path> modJars = new HashSet<>(jars);
        List<ModFile> added = new ArrayList<>();
        for (var modFileInfo : mods.getModFiles()) {
            var modFile = modFileInfo.getFile();
            if (!modJars.contains(modFile.getFilePath())) continue;
            FabricLauncherBase.getLauncher().addToClassPath(modFile.getFilePath());
            added.add(modFile);
            LOGGER.info("Found NeoForge mod file {} with mods {}", modFile.getFileName(), modFile.getModInfos());
        }
        // Before any of Minecraft's classes load, as FML's AT engine is in place before the game's classes are.
        ModAccessTransformers.apply(added);
        NeoMixinConfigs.register(mods);
        // Only now: the scan threads load classes, and with them Mixin would take up configs before they're tagged.
        loader.startModScan();
        // And finish it here, before Knot loads the game's classes. A scan thread loading a class (through Knot, which
        // holds that class's lock and then waits for Mixin's transformer lock) deadlocks with the main thread applying
        // mixins (which holds the transformer lock and can then load a class through Knot): seen with MixinExtras
        // loading a class while the scanner loaded ModClassVisitor. FML overlaps the scan with the game's bootstrap;
        // ModLoader's own waitForScanToComplete then returns at once.
        loader.backgroundScanHandler.waitForScanToComplete(() -> {});
    }
}
