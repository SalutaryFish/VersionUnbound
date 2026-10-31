package net.minecraft.core;

import com.mojang.datafixers.util.Either;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.Spliterator;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.VisibleForTesting;

public interface HolderSet<T> extends Iterable<Holder<T>> {
    Stream<Holder<T>> stream();

    int size();

    Either<TagKey<T>, List<Holder<T>>> unwrap();

    Optional<Holder<T>> getRandomElement(RandomSource var1);

    Holder<T> get(int var1);

    boolean contains(Holder<T> var1);

    boolean canSerializeIn(HolderOwner<T> var1);

    Optional<TagKey<T>> unwrapKey();

    @Deprecated
    @VisibleForTesting
    static <T> HolderSet.Named<T> emptyNamed(HolderOwner<T> owner, TagKey<T> key) {
        return new HolderSet.Named<T>(owner, key) {
            @Override
            protected List<Holder<T>> contents() {
                throw new UnsupportedOperationException("Tag " + this.key() + " can't be dereferenced during construction");
            }
        };
    }

    static <T> HolderSet<T> empty() {
        return (HolderSet<T>)HolderSet.Direct.EMPTY;
    }

    @SafeVarargs
    static <T> HolderSet.Direct<T> direct(Holder<T>... contents) {
        return new HolderSet.Direct<>(List.of(contents));
    }

    static <T> HolderSet.Direct<T> direct(List<? extends Holder<T>> contents) {
        return new HolderSet.Direct<>(List.copyOf(contents));
    }

    @SafeVarargs
    static <E, T> HolderSet.Direct<T> direct(Function<E, Holder<T>> holderFactory, E... values) {
        return direct(Stream.of(values).map(holderFactory).toList());
    }

    static <E, T> HolderSet.Direct<T> direct(Function<E, Holder<T>> holderFactory, Collection<E> values) {
        return direct(values.stream().map(holderFactory).toList());
    }

    final class Direct<T> extends HolderSet.ListBacked<T> {
        static final HolderSet.Direct<?> EMPTY = new HolderSet.Direct(List.of());
        private final List<Holder<T>> contents;
        @Nullable
        private Set<Holder<T>> contentsSet;

        Direct(List<Holder<T>> contents) {
            this.contents = contents;
        }

        @Override
        protected List<Holder<T>> contents() {
            return this.contents;
        }

        @Override
        public Either<TagKey<T>, List<Holder<T>>> unwrap() {
            return Either.right(this.contents);
        }

        @Override
        public Optional<TagKey<T>> unwrapKey() {
            return Optional.empty();
        }

        @Override
        public boolean contains(Holder<T> holder) {
            if (this.contentsSet == null) {
                this.contentsSet = Set.copyOf(this.contents);
            }

            return this.contentsSet.contains(holder);
        }

        @Override
        public String toString() {
            return "DirectSet[" + this.contents + "]";
        }

        @Override
        public boolean equals(Object other) {
            return this == other ? true : other instanceof HolderSet.Direct<?> direct && this.contents.equals(direct.contents);
        }

        @Override
        public int hashCode() {
            return this.contents.hashCode();
        }
    }

    abstract class ListBacked<T> implements HolderSet<T> {
        protected abstract List<Holder<T>> contents();

        @Override
        public int size() {
            return this.contents().size();
        }

        @Override
        public Spliterator<Holder<T>> spliterator() {
            return this.contents().spliterator();
        }

        @Override
        public Iterator<Holder<T>> iterator() {
            return this.contents().iterator();
        }

        @Override
        public Stream<Holder<T>> stream() {
            return this.contents().stream();
        }

        @Override
        public Optional<Holder<T>> getRandomElement(RandomSource randomSource) {
            return Util.getRandomSafe(this.contents(), randomSource);
        }

        @Override
        public Holder<T> get(int i) {
            return this.contents().get(i);
        }

        @Override
        public boolean canSerializeIn(HolderOwner<T> holderOwner) {
            return true;
        }
    }

    class Named<T> extends HolderSet.ListBacked<T> {
        private final HolderOwner<T> owner;
        private final TagKey<T> key;
        private List<Holder<T>> contents = List.of();

        Named(HolderOwner<T> owner, TagKey<T> key) {
            this.owner = owner;
            this.key = key;
        }

        void bind(List<Holder<T>> contents) {
            this.contents = List.copyOf(contents);
        }

        public TagKey<T> key() {
            return this.key;
        }

        @Override
        protected List<Holder<T>> contents() {
            return this.contents;
        }

        @Override
        public Either<TagKey<T>, List<Holder<T>>> unwrap() {
            return Either.left(this.key);
        }

        @Override
        public Optional<TagKey<T>> unwrapKey() {
            return Optional.of(this.key);
        }

        @Override
        public boolean contains(Holder<T> holder) {
            return holder.is(this.key);
        }

        @Override
        public String toString() {
            return "NamedSet(" + this.key + ")[" + this.contents + "]";
        }

        @Override
        public boolean canSerializeIn(HolderOwner<T> owner) {
            return this.owner.canSerializeIn(owner);
        }
    }
}
