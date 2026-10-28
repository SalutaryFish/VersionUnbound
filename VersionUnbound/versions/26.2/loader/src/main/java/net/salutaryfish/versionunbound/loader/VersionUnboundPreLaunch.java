package net.salutaryfish.versionunbound.loader;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs before Minecraft's classes load. Starts FML ({@link FMLLoader}), lets it read the NeoForge mod jars in
 * mods/, then puts the accepted jars on the game classpath and registers their mixin configs, so the mixins apply
 * when the target classes first load.
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
        LoadingModList mods = loader.discoverMods(minecraft.getOrigin().getPaths().getFirst(), layerA.getOrigin().getPaths().getFirst(), jars);

        // If FML rejected the mod set, it keeps only the system mods; the errors are thrown when mod loading starts.
        Set<Path> modJars = new HashSet<>(jars);
        for (var modFileInfo : mods.getModFiles()) {
            var modFile = modFileInfo.getFile();
            if (!modJars.contains(modFile.getFilePath())) continue;
            FabricLauncherBase.getLauncher().addToClassPath(modFile.getFilePath());
            LOGGER.info("Found NeoForge mod file {} with mods {}", modFile.getFileName(), modFile.getModInfos());
        }
        NeoMixinConfigs.register(mods);
    }
}
