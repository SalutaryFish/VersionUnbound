package net.minecraft.world.level.storage.loot.functions;

import com.google.common.collect.ImmutableSet;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/**
 * LootItemFunction that copies a set of block state properties to the {@code "BlockStateTag"} NBT tag of the ItemStack.
 * This tag is checked when the block is placed.
 */
public class CopyBlockState extends LootItemConditionalFunction {
    public static final MapCodec<CopyBlockState> CODEC = RecordCodecBuilder.mapCodec(
        instance -> commonFields(instance)
            .and(
                instance.group(
                    BuiltInRegistries.BLOCK.holderByNameCodec().fieldOf("block").forGetter(copyBlockState -> copyBlockState.block),
                    Codec.STRING.listOf().fieldOf("properties").forGetter(copyBlockState -> copyBlockState.properties.stream().map(Property::getName).toList())
                )
            )
            .apply(instance, CopyBlockState::new)
    );
    private final Holder<Block> block;
    private final Set<Property<?>> properties;

    CopyBlockState(List<LootItemCondition> conditions, Holder<Block> block, Set<Property<?>> properties) {
        super(conditions);
        this.block = block;
        this.properties = properties;
    }

    private CopyBlockState(List<LootItemCondition> conditions, Holder<Block> block, List<String> properties) {
        this(conditions, block, properties.stream().map(block.value().getStateDefinition()::getProperty).filter(Objects::nonNull).collect(Collectors.toSet()));
    }

    @Override
    public LootItemFunctionType<CopyBlockState> getType() {
        return LootItemFunctions.COPY_STATE;
    }

    @Override
    public Set<LootContextParam<?>> getReferencedContextParams() {
        return ImmutableSet.of(LootContextParams.BLOCK_STATE);
    }

    /**
     * Called to perform the actual action of this function, after conditions have been checked.
     */
    @Override
    protected ItemStack run(ItemStack stack, LootContext context) {
        BlockState blockState = context.getParamOrNull(LootContextParams.BLOCK_STATE);
        if (blockState != null) {
            stack.update(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY, blockItemStateProperties -> {
                for (Property<?> property : this.properties) {
                    if (blockState.hasProperty(property)) {
                        blockItemStateProperties = blockItemStateProperties.with(property, blockState);
                    }
                }

                return blockItemStateProperties;
            });
        }

        return stack;
    }

    public static CopyBlockState.Builder copyState(Block block) {
        return new CopyBlockState.Builder(block);
    }

    public static class Builder extends LootItemConditionalFunction.Builder<CopyBlockState.Builder> {
        private final Holder<Block> block;
        private final ImmutableSet.Builder<Property<?>> properties = ImmutableSet.builder();

        Builder(Block block) {
            this.block = block.builtInRegistryHolder();
        }

        public CopyBlockState.Builder copy(Property<?> property) {
            if (!this.block.value().getStateDefinition().getProperties().contains(property)) {
                throw new IllegalStateException("Property " + property + " is not present on block " + this.block);
            }

            this.properties.add(property);
            return this;
        }

        protected CopyBlockState.Builder getThis() {
            return this;
        }

        @Override
        public LootItemFunction build() {
            return new CopyBlockState(this.getConditions(), this.block, this.properties.build());
        }
    }
}
