package net.salutaryfish.versionunbound.neoforge.coremods;

import java.util.List;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Static methods NeoForge's patches add to vanilla classes, which mods call. A mixin can't add them (Mixin rejects
 * non-private static methods), so {@link CoremodMixinPlugin} adds each one in {@code preApply} of the carrier mixin
 * {@code AddedStaticMethodsTargetsMixin}, as a method with NeoForge's access, name and descriptor that hands its
 * arguments to the static method of the same name and descriptor in a {@code patches/} class, where the code is.
 * Mods' {@code invokestatic} calls (also those naming a subclass as the owner) then resolve as on NeoForge.
 * <p>
 * Added so far: {@code CropBlock.getGrowthSpeed(BlockState, BlockGetter, BlockPos)} (KaleidoscopeCookery's rice calls
 * it). Not yet: the ones listed in HANDOFF.md, Known gap 15 ({@code SpawnPlacements.hasPlacement} and
 * {@code fireSpawnPlacementEvent}, {@code RegistryOps.retrieveRegistryLookup},
 * {@code BuiltInRegistries.getVanillaRegistrationOrder}); add a line here and the class to the carrier.
 */
final class AddedStaticMethods {
    private static final List<AddedStaticMethod> METHODS = List.of(
            new AddedStaticMethod("net/minecraft/world/level/block/CropBlock", Opcodes.ACC_PROTECTED, "getGrowthSpeed",
                    "(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)F",
                    "net/salutaryfish/versionunbound/neoforge/patches/CropBlockPatch"));

    private AddedStaticMethods() {}

    /** Adds the methods that belong to {@code target}; returns how many it added. */
    static int add(ClassNode target) {
        int added = 0;
        for (AddedStaticMethod method : METHODS) {
            if (!method.owner().equals(target.name)) continue;
            boolean present = target.methods.stream().anyMatch(existing -> existing.name.equals(method.name()) && existing.desc.equals(method.descriptor()));
            if (present) {
                throw new IllegalStateException(target.name + " already has " + method.name() + method.descriptor());
            }
            target.methods.add(method.forwarder());
            added++;
        }
        return added;
    }

    /** A static method on {@code owner} whose body is a call of {@code implementation}'s method of the same name and descriptor. */
    private record AddedStaticMethod(String owner, int access, String name, String descriptor, String implementation) {
        MethodNode forwarder() {
            MethodNode node = new MethodNode(access | Opcodes.ACC_STATIC, name, descriptor, null, null);
            int slot = 0;
            for (Type argument : Type.getArgumentTypes(descriptor)) {
                node.instructions.add(new VarInsnNode(argument.getOpcode(Opcodes.ILOAD), slot));
                slot += argument.getSize();
            }
            node.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, implementation, name, descriptor, false));
            Type result = Type.getReturnType(descriptor);
            node.instructions.add(new InsnNode(result.getOpcode(Opcodes.IRETURN)));
            node.maxLocals = slot;
            node.maxStack = Math.max(slot, result.getSize());
            return node;
        }
    }
}
