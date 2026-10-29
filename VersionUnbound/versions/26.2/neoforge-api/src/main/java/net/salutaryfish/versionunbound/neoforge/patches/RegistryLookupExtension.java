package net.salutaryfish.versionunbound.neoforge.patches;

import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import org.jspecify.annotations.Nullable;

/**
 * The data map methods NeoForge's patch adds to {@link HolderLookup.RegistryLookup}. Patches can add default methods to a
 * vanilla interface; on Fabric this interface is injected into {@code RegistryLookup} instead (see the class tweaker),
 * which gives mods the same methods.
 *
 * @param <T> the type of registry entries
 */
public interface RegistryLookupExtension<T> {
    /// {@return the data map value attached with the object with the key, or {@code null} if there's no attached value}
    ///
    /// @param type the type of the data map
    /// @param key  the object to get the value for
    /// @param <A>  the data type
    @Nullable
    default <A> A getData(DataMapType<T, A> type, ResourceKey<T> key) {
        return null;
    }

    /// {@return the data map of the given `type`}
    ///
    /// @param type the type of the data map
    /// @param <A>  the data type
    default <A> Map<ResourceKey<T>, A> getDataMap(DataMapType<T, A> type) {
        return Map.of();
    }

    /**
     * NeoForge's overrides in {@link HolderLookup.RegistryLookup.Delegate}, injected into that interface.
     */
    interface Delegate<T> extends RegistryLookupExtension<T> {
        @Override
        @Nullable
        default <A> A getData(DataMapType<T, A> type, ResourceKey<T> key) {
            return ((HolderLookup.RegistryLookup.Delegate<T>) this).parent().getData(type, key);
        }

        @Override
        default <A> Map<ResourceKey<T>, A> getDataMap(DataMapType<T, A> type) {
            return ((HolderLookup.RegistryLookup.Delegate<T>) this).parent().getDataMap(type);
        }
    }
}
