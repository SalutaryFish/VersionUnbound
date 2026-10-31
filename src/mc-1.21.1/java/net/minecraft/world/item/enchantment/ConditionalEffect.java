package net.minecraft.world.item.enchantment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.ValidationContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSet;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public record ConditionalEffect<T>(T effect, Optional<LootItemCondition> requirements) {
    public static Codec<LootItemCondition> conditionCodec(LootContextParamSet params) {
        return LootItemCondition.DIRECT_CODEC
            .validate(
                condition -> {
                    ProblemReporter.Collector collector = new ProblemReporter.Collector();
                    ValidationContext validationContext = new ValidationContext(collector, params);
                    condition.validate(validationContext);
                    return collector.getReport()
                        .map(string -> DataResult.error(() -> "Validation error in enchantment effect condition: " + string))
                        .orElseGet(() -> DataResult.success(condition));
                }
            );
    }

    public static <T> Codec<ConditionalEffect<T>> codec(Codec<T> codec, LootContextParamSet params) {
        return RecordCodecBuilder.create(
            instance -> instance.group(
                    codec.fieldOf("effect").forGetter(ConditionalEffect::effect),
                    conditionCodec(params).optionalFieldOf("requirements").forGetter(ConditionalEffect::requirements)
                )
                .apply(instance, ConditionalEffect::new)
        );
    }

    public boolean matches(LootContext context) {
        return this.requirements.isEmpty() ? true : this.requirements.get().test(context);
    }
}
