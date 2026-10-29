package net.salutaryfish.versionunbound.neoforge.mixin.registries;

import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Vanilla's registries in bootstrap order, for NeoForge's registration order (its patch adds a getter for the keys).
 */
@Mixin(BuiltInRegistries.class)
public interface BuiltInRegistriesAccessor {
    @Accessor("LOADERS")
    static Map<Identifier, Supplier<?>> getLoaders() {
        throw new AssertionError();
    }
}
