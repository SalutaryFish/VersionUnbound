package net.minecraft.advancements.critereon;

import com.google.common.collect.ImmutableMap;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.component.DataComponentPredicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

public record ItemPredicate(
    Optional<HolderSet<Item>> items, MinMaxBounds.Ints count, DataComponentPredicate components, Map<ItemSubPredicate.Type<?>, ItemSubPredicate> subPredicates
) implements Predicate<ItemStack> {
    public static final Codec<ItemPredicate> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                RegistryCodecs.homogeneousList(Registries.ITEM).optionalFieldOf("items").forGetter(ItemPredicate::items),
                MinMaxBounds.Ints.CODEC.optionalFieldOf("count", MinMaxBounds.Ints.ANY).forGetter(ItemPredicate::count),
                DataComponentPredicate.CODEC.optionalFieldOf("components", DataComponentPredicate.EMPTY).forGetter(ItemPredicate::components),
                ItemSubPredicate.CODEC.optionalFieldOf("predicates", Map.of()).forGetter(ItemPredicate::subPredicates)
            )
            .apply(instance, ItemPredicate::new)
    );

    public boolean test(ItemStack itemStack) {
        if (this.items.isPresent() && !itemStack.is(this.items.get())) {
            return false;
        }

        if (!this.count.matches(itemStack.getCount())) {
            return false;
        }

        if (!this.components.test(itemStack)) {
            return false;
        }

        for (ItemSubPredicate itemSubPredicate : this.subPredicates.values()) {
            if (!itemSubPredicate.matches(itemStack)) {
                return false;
            }
        }

        return true;
    }

    public static class Builder {
        private Optional<HolderSet<Item>> items = Optional.empty();
        private MinMaxBounds.Ints count = MinMaxBounds.Ints.ANY;
        private DataComponentPredicate components = DataComponentPredicate.EMPTY;
        private final ImmutableMap.Builder<ItemSubPredicate.Type<?>, ItemSubPredicate> subPredicates = ImmutableMap.builder();

        private Builder() {
        }

        public static ItemPredicate.Builder item() {
            return new ItemPredicate.Builder();
        }

        public ItemPredicate.Builder of(ItemLike... items) {
            this.items = Optional.of(HolderSet.direct(item -> item.asItem().builtInRegistryHolder(), items));
            return this;
        }

        public ItemPredicate.Builder of(TagKey<Item> tag) {
            this.items = Optional.of(BuiltInRegistries.ITEM.getOrCreateTag(tag));
            return this;
        }

        public ItemPredicate.Builder withCount(MinMaxBounds.Ints count) {
            this.count = count;
            return this;
        }

        public <T extends ItemSubPredicate> ItemPredicate.Builder withSubPredicate(ItemSubPredicate.Type<T> type, T itemSubPredicate) {
            this.subPredicates.put(type, itemSubPredicate);
            return this;
        }

        public ItemPredicate.Builder hasComponents(DataComponentPredicate components) {
            this.components = components;
            return this;
        }

        public ItemPredicate build() {
            return new ItemPredicate(this.items, this.count, this.components, this.subPredicates.build());
        }
    }
}
