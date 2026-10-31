package net.minecraft.core;

import com.google.common.collect.ImmutableMap;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Lifecycle;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceKey;
import org.slf4j.Logger;

/**
 * The root level registry, essentially a registry of registries. It is also an access point, hence the name, for other dynamic registries.
 */
public interface RegistryAccess extends HolderLookup.Provider {
    Logger LOGGER = LogUtils.getLogger();
    RegistryAccess.Frozen EMPTY = new RegistryAccess.ImmutableRegistryAccess(Map.of()).freeze();

    /**
     * Get the registry owned by this registry access by the given key. If it doesn't exist, the default registry of registries is queried instead, which contains static registries such as blocks.
     * The returned registry can not guarantee that it is writable here, so the return type is widened to {@code Registry<E>} instead.
     */
    <E> Optional<Registry<E>> registry(ResourceKey<? extends Registry<? extends E>> var1);

    @Override
    default <T> Optional<HolderLookup.RegistryLookup<T>> lookup(ResourceKey<? extends Registry<? extends T>> registryKey) {
        return this.registry(registryKey).map(Registry::asLookup);
    }

    /**
     * A variant of {@link #registry(ResourceKey)} that throws if the registry does not exist.
     */
    default <E> Registry<E> registryOrThrow(ResourceKey<? extends Registry<? extends E>> registryKey) {
        return this.registry(registryKey).orElseThrow(() -> new IllegalStateException("Missing registry: " + registryKey));
    }

    Stream<RegistryAccess.RegistryEntry<?>> registries();

    @Override
    default Stream<ResourceKey<? extends Registry<?>>> listRegistries() {
        return this.registries().map(RegistryAccess.RegistryEntry::key);
    }

    static RegistryAccess.Frozen fromRegistryOfRegistries(Registry<? extends Registry<?>> registryOfRegistries) {
        return new RegistryAccess.Frozen() {
            @Override
            public <T> Optional<Registry<T>> registry(ResourceKey<? extends Registry<? extends T>> resourceKey) {
                Registry<Registry<T>> registry = (Registry<Registry<T>>)registryOfRegistries;
                return registry.getOptional((ResourceKey<Registry<T>>)resourceKey);
            }

            @Override
            public Stream<RegistryAccess.RegistryEntry<?>> registries() {
                return registryOfRegistries.entrySet().stream().map(RegistryAccess.RegistryEntry::fromMapEntry);
            }

            @Override
            public RegistryAccess.Frozen freeze() {
                return this;
            }
        };
    }

    default RegistryAccess.Frozen freeze() {
        class FrozenAccess extends RegistryAccess.ImmutableRegistryAccess implements RegistryAccess.Frozen {
            protected FrozenAccess(final Stream<RegistryAccess.RegistryEntry<?>> registries) {
                super(registries);
            }
        }

        return new FrozenAccess(this.registries().map(RegistryAccess.RegistryEntry::freeze));
    }

    default Lifecycle allRegistriesLifecycle() {
        return this.registries().map(registry -> registry.value.registryLifecycle()).reduce(Lifecycle.stable(), Lifecycle::add);
    }

    interface Frozen extends RegistryAccess {
    }

    class ImmutableRegistryAccess implements RegistryAccess {
        private final Map<? extends ResourceKey<? extends Registry<?>>, ? extends Registry<?>> registries;

        public ImmutableRegistryAccess(List<? extends Registry<?>> registries) {
            this.registries = registries.stream().collect(Collectors.toUnmodifiableMap(Registry::key, registry -> registry));
        }

        public ImmutableRegistryAccess(Map<? extends ResourceKey<? extends Registry<?>>, ? extends Registry<?>> registries) {
            this.registries = Map.copyOf(registries);
        }

        public ImmutableRegistryAccess(Stream<RegistryAccess.RegistryEntry<?>> registries) {
            this.registries = registries.collect(ImmutableMap.toImmutableMap(RegistryAccess.RegistryEntry::key, RegistryAccess.RegistryEntry::value));
        }

        /**
         * Get the registry owned by this registry access by the given key. If it doesn't exist, the default registry of registries is queried instead, which contains static registries such as blocks.
         * The returned registry can not guarantee that it is writable here, so the return type is widened to {@code Registry<E>} instead.
         */
        @Override
        public <E> Optional<Registry<E>> registry(ResourceKey<? extends Registry<? extends E>> registryKey) {
            return Optional.ofNullable(this.registries.get(registryKey)).map(registry -> (Registry<E>)registry);
        }

        @Override
        public Stream<RegistryAccess.RegistryEntry<?>> registries() {
            return this.registries.entrySet().stream().map(RegistryAccess.RegistryEntry::fromMapEntry);
        }
    }

    record RegistryEntry<T>(ResourceKey<? extends Registry<T>> key, Registry<T> value) {
        private static <T, R extends Registry<? extends T>> RegistryAccess.RegistryEntry<T> fromMapEntry(
            Entry<? extends ResourceKey<? extends Registry<?>>, R> mapEntry
        ) {
            return fromUntyped((ResourceKey<? extends Registry<?>>)mapEntry.getKey(), mapEntry.getValue());
        }

        private static <T> RegistryAccess.RegistryEntry<T> fromUntyped(ResourceKey<? extends Registry<?>> key, Registry<?> value) {
            return new RegistryAccess.RegistryEntry<>((ResourceKey<? extends Registry<T>>)key, (Registry<T>)value);
        }

        private RegistryAccess.RegistryEntry<T> freeze() {
            return new RegistryAccess.RegistryEntry<>(this.key, this.value.freeze());
        }
    }
}
