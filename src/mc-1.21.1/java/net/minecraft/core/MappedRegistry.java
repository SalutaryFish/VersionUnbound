package net.minecraft.core;

import com.google.common.collect.Iterators;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Lifecycle;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import org.slf4j.Logger;

public class MappedRegistry<T> implements WritableRegistry<T> {
    private static final Logger LOGGER = LogUtils.getLogger();
    final ResourceKey<? extends Registry<T>> key;
    private final ObjectList<Holder.Reference<T>> byId = new ObjectArrayList<>(256);
    private final Reference2IntMap<T> toId = Util.make(new Reference2IntOpenHashMap<>(), map -> map.defaultReturnValue(-1));
    private final Map<ResourceLocation, Holder.Reference<T>> byLocation = new HashMap<>();
    private final Map<ResourceKey<T>, Holder.Reference<T>> byKey = new HashMap<>();
    private final Map<T, Holder.Reference<T>> byValue = new IdentityHashMap<>();
    private final Map<ResourceKey<T>, RegistrationInfo> registrationInfos = new IdentityHashMap<>();
    private Lifecycle registryLifecycle;
    private volatile Map<TagKey<T>, HolderSet.Named<T>> tags = new IdentityHashMap<>();
    private boolean frozen;
    @Nullable
    private Map<T, Holder.Reference<T>> unregisteredIntrusiveHolders;
    private final HolderLookup.RegistryLookup<T> lookup = new HolderLookup.RegistryLookup<T>() {
        @Override
        public ResourceKey<? extends Registry<? extends T>> key() {
            return MappedRegistry.this.key;
        }

        @Override
        public Lifecycle registryLifecycle() {
            return MappedRegistry.this.registryLifecycle();
        }

        @Override
        public Optional<Holder.Reference<T>> get(ResourceKey<T> resourceKey) {
            return MappedRegistry.this.getHolder(resourceKey);
        }

        @Override
        public Stream<Holder.Reference<T>> listElements() {
            return MappedRegistry.this.holders();
        }

        @Override
        public Optional<HolderSet.Named<T>> get(TagKey<T> tagKey) {
            return MappedRegistry.this.getTag(tagKey);
        }

        @Override
        public Stream<HolderSet.Named<T>> listTags() {
            return MappedRegistry.this.getTags().map(Pair::getSecond);
        }
    };
    private final Object tagAdditionLock = new Object();

    public MappedRegistry(ResourceKey<? extends Registry<T>> key, Lifecycle registryLifecycle) {
        this(key, registryLifecycle, false);
    }

    public MappedRegistry(ResourceKey<? extends Registry<T>> key, Lifecycle registryLifecycle, boolean hasIntrusiveHolders) {
        this.key = key;
        this.registryLifecycle = registryLifecycle;
        if (hasIntrusiveHolders) {
            this.unregisteredIntrusiveHolders = new IdentityHashMap<>();
        }
    }

    @Override
    public ResourceKey<? extends Registry<T>> key() {
        return this.key;
    }

    @Override
    public String toString() {
        return "Registry[" + this.key + " (" + this.registryLifecycle + ")]";
    }

    private void validateWrite() {
        if (this.frozen) {
            throw new IllegalStateException("Registry is already frozen");
        }
    }

    private void validateWrite(ResourceKey<T> key) {
        if (this.frozen) {
            throw new IllegalStateException("Registry is already frozen (trying to add key " + key + ")");
        }
    }

    @Override
    public Holder.Reference<T> register(ResourceKey<T> key, T value, RegistrationInfo registrationInfo) {
        this.validateWrite(key);
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);
        if (this.byLocation.containsKey(key.location())) {
            Util.pauseInIde(new IllegalStateException("Adding duplicate key '" + key + "' to registry"));
        }

        if (this.byValue.containsKey(value)) {
            Util.pauseInIde(new IllegalStateException("Adding duplicate value '" + value + "' to registry"));
        }

        Holder.Reference<T> reference;
        if (this.unregisteredIntrusiveHolders != null) {
            reference = this.unregisteredIntrusiveHolders.remove(value);
            if (reference == null) {
                throw new AssertionError("Missing intrusive holder for " + key + ":" + value);
            }

            reference.bindKey(key);
        } else {
            reference = this.byKey.computeIfAbsent(key, resourceKey -> Holder.Reference.createStandAlone(this.holderOwner(), (ResourceKey<T>)resourceKey));
        }

