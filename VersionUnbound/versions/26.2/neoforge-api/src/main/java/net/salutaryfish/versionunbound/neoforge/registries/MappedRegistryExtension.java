package net.salutaryfish.versionunbound.neoforge.registries;

import java.util.Map;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.registries.datamaps.DataMapType;

/**
 * What NeoForge's {@code BaseMappedRegistry} adds to {@link MappedRegistry} beyond {@link net.neoforged.neoforge.registries.IRegistryExtension}.
 * NeoForge makes it {@code MappedRegistry}'s superclass; a mixin can't change a superclass, so the mixin implements this
 * interface instead, and NeoForge's code that casts to {@code BaseMappedRegistry} casts to this.
 */
public interface MappedRegistryExtension<T> {
    @SuppressWarnings("unchecked")
    static <T> MappedRegistryExtension<T> of(Registry<T> registry) {
        return (MappedRegistryExtension<T>) registry;
    }

    /** NeoForge's {@code setSync}. Sets Fabric API's {@code SYNCED} registry attribute, which drives Fabric's registry sync. */
    void versionunbound$setSync(boolean sync);

    /** NeoForge's {@code setMaxId}. */
    void versionunbound$setMaxId(int maxId);

    /** NeoForge's {@code getDataMaps}: every data map attached to this registry, by type. */
    Map<DataMapType<T, ?>, Map<ResourceKey<T>, ?>> versionunbound$getDataMaps();
}
