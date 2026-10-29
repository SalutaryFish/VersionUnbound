/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.salutaryfish.versionunbound.neoforge.coremods;

import java.util.List;
import java.util.function.Function;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

/**
 * Redirect calls to one method to another.
 * <p>
 * VersionUnbound: NeoForge's {@code net.neoforged.neoforge.coremods.MethodRedirector}, without FML's class processor API
 * (not ported): {@link CoremodMixinPlugin} runs {@link #transform} on the classes its carrier mixins target, and those
 * mixins' target lists stand in for NeoForge's target list files. {@code transform} returns how many calls it replaced.
 */
public final class MethodRedirector {
    static final List<MethodRedirection> REDIRECTIONS = List.of(
            new MethodRedirection(
                    Opcodes.INVOKEVIRTUAL,
                    "finalizeSpawn",
                    "(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/world/DifficultyInstance;Lnet/minecraft/world/entity/EntitySpawnReason;Lnet/minecraft/world/entity/SpawnGroupData;)Lnet/minecraft/world/entity/SpawnGroupData;",
                    methodInsnNode -> new MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            "net/neoforged/neoforge/event/EventHooks",
                            "finalizeMobSpawn",
                            "(Lnet/minecraft/world/entity/Mob;Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/world/DifficultyInstance;Lnet/minecraft/world/entity/EntitySpawnReason;Lnet/minecraft/world/entity/SpawnGroupData;)Lnet/minecraft/world/entity/SpawnGroupData;",
                            false)));

    private MethodRedirector() {}

    static int transform(ClassNode classNode, List<MethodRedirection> redirections) {
        int replaced = 0;
        var methods = classNode.methods;
        for (var method : methods) {
            var instr = method.instructions;
            for (var i = 0; i < instr.size(); i++) {
                var node = instr.get(i);
                if (node instanceof MethodInsnNode methodInsnNode) {
                    for (var redirection : redirections) {
                        if (redirection.invokeOpCode == methodInsnNode.getOpcode()
                                && redirection.methodName.equals(methodInsnNode.name)
                                && redirection.methodDescriptor.equals(methodInsnNode.desc)) {
                            // Found a match for the target method
                            instr.set(
                                    methodInsnNode,
                                    redirection.redirector.apply(methodInsnNode));
                            replaced++;
                        }
                    }
                }
            }
        }
        return replaced;
    }

    record MethodRedirection(
            int invokeOpCode,
            String methodName,
            String methodDescriptor,
            Function<MethodInsnNode, MethodInsnNode> redirector) {}
}
