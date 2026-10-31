package net.minecraft.data.models.blockstates;

import com.google.common.collect.Maps;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class Variant implements Supplier<JsonElement> {
    private final Map<VariantProperty<?>, VariantProperty<?>.Value> values = Maps.newLinkedHashMap();

    public <T> Variant with(VariantProperty<T> property, T value) {
        VariantProperty<?>.Value value2 = this.values.put(property, property.withValue(value));
        if (value2 != null) {
            throw new IllegalStateException("Replacing value of " + value2 + " with " + value);
        } else {
            return this;
        }
    }

    public static Variant variant() {
        return new Variant();
    }

    public static Variant merge(Variant definition1, Variant definition2) {
        Variant variant = new Variant();
        variant.values.putAll(definition1.values);
        variant.values.putAll(definition2.values);
        return variant;
    }

    public JsonElement get() {
        JsonObject jsonObject = new JsonObject();
        this.values.values().forEach(value -> value.addToVariant(jsonObject));
        return jsonObject;
    }

    public static JsonElement convertList(List<Variant> definitions) {
        if (definitions.size() == 1) {
            return definitions.get(0).get();
        }

        JsonArray jsonArray = new JsonArray();
        definitions.forEach(variant -> jsonArray.add(variant.get()));
        return jsonArray;
    }
}
