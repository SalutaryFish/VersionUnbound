package net.salutaryfish.versionunbound.neoforge.patches;

import net.minecraft.util.TriState;

/**
 * NeoForge's patch to {@link TriState}, injected into it by the class tweaker.
 */
public interface TriStateExtension {
    // Neo: Helper methods for use in patches
    default boolean isTrue() {
        return this == TriState.TRUE;
    }

    default boolean isDefault() {
        return this == TriState.DEFAULT;
    }

    default boolean isFalse() {
        return this == TriState.FALSE;
    }
}
