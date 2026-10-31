package net.salutaryfish.versionunbound.upgrade.v1_21_1;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.toml.TomlParser;
import com.electronwill.nightconfig.toml.TomlWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;

/**
 * Upgrades a NeoForge 1.21.1 mod jar into a new jar: classes through {@link ClassUpgrader}, the mods.toml's
 * {@code minecraft} and {@code neoforge} dependency ranges widened to the versions Layer A provides (the upgraded jar is
 * a 26.2 mod), and the class renames applied to its access transformers. Signature files are dropped (the classes
 * changed). Not yet: nested jars (JarJar), mixin configs and the mixins' target strings, data and asset files.
 */
final class JarUpgrader {
    static final String MODS_TOML = "META-INF/neoforge.mods.toml";
    private static final String DEFAULT_AT = "META-INF/accesstransformer.cfg";
    private static final DefaultArtifactVersion FROM = new DefaultArtifactVersion("1.21.1");
    /** 26.2's range, which FML's dependency check then finds the running Minecraft in. */
    static final String MINECRAFT_RANGE = "[26.2,26.3)";
    /** Any NeoForge: Layer A is the NeoForge the upgraded jar needs. */
    static final String NEOFORGE_RANGE = "[0,)";
    /** A name in a class or descriptor: an internal name, or a dotted one (ATs). */
    private static final Pattern CLASS_NAME = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*(?:[./][A-Za-z_$][A-Za-z0-9_$]*)+");

    private JarUpgrader() {}

    /**
     * Whether the jar is a 1.21.1 mod: its mods.toml requires a Minecraft range that holds 1.21.1 and not
     * {@code current}. A jar that names no Minecraft range isn't upgraded.
     */
    static boolean isFrom1211(ZipFile jar, String current) throws IOException {
        ZipEntry toml = jar.getEntry(MODS_TOML);
        if (toml == null) return false;
        CommentedConfig config = new TomlParser().parse(new String(jar.getInputStream(toml).readAllBytes(), StandardCharsets.UTF_8));
        return dependencies(config).stream()
                .filter(dependency -> "minecraft".equals(dependency.get("modId")))
                .map(dependency -> dependency.<Object>get("versionRange"))
                .flatMap(range -> range instanceof String spec ? Optional.of(spec).stream() : Optional.<String>empty().stream())
                .anyMatch(spec -> {
                    try {
                        VersionRange range = VersionRange.createFromVersionSpec(spec);
                        return range.containsVersion(FROM) && !range.containsVersion(new DefaultArtifactVersion(current));
                    } catch (InvalidVersionSpecificationException e) {
                        return false;
                    }
                });
    }

    /** Writes the upgraded jar to {@code target}, through a temporary file next to it. Returns the rewritten references. */
    static int upgrade(ZipFile jar, Path target, ClassHierarchy gameClasses) throws IOException {
        Map<String, ZipEntry> classes = new HashMap<>();
        jar.stream().filter(entry -> entry.getName().endsWith(".class")).forEach(entry -> classes.put(entry.getName(), entry));
        var upgrader = new ClassUpgrader(gameClasses.withFirst(name -> {
            ZipEntry entry = classes.get(name + ".class");
            if (entry == null) return null;
            try {
                return jar.getInputStream(entry).readAllBytes();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }));
        CommentedConfig toml = new TomlParser().parse(new String(jar.getInputStream(jar.getEntry(MODS_TOML)).readAllBytes(), StandardCharsets.UTF_8));
        List<String> accessTransformers = new ArrayList<>();
        List<Config> listed = toml.getOrElse("accessTransformers", List.of());
        listed.forEach(at -> accessTransformers.add(at.get("file")));
        if (accessTransformers.isEmpty()) accessTransformers.add(DEFAULT_AT);

        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
        try {
            try (OutputStream file = Files.newOutputStream(temporary); var out = new ZipOutputStream(file)) {
                for (ZipEntry entry : jar.stream().toList()) {
                    String name = entry.getName();
                    if (isSignature(name)) continue;
                    out.putNextEntry(new ZipEntry(name));
                    if (entry.isDirectory()) {
                        out.closeEntry();
                        continue;
                    }
                    byte[] bytes = jar.getInputStream(entry).readAllBytes();
                    if (name.endsWith(".class")) {
                        bytes = upgrader.upgrade(bytes);
                    } else if (name.equals(MODS_TOML)) {
                        bytes = upgradeToml(toml);
                    } else if (accessTransformers.contains(name)) {
                        bytes = renameClasses(new String(bytes, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8);
                    }
                    out.write(bytes);
                    out.closeEntry();
                }
            }
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temporary);
        }
        return upgrader.rewritten.get();
    }

    private static boolean isSignature(String name) {
        if (!name.startsWith("META-INF/") || name.indexOf('/', "META-INF/".length()) >= 0) return false;
        return name.endsWith(".SF") || name.endsWith(".RSA") || name.endsWith(".DSA") || name.endsWith(".EC");
    }

    private static byte[] upgradeToml(CommentedConfig toml) {
        for (Config dependency : dependencies(toml)) {
            Object modId = dependency.get("modId");
            if ("minecraft".equals(modId)) dependency.set("versionRange", MINECRAFT_RANGE);
            if ("neoforge".equals(modId)) dependency.set("versionRange", NEOFORGE_RANGE);
        }
        return new TomlWriter().writeToString(toml).getBytes(StandardCharsets.UTF_8);
    }

    /** Every {@code [[dependencies.<modid>]]} table. */
    private static List<Config> dependencies(Config toml) {
        Config dependencies = toml.get("dependencies");
        if (dependencies == null) return List.of();
        List<Config> all = new ArrayList<>();
        for (Object value : dependencies.valueMap().values()) {
            if (value instanceof List<?> list) {
                list.forEach(item -> {
                    if (item instanceof Config config) all.add(config);
                });
            }
        }
        return all;
    }

    /** Renames classes in text, in internal ({@code a/b/C}) and dotted ({@code a.b.C}) form. */
    static String renameClasses(String text) {
        Matcher matcher = CLASS_NAME.matcher(text);
        var result = new StringBuilder();
        while (matcher.find()) {
            String found = matcher.group();
            boolean dotted = found.indexOf('/') < 0;
            String renamed = ClassUpgrader.renameClass(dotted ? found.replace('.', '/') : found);
            matcher.appendReplacement(result, Matcher.quoteReplacement(dotted ? renamed.replace('/', '.') : renamed));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
