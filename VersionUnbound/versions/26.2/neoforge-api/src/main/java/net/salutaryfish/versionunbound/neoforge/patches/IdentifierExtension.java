package net.salutaryfish.versionunbound.neoforge.patches;

import net.minecraft.resources.Identifier;

/**
 * NeoForge's patch to {@link Identifier}, injected into it by the class tweaker.
 */
public interface IdentifierExtension {
    // Normal compare sorts by path first, this compares namespace first.
    default int compareNamespaced(Identifier o) {
        Identifier self = (Identifier) this;
        int ret = self.getNamespace().compareTo(o.getNamespace());
        return ret != 0 ? ret : self.getPath().compareTo(o.getPath());
    }
}
