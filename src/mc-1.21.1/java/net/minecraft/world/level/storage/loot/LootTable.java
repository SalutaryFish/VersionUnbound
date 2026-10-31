package net.minecraft.world.level.storage.loot;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryFileCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.functions.FunctionUserBuilder;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctions;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSet;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import org.slf4j.Logger;

public class LootTable {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final LootTable EMPTY = new LootTable(LootContextParamSets.EMPTY, Optional.empty(), List.of(), List.of());
    public static final LootContextParamSet DEFAULT_PARAM_SET = LootContextParamSets.ALL_PARAMS;
    public static final long RANDOMIZE_SEED = 0L;
    public static final Codec<LootTable> DIRECT_CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                LootContextParamSets.CODEC.lenientOptionalFieldOf("type", DEFAULT_PARAM_SET).forGetter(lootTable -> lootTable.paramSet),
                ResourceLocation.CODEC.optionalFieldOf("random_sequence").forGetter(lootTable -> lootTable.randomSequence),
                LootPool.CODEC.listOf().optionalFieldOf("pools", List.of()).forGetter(lootTable -> lootTable.pools),
                LootItemFunctions.ROOT_CODEC.listOf().optionalFieldOf("functions", List.of()).forGetter(lootTable -> lootTable.functions)
            )
            .apply(instance, LootTable::new)
    );
    public static final Codec<Holder<LootTable>> CODEC = RegistryFileCodec.create(Registries.LOOT_TABLE, DIRECT_CODEC);
    private final LootContextParamSet paramSet;
    private final Optional<ResourceLocation> randomSequence;
    private final List<LootPool> pools;
    private final List<LootItemFunction> functions;
    private final BiFunction<ItemStack, LootContext, ItemStack> compositeFunction;

    LootTable(LootContextParamSet paramSet, Optional<ResourceLocation> randomSequence, List<LootPool> pools, List<LootItemFunction> functions) {
        this.paramSet = paramSet;
        this.randomSequence = randomSequence;
        this.pools = pools;
        this.functions = functions;
        this.compositeFunction = LootItemFunctions.compose(functions);
    }

    public static Consumer<ItemStack> createStackSplitter(ServerLevel level, Consumer<ItemStack> output) {
        return itemStack -> {
            if (itemStack.isItemEnabled(level.enabledFeatures())) {
                if (itemStack.getCount() < itemStack.getMaxStackSize()) {
                    output.accept(itemStack);
                } else {
                    int i = itemStack.getCount();

                    while (i > 0) {
                        ItemStack itemStack2 = itemStack.copyWithCount(Math.min(itemStack.getMaxStackSize(), i));
                        i -= itemStack2.getCount();
                        output.accept(itemStack2);
                    }
                }
            }
        };
    }

    public void getRandomItemsRaw(LootParams params, Consumer<ItemStack> output) {
        this.getRandomItemsRaw(new LootContext.Builder(params).create(this.randomSequence), output);
    }

    /**
     * Generate items to the given Consumer, ignoring maximum stack size.
     */
    public void getRandomItemsRaw(LootContext context, Consumer<ItemStack> output) {
        LootContext.VisitedEntry<?> visitedEntry = LootContext.createVisitedEntry(this);
        if (context.pushVisitedElement(visitedEntry)) {
            Consumer<ItemStack> consumer = LootItemFunction.decorate(this.compositeFunction, output, context);

            for (LootPool lootPool : this.pools) {
                lootPool.addRandomItems(consumer, context);
            }

            context.popVisitedElement(visitedEntry);
        } else {
            LOGGER.warn("Detected infinite loop in loot tables");
        }
    }

    public void getRandomItems(LootParams params, long seed, Consumer<ItemStack> output) {
        this.getRandomItemsRaw(
            new LootContext.Builder(params).withOptionalRandomSeed(seed).create(this.randomSequence), createStackSplitter(params.getLevel(), output)
        );
    }

    public void getRandomItems(LootParams params, Consumer<ItemStack> output) {
        this.getRandomItemsRaw(params, createStackSplitter(params.getLevel(), output));
    }

    /**
     * Generate random items to the given Consumer, ensuring they do not exceed their maximum stack size.
     */
    public void getRandomItems(LootContext contextData, Consumer<ItemStack> output) {
        this.getRandomItemsRaw(contextData, createStackSplitter(contextData.getLevel(), output));
    }

    public ObjectArrayList<ItemStack> getRandomItems(LootParams params, RandomSource random) {
        return this.getRandomItems(new LootContext.Builder(params).withOptionalRandomSource(random).create(this.randomSequence));
    }

    public ObjectArrayList<ItemStack> getRandomItems(LootParams params, long seed) {
        return this.getRandomItems(new LootContext.Builder(params).withOptionalRandomSeed(seed).create(this.randomSequence));
    }

    public ObjectArrayList<ItemStack> getRandomItems(LootParams params) {
        return this.getRandomItems(new LootContext.Builder(params).create(this.randomSequence));
    }

    /**
     * Generate random items to a List.
     */
    private ObjectArrayList<ItemStack> getRandomItems(LootContext context) {
        ObjectArrayList<ItemStack> objectArrayList = new ObjectArrayList<>();
        this.getRandomItems(context, objectArrayList::add);
        return objectArrayList;
    }

    public LootContextParamSet getParamSet() {
        return this.paramSet;
    }

    /**
     * Validate this LootTable using the given ValidationContext.
     */
    public void validate(ValidationContext validator) {
        for (int i = 0; i < this.pools.size(); i++) {
            this.pools.get(i).validate(validator.forChild(".pools[" + i + "]"));
        }

        for (int i = 0; i < this.functions.size(); i++) {
            this.functions.get(i).validate(validator.forChild(".functions[" + i + "]"));
        }
    }

    public void fill(Container container, LootParams params, long seed) {
        LootContext lootContext = new LootContext.Builder(params).withOptionalRandomSeed(seed).create(this.randomSequence);
        ObjectArrayList<ItemStack> objectArrayList = this.getRandomItems(lootContext);
        RandomSource randomSource = lootContext.getRandom();
        List<Integer> list = this.getAvailableSlots(container, randomSource);
        this.shuffleAndSplitItems(objectArrayList, list.size(), randomSource);

        for (ItemStack itemStack : objectArrayList) {
            if (list.isEmpty()) {
                LOGGER.warn("Tried to over-fill a container");
                return;
            }

            if (itemStack.isEmpty()) {
                container.setItem(list.remove(list.size() - 1), ItemStack.EMPTY);
            } else {
                container.setItem(list.remove(list.size() - 1), itemStack);
            }
        }
    }

    /**
     * Shuffles items by changing their order and splitting stacks
     */
    private void shuffleAndSplitItems(ObjectArrayList<ItemStack> stacks, int emptySlotsCount, RandomSource random) {
        List<ItemStack> list = Lists.newArrayList();
        Iterator<ItemStack> iterator = stacks.iterator();

        while (iterator.hasNext()) {
            ItemStack itemStack = iterator.next();
            if (itemStack.isEmpty()) {
                iterator.remove();
            } else if (itemStack.getCount() > 1) {
                list.add(itemStack);
                iterator.remove();
            }
        }

        while (emptySlotsCount - stacks.size() - list.size() > 0 && !list.isEmpty()) {
            ItemStack itemStack2 = list.remove(Mth.nextInt(random, 0, list.size() - 1));
            int i = Mth.nextInt(random, 1, itemStack2.getCount() / 2);
            ItemStack itemStack3 = itemStack2.split(i);
            if (itemStack2.getCount() > 1 && random.nextBoolean()) {
                list.add(itemStack2);
            } else {
                stacks.add(itemStack2);
            }

            if (itemStack3.getCount() > 1 && random.nextBoolean()) {
                list.add(itemStack3);
            } else {
                stacks.add(itemStack3);
            }
        }

        stacks.addAll(list);
        Util.shuffle(stacks, random);
    }

    private List<Integer> getAvailableSlots(Container inventory, RandomSource random) {
        ObjectArrayList<Integer> objectArrayList = new ObjectArrayList<>();

        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).isEmpty()) {
                objectArrayList.add(i);
            }
        }

        Util.shuffle(objectArrayList, random);
        return objectArrayList;
    }

    public static LootTable.Builder lootTable() {
        return new LootTable.Builder();
    }

    public static class Builder implements FunctionUserBuilder<LootTable.Builder> {
        private final ImmutableList.Builder<LootPool> pools = ImmutableList.builder();
        private final ImmutableList.Builder<LootItemFunction> functions = ImmutableList.builder();
        private LootContextParamSet paramSet = LootTable.DEFAULT_PARAM_SET;
        private Optional<ResourceLocation> randomSequence = Optional.empty();

        public LootTable.Builder withPool(LootPool.Builder lootPool) {
            this.pools.add(lootPool.build());
            return this;
        }

        public LootTable.Builder setParamSet(LootContextParamSet parameterSet) {
            this.paramSet = parameterSet;
            return this;
        }

        public LootTable.Builder setRandomSequence(ResourceLocation randomSequence) {
            this.randomSequence = Optional.of(randomSequence);
            return this;
        }

        public LootTable.Builder apply(LootItemFunction.Builder functionBuilder) {
            this.functions.add(functionBuilder.build());
            return this;
        }

        public LootTable.Builder unwrap() {
            return this;
        }

        public LootTable build() {
            return new LootTable(this.paramSet, this.randomSequence, this.pools.build(), this.functions.build());
        }
    }
}
