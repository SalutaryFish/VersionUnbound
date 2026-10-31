package net.salutaryfish.versionunbound.loader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.launch.FabricLauncherBase;
import net.fabricmc.loader.impl.lib.classtweaker.api.ClassTweakerReader;
import net.neoforged.fml.loading.moddiscovery.ModFile;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Applies NeoForge mods' access transformers ({@code [[accessTransformers]]} in the TOML, by default
 * {@code META-INF/accesstransformer.cfg}), which FML hands to its AT engine. Here each file becomes class tweaker
 * lines that go into Fabric Loader's own class tweaker, which Fabric Loader consults for every class it loads
 * afterwards (its {@code FabricTransformer} asks for the targets per class). That's before Minecraft's classes load,
 * as with FML.
 *
 * <p>The conversion follows the same rules as the {@code generateClassTweakerAccess} task in neoforge-api's
 * build.gradle, which converts NeoForge's own ATs at build time; keep the two in step. Class tweakers need a field's
 * descriptor and have no wildcards, so this reads the target classes' unmodified bytes through Knot.
 */
final class ModAccessTransformers {
    private static final Logger LOGGER = LoggerFactory.getLogger("VersionUnbound");

    private ModAccessTransformers() {}

    static void apply(List<ModFile> modFiles) {
        var classes = new HashMap<String, @Nullable ClassNode>();
        var launcher = FabricLauncherBase.getLauncher();
        String namespace = launcher.getMappingConfiguration().getRuntimeNamespace();
        for (var modFile : modFiles) {
            for (var atPath : modFile.getAccessTransformers()) {
                String text;
                try (InputStream in = modFile.getContents().openFile(atPath)) {
                    if (in == null) {
                        LOGGER.error("Access transformer file {} provided by {} does not exist!", atPath, modFile);
                        continue;
                    }
                    text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to load AT at " + atPath + " from " + modFile, e);
                }
                var skipped = new ArrayList<String>();
                var lines = convert(text, name -> classes.computeIfAbsent(name, ModAccessTransformers::readClass), skipped);
                var tweaker = new StringBuilder("classTweaker\tv1\t").append(namespace).append('\n');
                Set<String> owners = new LinkedHashSet<>();
                for (var line : lines) {
                    tweaker.append(line).append('\n');
                    owners.add(line.split("\t")[2]);
                }
                try {
                    ClassTweakerReader.create(FabricLoaderImpl.INSTANCE.getClassTweaker())
                            .read(new BufferedReader(new StringReader(tweaker.toString())), namespace);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to apply AT at " + atPath + " from " + modFile, e);
                }
                for (var owner : owners) {
                    if (launcher.isClassLoaded(owner.replace('/', '.'))) {
                        LOGGER.warn("Access transformer {} from {} targets {}, which has already loaded; it won't apply", atPath, modFile, owner);
                    }
                }
                LOGGER.debug("Applied access transformer {} from {}: {} lines", atPath, modFile, lines.size());
                if (!skipped.isEmpty()) {
                    LOGGER.warn("Skipped {} entries of access transformer {} from {}:\n  {}", skipped.size(), atPath, modFile, String.join("\n  ", skipped));
                }
            }
        }
    }

    private static @Nullable ClassNode readClass(String internalName) {
        try {
            byte[] bytes = FabricLauncherBase.getLauncher().getClassByteArray(internalName.replace('/', '.'), false);
            if (bytes == null) return null;
            var node = new ClassNode();
            new ClassReader(bytes).accept(node, ClassReader.SKIP_CODE);
            return node;
        } catch (IOException e) {
            return null;
        }
    }

    interface ClassLookup {
        @Nullable
        ClassNode find(String internalName);
    }

    /** Converts AT lines into class tweaker lines (without the header); entries that can't apply go to skipped. */
    static List<String> convert(String atText, ClassLookup lookup, List<String> skipped) {
        Set<String> out = new LinkedHashSet<>();
        for (String raw : atText.split("\\R")) {
            int hash = raw.indexOf('#');
            String line = (hash >= 0 ? raw.substring(0, hash) : raw).trim();
            if (line.isEmpty()) continue;
            String[] parts = line.split("\\s+");
            String modifier = parts[0];
            if (modifier.endsWith("+f")) {
                skipped.add(raw + " (adds final)");
                continue;
            }
            boolean definal = modifier.endsWith("-f");
            String access = definal ? modifier.substring(0, modifier.length() - 2) : modifier;
            if (parts.length < 2 || !List.of("public", "protected", "default", "private").contains(access)) {
                skipped.add(raw + " (not an AT entry)");
                continue;
            }
            String owner = parts[1].replace('.', '/');
            ClassNode node = lookup.find(owner);
            if (node == null) {
                skipped.add(raw + " (no such class)");
                continue;
            }
            if (parts.length == 2) {
                if (!access.equals("private")) out.add("accessible\tclass\t" + owner);
                if (definal) out.add("extendable\tclass\t" + owner);
                continue;
            }
            String member = parts[2];
            List<FieldNode> fields = new ArrayList<>();
            List<MethodNode> methods = new ArrayList<>();
            if (member.equals("*")) {
                fields.addAll(node.fields);
            } else if (member.equals("*()")) {
                node.methods.stream().filter(m -> !m.name.equals("<clinit>")).forEach(methods::add);
            } else if (member.contains("(")) {
                String name = member.substring(0, member.indexOf('('));
                String desc = member.substring(member.indexOf('(')).replace('.', '/');
                node.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(desc)).forEach(methods::add);
                if (methods.isEmpty()) {
                    skipped.add(raw + " (no such method)");
                    continue;
                }
            } else {
                node.fields.stream().filter(f -> f.name.equals(member)).forEach(fields::add);
                if (fields.isEmpty()) {
                    skipped.add(raw + " (no such field)");
                    continue;
                }
            }
            for (FieldNode f : fields) {
                // Fields have no protected or package level in a class tweaker; accessible (public) covers them.
                if (!access.equals("private")) out.add("accessible\tfield\t" + owner + "\t" + f.name + "\t" + f.desc);
                if (definal) out.add("mutable\tfield\t" + owner + "\t" + f.name + "\t" + f.desc);
            }
            boolean isInterface = (node.access & Opcodes.ACC_INTERFACE) != 0;
            for (MethodNode m : methods) {
                boolean wasPrivate = (m.access & Opcodes.ACC_PRIVATE) != 0;
                boolean isStatic = (m.access & Opcodes.ACC_STATIC) != 0;
                if (isInterface && (!access.equals("public") || definal)) {
                    skipped.add(raw + " (" + m.name + ": not public in an interface)");
                    continue;
                }
                if (access.equals("public")) out.add("accessible\tmethod\t" + owner + "\t" + m.name + "\t" + m.desc);
                // Interfaces can't have protected methods, and nothing overrides a static method or a constructor.
                if (isInterface || (access.equals("public") && (isStatic || m.name.equals("<init>")))) continue;
                // extendable: protected and non-final, and the class's own invokespecial calls of a formerly private
                // method become invokevirtual, as with FML's AT engine. Only accessible would make it public final.
                if ((!access.equals("public") && !access.equals("private")) || definal || wasPrivate) {
                    out.add("extendable\tmethod\t" + owner + "\t" + m.name + "\t" + m.desc);
                }
            }
        }
        return new ArrayList<>(out);
    }
}
