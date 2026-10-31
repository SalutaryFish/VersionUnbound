package net.minecraft.util.datafix;

import com.mojang.datafixers.Typed;
import com.mojang.datafixers.types.Type;
import com.mojang.serialization.Dynamic;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.IntStream;

public class ExtraDataFixUtils {
    public static Dynamic<?> fixBlockPos(Dynamic<?> data) {
        Optional<Number> optional = data.get("X").asNumber().result();
        Optional<Number> optional2 = data.get("Y").asNumber().result();
        Optional<Number> optional3 = data.get("Z").asNumber().result();
        return !optional.isEmpty() && !optional2.isEmpty() && !optional3.isEmpty()
            ? data.createIntList(IntStream.of(optional.get().intValue(), optional2.get().intValue(), optional3.get().intValue()))
            : data;
    }

    public static <T, R> Typed<R> cast(Type<R> type, Typed<T> data) {
        return new Typed<>(type, data.getOps(), (R)data.getValue());
    }

    @SafeVarargs
    public static <T> Function<Typed<?>, Typed<?>> chainAllFilters(Function<Typed<?>, Typed<?>>... filters) {
        return typed -> {
            for (Function<Typed<?>, Typed<?>> function : filters) {
                typed = function.apply(typed);
            }

            return typed;
        };
    }
}
