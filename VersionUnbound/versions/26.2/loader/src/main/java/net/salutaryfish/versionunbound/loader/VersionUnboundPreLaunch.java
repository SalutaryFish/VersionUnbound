package net.salutaryfish.versionunbound.loader;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import net.fabricmc.loader.impl.launch.FabricLauncherBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixins;

/**
 * Runs before Minecraft's classes load: puts NeoForge mod jars on the game classpath and
 * registers their mixin configs, so the mixins apply when the target classes first load.
 */
public class VersionUnboundPreLaunch implements PreLaunchEntrypoint {
    private static final Logger LOGGER = LoggerFactory.getLogger("VersionUnbound");

    @Override
    public void onPreLaunch() {
        Path modsDir = FabricLoader.getInstance().getGameDir().resolve("mods");
        List<NeoModCandidate> mods;
        try {
            mods = NeoModDiscovery.discover(modsDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to scan " + modsDir + " for NeoForge mods", e);
        }

        Set<Path> jars = new LinkedHashSet<>();
        for (NeoModCandidate mod : mods) {
            if (jars.add(mod.jar())) {
                FabricLauncherBase.getLauncher().addToClassPath(mod.jar());
            }
            for (String config : mod.mixinConfigs()) {
                Mixins.addConfiguration(config);
            }
            LOGGER.info("Found NeoForge mod {} {} ({}), mixin configs {}", mod.modId(), mod.version(), mod.jar().getFileName(), mod.mixinConfigs());
        }
        NeoModLoader.setCandidates(mods);
    }
}
