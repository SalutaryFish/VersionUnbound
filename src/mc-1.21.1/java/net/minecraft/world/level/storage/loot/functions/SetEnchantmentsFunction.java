package net.minecraft.world.level.storage.loot.functions;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import net.minecraft.world.level.storage.loot.providers.number.NumberProviders;

/**
 * LootItemFunction that sets a stack's enchantments. If {@code add} is set, will add to any already existing enchantment levels instead of replacing them (ignored for enchanted books).
 */
public class SetEnchantmentsFunction extends LootItemConditionalFunction {
    public static final MapCodec<SetEnchantmentsFunction> CODEC = RecordCodecBuilder.mapCodec(
        instance -> commonFields(instance)
            .and(
                instance.group(
                    Codec.unboundedMap(Enchantment.CODEC, NumberProviders.CODEC)
                        .optionalFieldOf("enchantments", Map.of())
                        .forGetter(setEnchantmentsFunction -> setEnchantmentsFunction.enchantments),
                    Codec.BOOL.fieldOf("add").orElse(false).forGetter(setEnchantmentsFunction -> setEnchantmentsFunction.add)
                )
            )
            .apply(instance, SetEnchantmentsFunction::new)
    );
    private final Map<Holder<Enchantment>, NumberProvider> enchantments;
    private final boolean add;

    SetEnchantmentsFunction(List<LootItemCondition> conditions, Map<Holder<Enchantment>, NumberProvider> enchantments, boolean add) {
        super(conditions);
        this.enchantments = Map.copyOf(enchantments);
        this.add = add;
    }

    @Override
    public LootItemFunctionType<SetEnchantmentsFunction> getType() {
        return LootItemFunctions.SET_ENCHANTMENTS;
    }

    @Override
    public Set<LootContextParam<?>> getReferencedContextParams() {
        return this.enchantments
            .values()
            .stream()
            .flatMap(numberProvider -> numberProvider.getReferencedContextParams().stream())
            .collect(ImmutableSet.toImmutableSet());
    }

    /**
     * Called to perform the actual action of this function, after conditions have been checked.
     */
    @Override
    public ItemStack run(ItemStack stack, LootContext context) {
        if (stack.is(Items.BOOK)) {
            stack = stack.transmuteCopy(Items.ENCHANTED_BOOK);
            stack.set(DataComponents.STORED_ENCHANTMENTS, stack.remove(DataComponents.ENCHANTMENTS));
        }

        EnchantmentHelper.updateEnchantments(
            stack,
            mutable -> {
                if (this.add) {
                    this.enchantments
                        .forEach(
                            (holder, numberProvider) -> mutable.set(
                                (Holder<Enchantment>)holder, Mth.clamp(mutable.getLevel((Holder<Enchantment>)holder) + numberProvider.getInt(context), 0, 255)
                            )
                        );
                } else {
                    this.enchantments
                        .forEach((holder, numberProvider) -> mutable.set((Holder<Enchantment>)holder, Mth.clamp(numberProvider.getInt(context), 0, 255)));
                }
            }
        );
        return stack;
    }

    public static class Builder extends LootItemConditionalFunction.Builder<SetEnchantmentsFunction.Builder> {
        private final ImmutableMap.Builder<Holder<Enchantment>, NumberProvider> enchantments = ImmutableMap.builder();
        private final boolean add;

        public Builder() {
            this(false);
        }

        public Builder(boolean add) {
            this.add = add;
        }

        protected SetEnchantmentsFunction.Builder getThis() {
            return this;
        }

        public SetEnchantmentsFunction.Builder withEnchantment(Holder<Enchantment> enchantment, NumberProvider level) {
            this.enchantments.put(enchantment, level);
            return this;
        }

        @Override
        public LootItemFunction build() {
            return new SetEnchantmentsFunction(this.getConditions(), this.enchantments.build(), this.add);
        }
    }
}
