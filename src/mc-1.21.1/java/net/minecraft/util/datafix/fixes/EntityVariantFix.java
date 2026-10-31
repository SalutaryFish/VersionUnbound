package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.DSL.TypeReference;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import java.util.function.Function;
import java.util.function.IntFunction;

public class EntityVariantFix extends NamedEntityFix {
    private final String fieldName;
    private final IntFunction<String> idConversions;

    public EntityVariantFix(Schema outputSchema, String name, TypeReference type, String entityName, String fieldName, IntFunction<String> idConversions) {
        super(outputSchema, false, name, type, entityName);
        this.fieldName = fieldName;
        this.idConversions = idConversions;
    }

    private static <T> Dynamic<T> updateAndRename(Dynamic<T> dynamic, String fieldName, String newFieldName, Function<Dynamic<T>, Dynamic<T>> fixer) {
        return dynamic.map(
            object -> {
                DynamicOps<T> dynamicOps = dynamic.getOps();
                Function<T, T> function2 = objectx -> fixer.apply(new Dynamic<>(dynamicOps, (T)objectx)).getValue();
                return dynamicOps.get((T)object, fieldName)
                    .map(object2 -> dynamicOps.set((T)object, newFieldName, function2.apply((T)object2)))
                    .result()
                    .orElse((T)object);
            }
        );
    }

    @Override
    protected Typed<?> fix(Typed<?> typed) {
        return typed.update(
            DSL.remainderFinder(),
            dynamic -> updateAndRename(
                dynamic,
                this.fieldName,
                "variant",
                dynamicx -> DataFixUtils.orElse(
                    dynamicx.asNumber().map(number -> dynamicx.createString(this.idConversions.apply(number.intValue()))).result(), dynamicx
                )
            )
        );
    }
}
