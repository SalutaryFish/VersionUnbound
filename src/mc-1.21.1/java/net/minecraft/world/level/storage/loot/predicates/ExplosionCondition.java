package net.minecraft.world.level.storage.loot.predicates;

import com.google.common.collect.ImmutableSet;
import com.mojang.serialization.MapCodec;
import java.util.Set;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/**
 * A LootItemCondition that checks whether an item should survive from an explosion or not.
 * This condition checks the {@linkplain LootContextParams#EXPLOSION_RADIUS explosion radius loot parameter}.
 */
public class ExplosionCondition implements LootItemCondition {
    private static final ExplosionCondition INSTANCE = new ExplosionCondition();
    public static final MapCodec<ExplosionCondition> CODEC = MapCodec.unit(INSTANCE);

    private ExplosionCondition() {
    }

    @Override
    public LootItemConditionType getType() {
        return LootItemConditions.SURVIVES_EXPLOSION;
    }

    @Override
    public Set<LootContextParam<?>> getReferencedContextParams() {
        return ImmutableSet.of(LootContextParams.EXPLOSION_RADIUS);
    }

    public boolean test(LootContext context) {
        Float float_ = context.getParamOrNull(LootContextParams.EXPLOSION_RADIUS);
        if (float_ != null) {
            RandomSource randomSource = context.getRandom();
            float f = 1.0F / float_;
            return randomSource.nextFloat() <= f;
        } else {
            return true;
        }
    }

    public static LootItemCondition.Builder survivesExplosion() {
        return () -> INSTANCE;
    }
}
