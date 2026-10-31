package net.minecraft.world.level.storage.loot.entries;

import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.world.level.storage.loot.LootContext;

/**
 * Base interface for loot pool entry containers.
 * A loot pool entry container holds one or more loot pools and will expand into those.
 * Additionally, the container can either succeed or fail, based on its conditions.
 */
@FunctionalInterface
interface ComposableEntryContainer {
    /**
     * A container which always fails.
     */
    ComposableEntryContainer ALWAYS_FALSE = (lootContext, consumer) -> false;
    /**
     * A container that always succeeds.
     */
    ComposableEntryContainer ALWAYS_TRUE = (lootContext, consumer) -> true;

    /**
     * Expand this loot pool entry container by calling {@code entryConsumer} with any applicable entries
     *
     * @return whether this loot pool entry container successfully expanded or not
     */
    boolean expand(LootContext var1, Consumer<LootPoolEntry> var2);

    default ComposableEntryContainer and(ComposableEntryContainer entry) {
        Objects.requireNonNull(entry);
        return (lootContext, consumer) -> this.expand(lootContext, consumer) && entry.expand(lootContext, consumer);
    }

    default ComposableEntryContainer or(ComposableEntryContainer entry) {
        Objects.requireNonNull(entry);
        return (lootContext, consumer) -> this.expand(lootContext, consumer) || entry.expand(lootContext, consumer);
    }
}
