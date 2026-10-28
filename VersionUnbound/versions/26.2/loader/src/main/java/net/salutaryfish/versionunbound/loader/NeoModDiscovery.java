package net.salutaryfish.versionunbound.loader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipFile;
import net.neoforged.fml.loading.moddiscovery.ModFileParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Finds NeoForge mod jars: the jars in a directory that contain META-INF/neoforge.mods.toml.
 * Reading them is left to FML ({@link net.neoforged.fml.loading.FMLLoader#discoverMods}).
 */
public final class NeoModDiscovery {
    private static final Logger LOGGER = LoggerFactory.getLogger("VersionUnbound");

    private NeoModDiscovery() {}

    public static List<Path> findModJars(Path modsDir) throws IOException {
        if (!Files.isDirectory(modsDir)) return List.of();
        List<Path> found = new ArrayList<>();
        try (Stream<Path> files = Files.list(modsDir)) {
            for (Path jar : files.filter(p -> p.getFileName().toString().endsWith(".jar")).sorted().toList()) {
                try (ZipFile zip = new ZipFile(jar.toFile())) {
                    if (zip.getEntry(ModFileParser.MODS_TOML) != null) found.add(jar);
                } catch (IOException e) {
                    LOGGER.warn("Skipping unreadable jar {}", jar, e);
                }
            }
        }
        return found;
    }
}
