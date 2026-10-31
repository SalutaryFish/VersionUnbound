package net.minecraft.world.level.storage.loot.functions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SeededContainerLoot;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.ValidationContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/**
 * LootItemFunction that sets the LootTable and optionally the loot table seed on the stack's {@code BlockEntityTag}. The effect of this is that containers such as chests will receive the given LootTable when placed.
 */
public class SetContainerLootTable extends LootItemConditionalFunction {
    public static final MapCodec<SetContainerLootTable> CODEC = RecordCodecBuilder.mapCodec(
        instance -> commonFields(instance)
            .and(
                instance.group(
                    ResourceKey.codec(Registries.LOOT_TABLE).fieldOf("name").forGetter(setContainerLootTable -> setContainerLootTable.name),
                    Codec.LONG.optionalFieldOf("seed", 0L).forGetter(setContainerLootTable -> setContainerLootTable.seed),
                    BuiltInRegistries.BLOCK_ENTITY_TYPE.holderByNameCodec().fieldOf("type").forGetter(setContainerLootTable -> setContainerLootTable.type)
                )
            )
            .apply(instance, SetContainerLootTable::new)
    );
    private final ResourceKey<LootTable> name;
    private final long seed;
    private final Holder<BlockEntityType<?>> type;

    private SetContainerLootTable(List<LootItemCondition> conditions, ResourceKey<LootTable> name, long seed, Holder<BlockEntityType<?>> type) {
        super(conditions);
        this.name = name;
        this.seed = seed;
        this.type = type;
    }

    @Override
    public LootItemFunctionType<SetContainerLootTable> getType() {
        return LootItemFunctions.SET_LOOT_TABLE;
    }

    /**
     * Called to perform the actual action of this function, after conditions have been checked.
     */
    @Override
    public ItemStack run(ItemStack stack, LootContext context) {
        if (stack.isEmpty()) {
            return stack;
        }

        stack.set(DataComponents.CONTAINER_LOOT, new SeededContainerLoot(this.name, this.seed));
        return stack;
    }

    /**
     * Validate that this object is used correctly according to the given ValidationContext.
     */
    @Override
    public void validate(ValidationContext context) {
        super.validate(context);
        if (!context.allowsReferences()) {
            context.reportProblem("Uses reference to " + this.name.location() + ", but references are not allowed");
        } else {
            if (context.resolver().get(Registries.LOOT_TABLE, this.name).isEmpty()) {
                context.reportProblem("Missing loot table used for container: " + this.name.location());
            }
        }
    }

    public static LootItemConditionalFunction.Builder<?> withLootTable(BlockEntityType<?> type, ResourceKey<LootTable> toolTable) {
        return simpleBuilder(list -> new SetContainerLootTable(list, toolTable, 0L, type.builtInRegistryHolder()));
    }

    public static LootItemConditionalFunction.Builder<?> withLootTable(BlockEntityType<?> type, ResourceKey<LootTable> lootTable, long seed) {
        return simpleBuilder(list -> new SetContainerLootTable(list, lootTable, seed, type.builtInRegistryHolder()));
    }
}
