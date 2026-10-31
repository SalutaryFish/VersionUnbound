package net.minecraft.world.level.entity;

import javax.annotation.Nullable;

public interface EntityTypeTest<B, T extends B> {
    static <B, T extends B> EntityTypeTest<B, T> forClass(Class<T> clazz) {
        return new EntityTypeTest<B, T>() {
            @Nullable
            @Override
            public T tryCast(B object) {
                return (T)(clazz.isInstance(object) ? object : null);
            }

            @Override
            public Class<? extends B> getBaseClass() {
                return clazz;
            }
        };
    }

    static <B, T extends B> EntityTypeTest<B, T> forExactClass(Class<T> clazz) {
        return new EntityTypeTest<B, T>() {
            @Nullable
            @Override
            public T tryCast(B object) {
                return (T)(clazz.equals(object.getClass()) ? object : null);
            }

            @Override
            public Class<? extends B> getBaseClass() {
                return clazz;
            }
        };
    }

    @Nullable
    T tryCast(B var1);

    Class<? extends B> getBaseClass();
}
