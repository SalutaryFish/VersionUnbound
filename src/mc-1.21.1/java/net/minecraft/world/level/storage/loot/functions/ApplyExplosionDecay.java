package net.minecraft.world.level.storage.loot.functions;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/**
 * LootItemFunction that reduces a stack's count based on the {@linkplain LootContextParams#EXPLOSION_RADIUS explosion radius}.
 */
public class ApplyExplosionDecay extends LootItemConditionalFunction {
    public static final MapCodec<ApplyExplosionDecay> CODEC = RecordCodecBuilder.mapCodec(
        instance -> commonFields(instance).apply(instance, ApplyExplosionDecay::new)
    );

    private ApplyExplosionDecay(List<LootItemCondition> conditions) {
        super(conditions);
    }

    @Override
    public LootItemFunctionType<ApplyExplosionDecay> getType() {
        return LootItemFunctions.EXPLOSION_DECAY;
    }

    /**
     * Called to perform the actual action of this function, after conditions have been checked.
     */
    @Override
    public ItemStack run(ItemStack stack, LootContext context) {
        Float float_ = context.getParamOrNull(LootContextParams.EXPLOSION_RADIUS);
        if (float_ != null) {
            RandomSource randomSource = context.getRandom();
            float f = 1.0F / float_;
            int i = stack.getCount();
            int j = 0;

            for (int k = 0; k < i; k++) {
                if (randomSource.nextFloat() <= f) {
                    j++;
                }
            }

            stack.setCount(j);
        }

        return stack;
    }

    public static LootItemConditionalFunction.Builder<?> explosionDecay() {
        return simpleBuilder(ApplyExplosionDecay::new);
    }
}
