package net.minecraft.client.renderer.block.model;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

public class ItemOverride {
    private final ResourceLocation model;
    private final List<ItemOverride.Predicate> predicates;

    public ItemOverride(ResourceLocation model, List<ItemOverride.Predicate> predicates) {
        this.model = model;
        this.predicates = ImmutableList.copyOf(predicates);
    }

    public ResourceLocation getModel() {
        return this.model;
    }

    public Stream<ItemOverride.Predicate> getPredicates() {
        return this.predicates.stream();
    }

    protected static class Deserializer implements JsonDeserializer<ItemOverride> {
        public ItemOverride deserialize(JsonElement json, Type type, JsonDeserializationContext context) throws JsonParseException {
            JsonObject jsonObject = json.getAsJsonObject();
            ResourceLocation resourceLocation = ResourceLocation.parse(GsonHelper.getAsString(jsonObject, "model"));
            List<ItemOverride.Predicate> list = this.getPredicates(jsonObject);
            return new ItemOverride(resourceLocation, list);
        }

        protected List<ItemOverride.Predicate> getPredicates(JsonObject json) {
            Map<ResourceLocation, Float> map = Maps.newLinkedHashMap();
            JsonObject jsonObject = GsonHelper.getAsJsonObject(json, "predicate");

            for (Entry<String, JsonElement> entry : jsonObject.entrySet()) {
                map.put(ResourceLocation.parse(entry.getKey()), GsonHelper.convertToFloat(entry.getValue(), entry.getKey()));
            }

            return map.entrySet()
                .stream()
                .map(predicate -> new ItemOverride.Predicate(predicate.getKey(), predicate.getValue()))
                .collect(ImmutableList.toImmutableList());
        }
    }

    public static class Predicate {
        private final ResourceLocation property;
        private final float value;

        public Predicate(ResourceLocation property, float value) {
            this.property = property;
            this.value = value;
        }

        public ResourceLocation getProperty() {
            return this.property;
        }

        public float getValue() {
            return this.value;
        }
    }
}
