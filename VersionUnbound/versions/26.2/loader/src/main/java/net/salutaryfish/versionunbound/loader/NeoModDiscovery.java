package net.salutaryfish.versionunbound.loader;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.toml.TomlParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Finds NeoForge mod jars (those with META-INF/neoforge.mods.toml) in a directory.
 */
public final class NeoModDiscovery {
    private static final Logger LOGGER = LoggerFactory.getLogger("VersionUnbound");
    private static final String MODS_TOML = "META-INF/neoforge.mods.toml";
    private static final String MOD_ANNOTATION = "Lnet/neoforged/fml/common/Mod;";

    private NeoModDiscovery() {}

    public static List<NeoModCandidate> discover(Path modsDir) throws IOException {
        if (!Files.isDirectory(modsDir)) return List.of();
        List<NeoModCandidate> found = new ArrayList<>();
        try (Stream<Path> files = Files.list(modsDir)) {
            for (Path jar : files.filter(p -> p.getFileName().toString().endsWith(".jar")).sorted().toList()) {
                try (ZipFile zip = new ZipFile(jar.toFile())) {
                    ZipEntry toml = zip.getEntry(MODS_TOML);
                    if (toml != null) found.addAll(readJar(jar, zip, toml));
                } catch (IOException e) {
                    LOGGER.warn("Skipping unreadable jar {}", jar, e);
                }
            }
        }
        return found;
    }

    private static List<NeoModCandidate> readJar(Path jar, ZipFile zip, ZipEntry tomlEntry) throws IOException {
        Config toml;
        try (InputStream in = zip.getInputStream(tomlEntry)) {
            toml = new TomlParser().parse(new InputStreamReader(in, StandardCharsets.UTF_8));
        }

        List<String> mixinConfigs = new ArrayList<>();
        for (Config mixin : toml.<List<Config>>getOrElse("mixins", List.of())) {
            String config = mixin.get("config");
            if (config != null) mixinConfigs.add(config);
        }

        Map<String, List<String>> modClasses = findModClasses(zip);
        List<NeoModCandidate> mods = new ArrayList<>();
        for (Config mod : toml.<List<Config>>getOrElse("mods", List.of())) {
            String modId = mod.get("modId");
            String version = mod.getOrElse("version", "0");
            // Mixin configs belong to the jar; attach them to its first mod.
            List<String> mixins = mods.isEmpty() ? mixinConfigs : List.of();
            mods.add(new NeoModCandidate(jar, modId, version, modClasses.getOrDefault(modId, List.of()), mixins));
        }
        return mods;
    }

    /** Maps mod id to the classes annotated {@code @Mod(modId)}. */
    private static Map<String, List<String>> findModClasses(ZipFile zip) throws IOException {
        Map<String, List<String>> result = new HashMap<>();
        var entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String name = entry.getName();
            if (!name.endsWith(".class") || name.startsWith("META-INF/")) continue;
            try (InputStream in = zip.getInputStream(entry)) {
                String modId = readModAnnotation(in.readAllBytes());
                if (modId != null) {
                    String className = name.substring(0, name.length() - ".class".length()).replace('/', '.');
                    result.computeIfAbsent(modId, k -> new ArrayList<>()).add(className);
                }
            }
        }
        return result;
    }

    private static String readModAnnotation(byte[] bytes) {
        String[] modId = new String[1];
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                if (!MOD_ANNOTATION.equals(descriptor)) return null;
                return new AnnotationVisitor(Opcodes.ASM9) {
                    @Override
                    public void visit(String name, Object value) {
                        if ("value".equals(name)) modId[0] = (String) value;
                    }
                };
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return modId[0];
    }
}
