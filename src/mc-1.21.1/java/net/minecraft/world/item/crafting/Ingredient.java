package net.minecraft.world.item.crafting;

import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntComparators;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

public final class Ingredient implements Predicate<ItemStack> {
    public static final Ingredient EMPTY = new Ingredient(Stream.empty());
    public static final StreamCodec<RegistryFriendlyByteBuf, Ingredient> CONTENTS_STREAM_CODEC = ItemStack.LIST_STREAM_CODEC
        .map(list -> fromValues(list.stream().map(Ingredient.ItemValue::new)), ingredient -> Arrays.asList(ingredient.getItems()));
    private final Ingredient.Value[] values;
    @Nullable
    private ItemStack[] itemStacks;
    @Nullable
    private IntList stackingIds;
    public static final Codec<Ingredient> CODEC = codec(true);
    public static final Codec<Ingredient> CODEC_NONEMPTY = codec(false);

    private Ingredient(Stream<? extends Ingredient.Value> values) {
        this.values = values.toArray(Ingredient.Value[]::new);
    }

    private Ingredient(Ingredient.Value[] values) {
        this.values = values;
    }

    public ItemStack[] getItems() {
        if (this.itemStacks == null) {
            this.itemStacks = Arrays.stream(this.values).flatMap(value -> value.getItems().stream()).distinct().toArray(ItemStack[]::new);
        }

        return this.itemStacks;
    }

    public boolean test(@Nullable ItemStack stack) {
        if (stack == null) {
            return false;
        }

        if (this.isEmpty()) {
            return stack.isEmpty();
        }

        for (ItemStack itemStack : this.getItems()) {
            if (itemStack.is(stack.getItem())) {
                return true;
            }
        }

        return false;
    }

    public IntList getStackingIds() {
        if (this.stackingIds == null) {
            ItemStack[] itemStacks = this.getItems();
            this.stackingIds = new IntArrayList(itemStacks.length);

            for (ItemStack itemStack : itemStacks) {
                this.stackingIds.add(StackedContents.getStackingIndex(itemStack));
            }

            this.stackingIds.sort(IntComparators.NATURAL_COMPARATOR);
        }

        return this.stackingIds;
    }

    public boolean isEmpty() {
        return this.values.length == 0;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Ingredient ingredient ? Arrays.equals(this.values, ingredient.values) : false;
    }

    private static Ingredient fromValues(Stream<? extends Ingredient.Value> stream) {
        Ingredient ingredient = new Ingredient(stream);
        return ingredient.isEmpty() ? EMPTY : ingredient;
    }

    public static Ingredient of() {
        return EMPTY;
    }

    public static Ingredient of(ItemLike... items) {
        return of(Arrays.stream(items).map(ItemStack::new));
    }

    public static Ingredient of(ItemStack... stacks) {
        return of(Arrays.stream(stacks));
    }

    public static Ingredient of(Stream<ItemStack> stacks) {
        return fromValues(stacks.filter(itemStack -> !itemStack.isEmpty()).map(Ingredient.ItemValue::new));
    }

    public static Ingredient of(TagKey<Item> tag) {
        return fromValues(Stream.of(new Ingredient.TagValue(tag)));
    }

    private static Codec<Ingredient> codec(boolean allowEmpty) {
        Codec<Ingredient.Value[]> codec = Codec.list(Ingredient.Value.CODEC)
            .comapFlatMap(
                list -> !allowEmpty && list.size() < 1
                    ? DataResult.error(() -> "Item array cannot be empty, at least one item must be defined")
                    : DataResult.success(list.toArray(new Ingredient.Value[0])),
                List::of
            );
        return Codec.either(codec, Ingredient.Value.CODEC)
            .flatComapMap(
                either -> either.map(Ingredient::new, value -> new Ingredient(new Ingredient.Value[]{value})),
                ingredient -> {
                    if (ingredient.values.length == 1) {
                        return DataResult.success(Either.right(ingredient.values[0]));
                    } else {
                        return ingredient.values.length == 0 && !allowEmpty
                            ? DataResult.error(() -> "Item array cannot be empty, at least one item must be defined")
                            : DataResult.success(Either.left(ingredient.values));
                    }
                }
            );
    }

    record ItemValue(ItemStack item) implements Ingredient.Value {
        static final Codec<Ingredient.ItemValue> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(ItemStack.SIMPLE_ITEM_CODEC.fieldOf("item").forGetter(itemValue -> itemValue.item))
                .apply(instance, Ingredient.ItemValue::new)
        );

        @Override
        public boolean equals(Object other) {
            return !(other instanceof Ingredient.ItemValue itemValue)
                ? false
                : itemValue.item.getItem().equals(this.item.getItem()) && itemValue.item.getCount() == this.item.getCount();
        }

        @Override
        public Collection<ItemStack> getItems() {
            return Collections.singleton(this.item);
        }
    }

    record TagValue(TagKey<Item> tag) implements Ingredient.Value {
        static final Codec<Ingredient.TagValue> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(TagKey.codec(Registries.ITEM).fieldOf("tag").forGetter(tagValue -> tagValue.tag))
                .apply(instance, Ingredient.TagValue::new)
        );

        @Override
        public boolean equals(Object other) {
            return other instanceof Ingredient.TagValue tagValue ? tagValue.tag.location().equals(this.tag.location()) : false;
        }

        @Override
        public Collection<ItemStack> getItems() {
            List<ItemStack> list = Lists.newArrayList();

            for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(this.tag)) {
                list.add(new ItemStack(holder));
            }

            return list;
        }
    }

    interface Value {
        Codec<Ingredient.Value> CODEC = Codec.xor(Ingredient.ItemValue.CODEC, Ingredient.TagValue.CODEC)
            .xmap(either -> either.map(itemValue -> itemValue, tagValue -> tagValue), value -> {
                if (value instanceof Ingredient.TagValue tagValue) {
                    return Either.right(tagValue);
                } else if (value instanceof Ingredient.ItemValue itemValue) {
                    return Either.left(itemValue);
                } else {
                    throw new UnsupportedOperationException("This is neither an item value nor a tag value.");
                }
            });

        Collection<ItemStack> getItems();
    }
}
