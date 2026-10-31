package net.minecraft.world.inventory;

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public interface ContainerLevelAccess {
    ContainerLevelAccess NULL = new ContainerLevelAccess() {
        @Override
        public <T> Optional<T> evaluate(BiFunction<Level, BlockPos, T> biFunction) {
            return Optional.empty();
        }
    };

    static ContainerLevelAccess create(Level level, BlockPos pos) {
        return new ContainerLevelAccess() {
            @Override
            public <T> Optional<T> evaluate(BiFunction<Level, BlockPos, T> biFunction) {
                return Optional.of(biFunction.apply(level, pos));
            }
        };
    }

    <T> Optional<T> evaluate(BiFunction<Level, BlockPos, T> var1);

    default <T> T evaluate(BiFunction<Level, BlockPos, T> levelPosConsumer, T defaultValue) {
        return this.evaluate(levelPosConsumer).orElse(defaultValue);
    }

    default void execute(BiConsumer<Level, BlockPos> levelPosConsumer) {
        this.evaluate((level, pos) -> {
            levelPosConsumer.accept(level, pos);
            return Optional.empty();
        });
    }
}