        this.byKey.put(key, reference);
        this.byLocation.put(key.location(), reference);
        this.byValue.put(value, reference);
        int i = this.byId.size();
        this.byId.add(reference);
        this.toId.put(value, i);
        this.registrationInfos.put(key, registrationInfo);
        this.registryLifecycle = this.registryLifecycle.add(registrationInfo.lifecycle());
        return reference;
    }

    /**
     * @return the name used to identify the given object within this registry or {@code null} if the object is not within this registry
     */
    @Nullable
    @Override
    public ResourceLocation getKey(T value) {
        Holder.Reference<T> reference = this.byValue.get(value);
        return reference != null ? reference.key().location() : null;
    }

    @Override
    public Optional<ResourceKey<T>> getResourceKey(T value) {
        return Optional.ofNullable(this.byValue.get(value)).map(Holder.Reference::key);
    }

    /**
     * @return the integer ID used to identify the given object
     */
    @Override
    public int getId(@Nullable T value) {
        return this.toId.getInt(value);
    }

    @Nullable
    @Override
    public T get(@Nullable ResourceKey<T> key) {
        return getValueFromNullable(this.byKey.get(key));
    }

    @Nullable
    @Override
    public T byId(int id) {
        return id >= 0 && id < this.byId.size() ? this.byId.get(id).value() : null;
    }

    @Override
    public Optional<Holder.Reference<T>> getHolder(int id) {
        return id >= 0 && id < this.byId.size() ? Optional.ofNullable(this.byId.get(id)) : Optional.empty();
    }

    @Override
    public Optional<Holder.Reference<T>> getHolder(ResourceLocation location) {
        return Optional.ofNullable(this.byLocation.get(location));
    }

    @Override
    public Optional<Holder.Reference<T>> getHolder(ResourceKey<T> key) {
        return Optional.ofNullable(this.byKey.get(key));
    }

    @Override
    public Optional<Holder.Reference<T>> getAny() {
        return this.byId.isEmpty() ? Optional.empty() : Optional.of(this.byId.getFirst());
    }

    @Override
    public Holder<T> wrapAsHolder(T value) {
        Holder.Reference<T> reference = this.byValue.get(value);
        return reference != null ? reference : Holder.direct(value);
    }

    Holder.Reference<T> getOrCreateHolderOrThrow(ResourceKey<T> key) {
        return this.byKey.computeIfAbsent(key, keyx -> {
            if (this.unregisteredIntrusiveHolders != null) {
                throw new IllegalStateException("This registry can't create new holders without value");
            }

            this.validateWrite(keyx);
            return Holder.Reference.createStandAlone(this.holderOwner(), keyx);
        });
    }

    @Override
    public int size() {
        return this.byKey.size();
    }

    @Override
    public Optional<RegistrationInfo> registrationInfo(ResourceKey<T> key) {
        return Optional.ofNullable(this.registrationInfos.get(key));
    }

    @Override
    public Lifecycle registryLifecycle() {
        return this.registryLifecycle;
    }

    @Override
    public Iterator<T> iterator() {
        return Iterators.transform(this.byId.iterator(), Holder::value);
    }

    @Nullable
    @Override
    public T get(@Nullable ResourceLocation name) {
        Holder.Reference<T> reference = this.byLocation.get(name);
        return getValueFromNullable(reference);
    }

    @Nullable
    private static <T> T getValueFromNullable(@Nullable Holder.Reference<T> holder) {
        return holder != null ? holder.value() : null;
    }

    @Override
    public Set<ResourceLocation> keySet() {
        return Collections.unmodifiableSet(this.byLocation.keySet());
    }

    @Override
    public Set<ResourceKey<T>> registryKeySet() {
        return Collections.unmodifiableSet(this.byKey.keySet());
    }

    @Override
    public Set<Entry<ResourceKey<T>, T>> entrySet() {
        return Collections.unmodifiableSet(Maps.transformValues(this.byKey, Holder::value).entrySet());
    }

    @Override
    public Stream<Holder.Reference<T>> holders() {
        return this.byId.stream();
    }

    @Override
    public Stream<Pair<TagKey<T>, HolderSet.Named<T>>> getTags() {
        return this.tags.entrySet().stream().map(entry -> Pair.of(entry.getKey(), entry.getValue()));
    }

    @Override
    public HolderSet.Named<T> getOrCreateTag(TagKey<T> key) {
        HolderSet.Named<T> named = this.tags.get(key);
        if (named != null) {
            return named;
        }

        synchronized (this.tagAdditionLock) {
            named = this.tags.get(key);
            if (named != null) {
                return named;
            }

            named = this.createTag(key);
            Map<TagKey<T>, HolderSet.Named<T>> map = new IdentityHashMap<>(this.tags);
            map.put(key, named);
            this.tags = map;
            return named;
        }
    }

    private HolderSet.Named<T> createTag(TagKey<T> key) {
        return new HolderSet.Named<>(this.holderOwner(), key);
    }

    @Override
    public Stream<TagKey<T>> getTagNames() {
        return this.tags.keySet().stream();
    }

    @Override
    public boolean isEmpty() {
        return this.byKey.isEmpty();
    }

    @Override
    public Optional<Holder.Reference<T>> getRandom(RandomSource random) {
        return Util.getRandomSafe(this.byId, random);
    }

    @Override
    public boolean containsKey(ResourceLocation name) {
        return this.byLocation.containsKey(name);
    }

    @Override
    public boolean containsKey(ResourceKey<T> key) {
        return this.byKey.containsKey(key);
    }

    @Override
    public Registry<T> freeze() {
        if (this.frozen) {
            return this;
        }

        this.frozen = true;
        this.byValue.forEach((object, reference) -> reference.bindValue((T)object));
        List<ResourceLocation> list = this.byKey
            .entrySet()
            .stream()
            .filter(entry -> !entry.getValue().isBound())
            .map(entry -> entry.getKey().location())
            .sorted()
            .toList();
        if (!list.isEmpty()) {
            throw new IllegalStateException("Unbound values in registry " + this.key() + ": " + list);
        }

        if (this.unregisteredIntrusiveHolders != null) {
            if (!this.unregisteredIntrusiveHolders.isEmpty()) {
                throw new IllegalStateException("Some intrusive holders were not registered: " + this.unregisteredIntrusiveHolders.values());
            }

            this.unregisteredIntrusiveHolders = null;
        }

        return this;
    }

    @Override
    public Holder.Reference<T> createIntrusiveHolder(T value) {
        if (this.unregisteredIntrusiveHolders == null) {
            throw new IllegalStateException("This registry can't create intrusive holders");
        }

        this.validateWrite();
        return this.unregisteredIntrusiveHolders.computeIfAbsent(value, object -> Holder.Reference.createIntrusive(this.asLookup(), (T)object));
    }

    @Override
    public Optional<HolderSet.Named<T>> getTag(TagKey<T> key) {
        return Optional.ofNullable(this.tags.get(key));
    }

    @Override
    public void bindTags(Map<TagKey<T>, List<Holder<T>>> tagMap) {
        Map<Holder.Reference<T>, List<TagKey<T>>> map = new IdentityHashMap<>();
        this.byKey.values().forEach(reference -> map.put((Holder.Reference<T>)reference, new ArrayList<>()));
        tagMap.forEach((tagKey, list) -> {
            for (Holder<T> holder : list) {
                if (!holder.canSerializeIn(this.asLookup())) {
                    throw new IllegalStateException("Can't create named set " + tagKey + " containing value " + holder + " from outside registry " + this);
                }

                if (!(holder instanceof Holder.Reference<T> reference)) {
                    throw new IllegalStateException("Found direct holder " + holder + " value in tag " + tagKey);
                }

                map.get(reference).add((TagKey<T>)tagKey);
            }
        });
        Set<TagKey<T>> set = Sets.difference(this.tags.keySet(), tagMap.keySet());
        if (!set.isEmpty()) {
            LOGGER.warn(
                "Not all defined tags for registry {} are present in data pack: {}",
                this.key(),
                set.stream().map(tagKey -> tagKey.location().toString()).sorted().collect(Collectors.joining(", "))
            );
        }

        synchronized (this.tagAdditionLock) {
            Map<TagKey<T>, HolderSet.Named<T>> map2 = new IdentityHashMap<>(this.tags);
            tagMap.forEach((tagKey, list) -> map2.computeIfAbsent((TagKey<T>)tagKey, this::createTag).bind((List<Holder<T>>)list));
            map.forEach(Holder.Reference::bindTags);
            this.tags = map2;
        }
    }

    @Override
    public void resetTags() {
        this.tags.values().forEach(named -> named.bind(List.of()));
        this.byKey.values().forEach(reference -> reference.bindTags(Set.of()));
    }

    @Override
    public HolderGetter<T> createRegistrationLookup() {
        this.validateWrite();
        return new HolderGetter<T>() {
            @Override
            public Optional<Holder.Reference<T>> get(ResourceKey<T> resourceKey) {
                return Optional.of(this.getOrThrow(resourceKey));
            }

            @Override
            public Holder.Reference<T> getOrThrow(ResourceKey<T> resourceKey) {
                return MappedRegistry.this.getOrCreateHolderOrThrow(resourceKey);
            }

            @Override
            public Optional<HolderSet.Named<T>> get(TagKey<T> tagKey) {
                return Optional.of(this.getOrThrow(tagKey));
            }

            @Override
            public HolderSet.Named<T> getOrThrow(TagKey<T> tagKey) {
                return MappedRegistry.this.getOrCreateTag(tagKey);
            }
        };
    }

    @Override
    public HolderOwner<T> holderOwner() {
        return this.lookup;
    }

    @Override
    public HolderLookup.RegistryLookup<T> asLookup() {
        return this.lookup;
    }
}
