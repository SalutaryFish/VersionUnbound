package net.minecraft.core;

import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Keyable;
import com.mojang.serialization.Lifecycle;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;

public interface Registry<T> extends Keyable, IdMap<T> {
    ResourceKey<? extends Registry<T>> key();

    default Codec<T> byNameCodec() {
        return this.referenceHolderWithLifecycle().flatComapMap(Holder.Reference::value, object -> this.safeCastToReference(this.wrapAsHolder((T)object)));
    }

    default Codec<Holder<T>> holderByNameCodec() {
        return this.referenceHolderWithLifecycle().flatComapMap(reference -> (Holder<T>)reference, this::safeCastToReference);
    }

    private Codec<Holder.Reference<T>> referenceHolderWithLifecycle() {
        Codec<Holder.Reference<T>> codec = ResourceLocation.CODEC
            .comapFlatMap(
                resourceLocation -> this.getHolder(resourceLocation)
                    .map(DataResult::success)
                    .orElseGet(() -> DataResult.error(() -> "Unknown registry key in " + this.key() + ": " + resourceLocation)),
                reference -> reference.key().location()
            );
        return ExtraCodecs.overrideLifecycle(
            codec, reference -> this.registrationInfo(reference.key()).map(RegistrationInfo::lifecycle).orElse(Lifecycle.experimental())
        );
    }

    private DataResult<Holder.Reference<T>> safeCastToReference(Holder<T> value) {
        return value instanceof Holder.Reference<T> reference
            ? DataResult.success(reference)
            : DataResult.error(() -> "Unregistered holder in " + this.key() + ": " + value);
    }

    @Override
    default <U> Stream<U> keys(DynamicOps<U> ops) {
        return this.keySet().stream().map(resourceLocation -> ops.createString(resourceLocation.toString()));
    }

    /**
     * @return the name used to identify the given object within this registry or {@code null} if the object is not within this registry
     */
    @Nullable
    ResourceLocation getKey(T var1);

    Optional<ResourceKey<T>> getResourceKey(T var1);

    /**
     * @return the integer ID used to identify the given object
     */
    @Override
    int getId(@Nullable T var1);

    @Nullable
    T get(@Nullable ResourceKey<T> var1);

    @Nullable
    T get(@Nullable ResourceLocation var1);

    Optional<RegistrationInfo> registrationInfo(ResourceKey<T> var1);

    Lifecycle registryLifecycle();

    default Optional<T> getOptional(@Nullable ResourceLocation name) {
        return Optional.ofNullable(this.get(name));
    }

    default Optional<T> getOptional(@Nullable ResourceKey<T> registryKey) {
        return Optional.ofNullable(this.get(registryKey));
    }

    Optional<Holder.Reference<T>> getAny();

    default T getOrThrow(ResourceKey<T> key) {
        T object = this.get(key);
        if (object == null) {
            throw new IllegalStateException("Missing key in " + this.key() + ": " + key);
        } else {
            return object;
        }
    }

    Set<ResourceLocation> keySet();

    Set<Entry<ResourceKey<T>, T>> entrySet();

    Set<ResourceKey<T>> registryKeySet();

    Optional<Holder.Reference<T>> getRandom(RandomSource var1);

    default Stream<T> stream() {
        return StreamSupport.stream(this.spliterator(), false);
    }

    boolean containsKey(ResourceLocation var1);

    boolean containsKey(ResourceKey<T> var1);

    static <T> T register(Registry<? super T> registry, String name, T value) {
        return register(registry, ResourceLocation.parse(name), value);
    }

    static <V, T extends V> T register(Registry<V> registry, ResourceLocation name, T value) {
        return register(registry, ResourceKey.create(registry.key(), name), value);
    }

    static <V, T extends V> T register(Registry<V> registry, ResourceKey<V> key, T value) {
        ((WritableRegistry)registry).register(key, (V)value, RegistrationInfo.BUILT_IN);
        return value;
    }

    static <T> Holder.Reference<T> registerForHolder(Registry<T> registry, ResourceKey<T> key, T value) {
        return ((WritableRegistry)registry).register(key, value, RegistrationInfo.BUILT_IN);
    }

    static <T> Holder.Reference<T> registerForHolder(Registry<T> registry, ResourceLocation name, T value) {
        return registerForHolder(registry, ResourceKey.create(registry.key(), name), value);
    }

    Registry<T> freeze();

    Holder.Reference<T> createIntrusiveHolder(T var1);

    Optional<Holder.Reference<T>> getHolder(int var1);

    Optional<Holder.Reference<T>> getHolder(ResourceLocation var1);

    Optional<Holder.Reference<T>> getHolder(ResourceKey<T> var1);

    Holder<T> wrapAsHolder(T var1);

    default Holder.Reference<T> getHolderOrThrow(ResourceKey<T> key) {
        return this.getHolder(key).orElseThrow(() -> new IllegalStateException("Missing key in " + this.key() + ": " + key));
    }

    Stream<Holder.Reference<T>> holders();

    Optional<HolderSet.Named<T>> getTag(TagKey<T> var1);

    default Iterable<Holder<T>> getTagOrEmpty(TagKey<T> key) {
        return DataFixUtils.orElse(this.getTag(key), List.<T>of());
    }

    default Optional<Holder<T>> getRandomElementOf(TagKey<T> key, RandomSource random) {
        return this.getTag(key).flatMap(named -> named.getRandomElement(random));
    }

    HolderSet.Named<T> getOrCreateTag(TagKey<T> var1);

    Stream<Pair<TagKey<T>, HolderSet.Named<T>>> getTags();

    Stream<TagKey<T>> getTagNames();

    void resetTags();

    void bindTags(Map<TagKey<T>, List<Holder<T>>> var1);

    default IdMap<Holder<T>> asHolderIdMap() {
        return new IdMap<Holder<T>>() {
            /**
             * @return the integer ID used to identify the given object
             */
            public int getId(Holder<T> value) {
                return Registry.this.getId(value.value());
            }

            @Nullable
            public Holder<T> byId(int id) {
                return (Holder<T>)Registry.this.getHolder(id).orElse(null);
            }

            @Override
            public int size() {
                return Registry.this.size();
            }

            @Override
            public Iterator<Holder<T>> iterator() {
                return Registry.this.holders().map(holder -> (Holder<T>)holder).iterator();
            }
        };
    }

    HolderOwner<T> holderOwner();

    HolderLookup.RegistryLookup<T> asLookup();

    default HolderLookup.RegistryLookup<T> asTagAddingLookup() {
        return new HolderLookup.RegistryLookup.Delegate<T>() {
            @Override
            public HolderLookup.RegistryLookup<T> parent() {
                return Registry.this.asLookup();
            }

            @Override
            public Optional<HolderSet.Named<T>> get(TagKey<T> tagKey) {
                return Optional.of(this.getOrThrow(tagKey));
            }

            @Override
            public HolderSet.Named<T> getOrThrow(TagKey<T> tagKey) {
                return Registry.this.getOrCreateTag(tagKey);
            }
        };
    }
}
