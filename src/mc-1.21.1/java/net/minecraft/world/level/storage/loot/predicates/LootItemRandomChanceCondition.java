package net.minecraft.world.level.storage.loot.predicates;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import net.minecraft.world.level.storage.loot.providers.number.NumberProviders;

/**
 * A LootItemCondition that succeeds with a given probability.
 */
public record LootItemRandomChanceCondition(NumberProvider chance) implements LootItemCondition {
    public static final MapCodec<LootItemRandomChanceCondition> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(NumberProviders.CODEC.fieldOf("chance").forGetter(LootItemRandomChanceCondition::chance))
            .apply(instance, LootItemRandomChanceCondition::new)
    );

    @Override
    public LootItemConditionType getType() {
        return LootItemConditions.RANDOM_CHANCE;
    }

    public boolean test(LootContext context) {
        float f = this.chance.getFloat(context);
        return context.getRandom().nextFloat() < f;
    }

    public static LootItemCondition.Builder randomChance(float chance) {
        return () -> new LootItemRandomChanceCondition(ConstantValue.exactly(chance));
    }

    public static LootItemCondition.Builder randomChance(NumberProvider chance) {
        return () -> new LootItemRandomChanceCondition(chance);
    }
}
