package net.salutaryfish.versionunbound.testmod.metadata;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FireBlock;

/**
 * The mod's own access transformer (META-INF/accesstransformer.cfg) at run time. Each member is private in vanilla, so
 * without the loader applying the AT these throw {@code IllegalAccessError}, and {@link CountingFoodData} fails to load.
 */
final class AccessChecks {
    private AccessChecks() {}

    static void run(BiConsumer<String, Boolean> check) {
        check.accept("the mod's AT widens a field (no descriptor in the AT)", new FoodData().foodLevel == 20);
        int delay = FireBlock.getFireTickDelay(RandomSource.create());
        check.accept("the mod's AT widens a private static method", delay >= 30 && delay < 40);
        check.accept("the mod's AT widens a field to protected (public here)", ((FireBlock) Blocks.FIRE).igniteOdds.getInt(Blocks.OAK_PLANKS) == 5);
        // add is private in vanilla and called by eat(int, float): the override is reached only if the AT made it
        // non-final and turned that invokespecial into invokevirtual.
        var food = new CountingFoodData();
        food.eat(1, 0.5F);
        check.accept("an override of a method the mod's AT widened is called by vanilla", food.adds.get() == 1 && food.getFoodLevel() == 20);
    }

    private static final class CountingFoodData extends FoodData {
        final AtomicInteger adds = new AtomicInteger();

        @Override
        public void add(int food, float saturation) {
            adds.incrementAndGet();
            super.add(food, saturation);
        }
    }
}
