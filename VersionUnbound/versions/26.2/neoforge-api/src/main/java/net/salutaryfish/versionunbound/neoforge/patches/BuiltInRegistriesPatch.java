package net.salutaryfish.versionunbound.neoforge.patches;

import java.util.Collections;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.salutaryfish.versionunbound.neoforge.mixin.registries.BuiltInRegistriesAccessor;

/**
 * The static method NeoForge's patch adds to {@link BuiltInRegistries}, which {@code AddedStaticMethods} adds there as a
 * forwarder to this one.
 */
public final class BuiltInRegistriesPatch {
    private BuiltInRegistriesPatch() {}

    public static Set<Identifier> getVanillaRegistrationOrder() {
        return Collections.unmodifiableSet(BuiltInRegistriesAccessor.getLoaders().keySet());
    }
}
