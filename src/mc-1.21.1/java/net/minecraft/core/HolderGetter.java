package net.minecraft.core;

import java.util.Optional;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;

public interface HolderGetter<T> {
    Optional<Holder.Reference<T>> get(ResourceKey<T> var1);

    default Holder.Reference<T> getOrThrow(ResourceKey<T> resourceKey) {
        return this.get(resourceKey).orElseThrow(() -> new IllegalStateException("Missing element " + resourceKey));
    }

    Optional<HolderSet.Named<T>> get(TagKey<T> var1);

    default HolderSet.Named<T> getOrThrow(TagKey<T> tagKey) {
        return this.get(tagKey).orElseThrow(() -> new IllegalStateException("Missing tag " + tagKey));
    }

    interface Provider {
        <T> Optional<HolderGetter<T>> lookup(ResourceKey<? extends Registry<? extends T>> var1);

        default <T> HolderGetter<T> lookupOrThrow(ResourceKey<? extends Registry<? extends T>> registryKey) {
            return this.lookup(registryKey).orElseThrow(() -> new IllegalStateException("Registry " + registryKey.location() + " not found"));
        }

        default <T> Optional<Holder.Reference<T>> get(ResourceKey<? extends Registry<? extends T>> registryKey, ResourceKey<T> key) {
            return this.lookup(registryKey).flatMap(holderGetter -> holderGetter.get(key));
        }
    }
}
