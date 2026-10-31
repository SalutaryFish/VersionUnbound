package net.minecraft.core;

import com.mojang.datafixers.util.Either;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;

public interface Holder<T> {
    T value();

    boolean isBound();

    boolean is(ResourceLocation var1);

    boolean is(ResourceKey<T> var1);

    boolean is(Predicate<ResourceKey<T>> var1);

    boolean is(TagKey<T> var1);

    @Deprecated
    boolean is(Holder<T> var1);

    Stream<TagKey<T>> tags();

    Either<ResourceKey<T>, T> unwrap();

    Optional<ResourceKey<T>> unwrapKey();

    Holder.Kind kind();

    boolean canSerializeIn(HolderOwner<T> var1);

    default String getRegisteredName() {
        return this.unwrapKey().map(key -> key.location().toString()).orElse("[unregistered]");
    }

    static <T> Holder<T> direct(T value) {
        return new Holder.Direct<>(value);
    }

    record Direct<T>(T value) implements Holder<T> {
        @Override
        public boolean isBound() {
            return true;
        }

        @Override
        public boolean is(ResourceLocation resourceLocation) {
            return false;
        }

        @Override
        public boolean is(ResourceKey<T> resourceKey) {
            return false;
        }

        @Override
        public boolean is(TagKey<T> tagKey) {
            return false;
        }

        @Override
        public boolean is(Holder<T> holder) {
            return this.value.equals(holder.value());
        }

        @Override
        public boolean is(Predicate<ResourceKey<T>> predicate) {
            return false;
        }

        @Override
        public Either<ResourceKey<T>, T> unwrap() {
            return Either.right(this.value);
        }

        @Override
        public Optional<ResourceKey<T>> unwrapKey() {
            return Optional.empty();
        }

        @Override
        public Holder.Kind kind() {
            return Holder.Kind.DIRECT;
        }

        @Override
        public String toString() {
            return "Direct{" + this.value + "}";
        }

        @Override
        public boolean canSerializeIn(HolderOwner<T> holderOwner) {
            return true;
        }

        @Override
        public Stream<TagKey<T>> tags() {
            return Stream.of();
        }
    }

    enum Kind {
        REFERENCE,
        DIRECT;
    }

    class Reference<T> implements Holder<T> {
        private final HolderOwner<T> owner;
        private Set<TagKey<T>> tags = Set.of();
        private final Holder.Reference.Type type;
        @Nullable
        private ResourceKey<T> key;
        @Nullable
        private T value;

        protected Reference(Holder.Reference.Type type, HolderOwner<T> owner, @Nullable ResourceKey<T> key, @Nullable T value) {
            this.owner = owner;
            this.type = type;
            this.key = key;
            this.value = value;
        }

        public static <T> Holder.Reference<T> createStandAlone(HolderOwner<T> owner, ResourceKey<T> key) {
            return new Holder.Reference<>(Holder.Reference.Type.STAND_ALONE, owner, key, null);
        }

        @Deprecated
        public static <T> Holder.Reference<T> createIntrusive(HolderOwner<T> owner, @Nullable T value) {
            return new Holder.Reference<>(Holder.Reference.Type.INTRUSIVE, owner, null, value);
        }

        public ResourceKey<T> key() {
            if (this.key == null) {
                throw new IllegalStateException("Trying to access unbound value '" + this.value + "' from registry " + this.owner);
            } else {
                return this.key;
            }
        }

        @Override
        public T value() {
            if (this.value == null) {
                throw new IllegalStateException("Trying to access unbound value '" + this.key + "' from registry " + this.owner);
            } else {
                return this.value;
            }
        }

        @Override
        public boolean is(ResourceLocation location) {
            return this.key().location().equals(location);
        }

        @Override
        public boolean is(ResourceKey<T> resourceKey) {
            return this.key() == resourceKey;
        }

        @Override
        public boolean is(TagKey<T> tagKey) {
            return this.tags.contains(tagKey);
        }

        @Override
        public boolean is(Holder<T> holder) {
            return holder.is(this.key());
        }

        @Override
        public boolean is(Predicate<ResourceKey<T>> predicate) {
            return predicate.test(this.key());
        }

        @Override
        public boolean canSerializeIn(HolderOwner<T> owner) {
            return this.owner.canSerializeIn(owner);
        }

        @Override
        public Either<ResourceKey<T>, T> unwrap() {
            return Either.left(this.key());
        }

        @Override
        public Optional<ResourceKey<T>> unwrapKey() {
            return Optional.of(this.key());
        }

        @Override
        public Holder.Kind kind() {
            return Holder.Kind.REFERENCE;
        }

        @Override
        public boolean isBound() {
            return this.key != null && this.value != null;
        }

        void bindKey(ResourceKey<T> key) {
            if (this.key != null && key != this.key) {
                throw new IllegalStateException("Can't change holder key: existing=" + this.key + ", new=" + key);
            }

            this.key = key;
        }

        protected void bindValue(T value) {
            if (this.type == Holder.Reference.Type.INTRUSIVE && this.value != value) {
                throw new IllegalStateException("Can't change holder " + this.key + " value: existing=" + this.value + ", new=" + value);
            }

            this.value = value;
        }

        void bindTags(Collection<TagKey<T>> tags) {
            this.tags = Set.copyOf(tags);
        }

        @Override
        public Stream<TagKey<T>> tags() {
            return this.tags.stream();
        }

        @Override
        public String toString() {
            return "Reference{" + this.key + "=" + this.value + "}";
        }

        protected enum Type {
            STAND_ALONE,
            INTRUSIVE;
        }
    }
}
