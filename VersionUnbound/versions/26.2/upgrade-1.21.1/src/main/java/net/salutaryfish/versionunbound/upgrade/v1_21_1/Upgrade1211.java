package net.salutaryfish.versionunbound.upgrade.v1_21_1;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Function;
import java.util.zip.ZipFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Layer B for NeoForge 1.21.1: replaces each 1.21.1 mod jar in a list with its upgrade to the 26.2 API, made by
 * {@link JarUpgrader} and kept in a cache directory under the jar's SHA-256 and the mappings' fingerprint, so a jar is
 * upgraded once until it or the mappings change. Other jars are left as they are.
 */
public final class Upgrade1211 {
    private static final Logger LOGGER = LoggerFactory.getLogger("VersionUnbound");

    private Upgrade1211() {}

    /**
     * @param jars             NeoForge mod jars
     * @param cacheRoot        where upgraded jars go (a {@code upgrade-1.21.1} directory is made in it)
     * @param minecraftVersion the running Minecraft's version
     * @param gameClassFiles   the game's class files by internal name, for supertypes (nothing may be loaded)
     */
    public static List<Path> upgrade(List<Path> jars, Path cacheRoot, String minecraftVersion, Function<String, InputStream> gameClassFiles) {
        var hierarchy = new ClassHierarchy(name -> {
            try (InputStream in = gameClassFiles.apply(name + ".class")) {
                return in == null ? null : in.readAllBytes();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        List<Path> result = new ArrayList<>(jars.size());
        for (Path jar : jars) {
            try (var zip = new ZipFile(jar.toFile())) {
                if (!JarUpgrader.isFrom1211(zip, minecraftVersion)) {
                    result.add(jar);
                    continue;
                }
                Path target = cacheRoot.resolve("upgrade-1.21.1").resolve(sha256(jar) + "-" + Mappings.fingerprint()).resolve(jar.getFileName());
                if (Files.isRegularFile(target)) {
                    LOGGER.info("Using the upgraded copy of 1.21.1 mod {}: {}", jar.getFileName(), target);
                } else {
                    long start = System.nanoTime();
                    int rewritten = JarUpgrader.upgrade(zip, target, hierarchy);
                    LOGGER.info("Upgraded 1.21.1 mod {} to the 26.2 API in {} ms ({} references rewritten): {}", jar.getFileName(),
                            (System.nanoTime() - start) / 1_000_000, rewritten, target);
                }
                result.add(target);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to upgrade " + jar, e);
            }
        }
        return result;
    }

    private static String sha256(Path file) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file))).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
