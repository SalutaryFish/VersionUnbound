package net.salutaryfish.versionunbound.neoforge.patches;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.util.random.Weighted;
import net.minecraft.util.random.WeightedList;

/**
 * NeoForge's patch to {@link WeightedList.Builder}, injected into it by the class tweaker. NeoForge keeps the entries in
 * a mutable list so they can be removed again; {@code WeightedListBuilderMixin} adds that list and exposes it through
 * {@link Entries}.
 */
public interface WeightedListBuilderExtension<E> {
    private WeightedList.Builder<E> self() {
        return (WeightedList.Builder<E>) this;
    }

    @SuppressWarnings("unchecked")
    private List<Weighted<E>> entries() {
        return ((Entries<E>) this).versionunbound$entries();
    }

    default WeightedList.Builder<E> add(Weighted<E> value) {
        this.entries().add(value);
        return this.self();
    }

    default WeightedList.Builder<E> addAll(WeightedList<E> values) {
        return this.addAll(values.unwrap());
    }

    default WeightedList.Builder<E> addAll(Collection<Weighted<E>> values) {
        this.entries().addAll(values);
        return this.self();
    }

    default WeightedList.Builder<E> remove(Weighted<E> value) {
        this.entries().remove(value);
        return this.self();
    }

    default WeightedList.Builder<E> remove(E value) {
        this.entries().removeIf(weighted -> weighted.value().equals(value));
        return this.self();
    }

    default WeightedList.Builder<E> removeIf(Predicate<Weighted<E>> filter) {
        this.entries().removeIf(filter);
        return this.self();
    }

    default List<Weighted<E>> getList() {
        return Collections.unmodifiableList(this.entries());
    }

    /**
     * The builder's entries, implemented by {@code WeightedListBuilderMixin}.
     */
    interface Entries<E> {
        List<Weighted<E>> versionunbound$entries();
    }
}
