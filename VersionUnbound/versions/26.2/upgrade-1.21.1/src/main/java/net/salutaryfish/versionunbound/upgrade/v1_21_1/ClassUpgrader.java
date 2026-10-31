package net.salutaryfish.versionunbound.upgrade.v1_21_1;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Upgrades one class of a 1.21.1 jar: renames the classes {@link Mappings#CLASSES} lists (everywhere a class name can
 * appear), then rewrites method references, in calls and in method handles (lambdas, method references): renamed
 * methods get their 26.2 names, removed or changed ones call their bridge, and constructors that gained leading
 * parameters get them. Frames are kept (the code added runs straight through, its temporaries live only inside it);
 * maximums are recomputed.
 */
final class ClassUpgrader {
    private static final Remapper CLASS_RENAMES = new Remapper() {
        private final Map<String, String> renames = new HashMap<>();

        {
            Mappings.CLASSES.forEach(rename -> this.renames.put(rename.from(), rename.to()));
        }

        @Override
        public String map(String internalName) {
            String exact = this.renames.get(internalName);
            if (exact != null) return exact;
            int nested = internalName.indexOf('$');
            if (nested > 0) {
                String outer = this.renames.get(internalName.substring(0, nested));
                if (outer != null) return outer + internalName.substring(nested);
            }
            return internalName;
        }
    };

    private final ClassHierarchy hierarchy;
    /** How many references this upgrader rewrote, renames of classes not counted. */
    final AtomicInteger rewritten = new AtomicInteger();

    ClassUpgrader(ClassHierarchy hierarchy) {
        this.hierarchy = hierarchy;
    }

    /** Renames a class name as {@link Mappings#CLASSES} says (for resources that name classes, such as ATs). */
    static String renameClass(String internalName) {
        return CLASS_RENAMES.map(internalName);
    }

    byte[] upgrade(byte[] classFile) {
        var node = new ClassNode();
        new ClassReader(classFile).accept(new ClassRemapper(node, CLASS_RENAMES), 0);
        for (MethodNode method : node.methods) {
            this.upgrade(method);
        }
        var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private void upgrade(MethodNode method) {
        InsnList instructions = method.instructions;
        for (AbstractInsnNode instruction = instructions.getFirst(); instruction != null; instruction = instruction.getNext()) {
            switch (instruction) {
                case MethodInsnNode call when call.name.equals("<init>") -> this.constructor(method, call);
                case MethodInsnNode call -> this.call(call);
                case InvokeDynamicInsnNode indy -> {
                    indy.bsm = this.handle(indy.bsm);
                    for (int i = 0; i < indy.bsmArgs.length; i++) {
                        if (indy.bsmArgs[i] instanceof Handle handle) indy.bsmArgs[i] = this.handle(handle);
                    }
                }
                case LdcInsnNode ldc when ldc.cst instanceof Handle handle -> ldc.cst = this.handle(handle);
                default -> {}
            }
        }
    }

    private void call(MethodInsnNode call) {
        boolean isStatic = call.getOpcode() == Opcodes.INVOKESTATIC;
        Optional<Mappings.MethodRename> rename = this.rename(call.owner, call.name, call.desc);
        if (rename.isPresent()) {
            call.name = rename.get().newName();
            this.rewritten.incrementAndGet();
            return;
        }
        this.redirect(call.owner, call.name, call.desc, isStatic).ifPresent(redirect -> {
            call.setOpcode(Opcodes.INVOKESTATIC);
            call.owner = redirect.bridge();
            call.name = redirect.bridgeName();
            call.desc = redirect.bridgeDescriptor();
            call.itf = false;
            this.rewritten.incrementAndGet();
        });
    }

    /** {@code new owner(args)} where 26.2's constructor takes leading parameters: they're pushed below the arguments. */
    private void constructor(MethodNode method, MethodInsnNode call) {
        for (Mappings.ConstructorPrefix prefix : Mappings.CONSTRUCTORS) {
            if (!prefix.owner().equals(call.owner) || !prefix.descriptor().equals(call.desc)) continue;
            Type[] arguments = Type.getArgumentTypes(call.desc);
            int[] slots = new int[arguments.length];
            var before = new InsnList();
            int slot = method.maxLocals;
            for (int i = 0; i < arguments.length; i++) {
                slots[i] = slot;
                slot += arguments[i].getSize();
            }
            method.maxLocals = slot;
            for (int i = arguments.length - 1; i >= 0; i--) {
                before.add(new VarInsnNode(arguments[i].getOpcode(Opcodes.ISTORE), slots[i]));
            }
            before.add(new MethodInsnNode(Opcodes.INVOKESTATIC, prefix.bridge(), prefix.bridgeName(), "()" + prefix.prefixDescriptor(), false));
            for (int i = 0; i < arguments.length; i++) {
                before.add(new VarInsnNode(arguments[i].getOpcode(Opcodes.ILOAD), slots[i]));
            }
            method.instructions.insertBefore(call, before);
            call.desc = prefix.newDescriptor();
            this.rewritten.incrementAndGet();
            return;
        }
    }

    private Handle handle(Handle handle) {
        int tag = handle.getTag();
        if (tag < Opcodes.H_INVOKEVIRTUAL || tag == Opcodes.H_NEWINVOKESPECIAL) return handle;
        boolean isStatic = tag == Opcodes.H_INVOKESTATIC;
        Optional<Mappings.MethodRename> rename = this.rename(handle.getOwner(), handle.getName(), handle.getDesc());
        if (rename.isPresent()) {
            this.rewritten.incrementAndGet();
            return new Handle(tag, handle.getOwner(), rename.get().newName(), handle.getDesc(), handle.isInterface());
        }
        return this.redirect(handle.getOwner(), handle.getName(), handle.getDesc(), isStatic).map(redirect -> {
            this.rewritten.incrementAndGet();
            // A method handle to the bridge takes the receiver first, as a handle to the instance method did.
            return new Handle(Opcodes.H_INVOKESTATIC, redirect.bridge(), redirect.bridgeName(), redirect.bridgeDescriptor(), false);
        }).orElse(handle);
    }

    private Optional<Mappings.MethodRename> rename(String owner, String name, String descriptor) {
        return Mappings.METHOD_RENAMES.stream()
                .filter(rename -> rename.name().equals(name) && rename.descriptor().equals(descriptor) && this.hierarchy.isSubtype(owner, rename.owner()))
                .findFirst();
    }

    private Optional<Mappings.Redirect> redirect(String owner, String name, String descriptor, boolean isStatic) {
        return Mappings.REDIRECTS.stream()
                .filter(redirect -> redirect.isStatic() == isStatic && redirect.name().equals(name) && redirect.descriptor().equals(descriptor)
                        && (isStatic ? owner.equals(redirect.owner()) : this.hierarchy.isSubtype(owner, redirect.owner())))
                .findFirst();
    }
}
