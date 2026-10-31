package net.salutaryfish.versionunbound.upgrade.v1_21_1;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.objectweb.asm.ClassReader;

/**
 * Supertypes of classes as 26.2 has them, read from class files (headers only, nothing is loaded): the jar being
 * upgraded first, then the game's classes. A class whose file isn't found has no known supertypes.
 */
final class ClassHierarchy {
    private final Function<String, byte[]> classFiles;
    private final Map<String, List<String>> supertypes = new ConcurrentHashMap<>();

    /** {@code classFiles} gives a class file's bytes by internal name, or {@code null}. */
    ClassHierarchy(Function<String, byte[]> classFiles) {
        this.classFiles = classFiles;
    }

    ClassHierarchy withFirst(Function<String, byte[]> first) {
        return new ClassHierarchy(name -> {
            byte[] bytes = first.apply(name);
            return bytes != null ? bytes : this.classFiles.apply(name);
        });
    }

    /** Whether {@code type} is {@code of} or one of its subtypes. */
    boolean isSubtype(String type, String of) {
        if (type.equals(of)) return true;
        Set<String> seen = new HashSet<>();
        var queue = new ArrayDeque<String>();
        queue.add(type);
        while (!queue.isEmpty()) {
            for (String supertype : this.direct(queue.poll())) {
                if (supertype.equals(of)) return true;
                if (seen.add(supertype)) queue.add(supertype);
            }
        }
        return false;
    }

    private List<String> direct(String type) {
        return this.supertypes.computeIfAbsent(type, name -> {
            if (name.startsWith("java/")) return List.of();
            byte[] bytes = this.classFiles.apply(name);
            if (bytes == null) return List.of();
            var reader = new ClassReader(bytes);
            var result = new java.util.ArrayList<String>();
            if (reader.getSuperName() != null) result.add(reader.getSuperName());
            result.addAll(List.of(reader.getInterfaces()));
            return List.copyOf(result);
        });
    }
}
