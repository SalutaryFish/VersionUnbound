package net.minecraft.client.resources.sounds;

import com.google.common.collect.Lists;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.lang.reflect.Type;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.valueproviders.ConstantFloat;
import net.minecraft.util.valueproviders.FloatProvider;
import org.apache.commons.lang3.Validate;

public class SoundEventRegistrationSerializer implements JsonDeserializer<SoundEventRegistration> {
    private static final FloatProvider DEFAULT_FLOAT = ConstantFloat.of(1.0F);

    public SoundEventRegistration deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
        JsonObject jsonObject = GsonHelper.convertToJsonObject(jsonElement, "entry");
        boolean bl = GsonHelper.getAsBoolean(jsonObject, "replace", false);
        String string = GsonHelper.getAsString(jsonObject, "subtitle", null);
        List<Sound> list = this.getSounds(jsonObject);
        return new SoundEventRegistration(list, bl, string);
    }

    private List<Sound> getSounds(JsonObject object) {
        List<Sound> list = Lists.newArrayList();
        if (object.has("sounds")) {
            JsonArray jsonArray = GsonHelper.getAsJsonArray(object, "sounds");

            for (int i = 0; i < jsonArray.size(); i++) {
                JsonElement jsonElement = jsonArray.get(i);
                if (GsonHelper.isStringValue(jsonElement)) {
                    ResourceLocation resourceLocation = ResourceLocation.parse(GsonHelper.convertToString(jsonElement, "sound"));
                    list.add(new Sound(resourceLocation, DEFAULT_FLOAT, DEFAULT_FLOAT, 1, Sound.Type.FILE, false, false, 16));
                } else {
                    list.add(this.getSound(GsonHelper.convertToJsonObject(jsonElement, "sound")));
                }
            }
        }

        return list;
    }

    private Sound getSound(JsonObject object) {
        ResourceLocation resourceLocation = ResourceLocation.parse(GsonHelper.getAsString(object, "name"));
        Sound.Type type = this.getType(object, Sound.Type.FILE);
        float f = GsonHelper.getAsFloat(object, "volume", 1.0F);
        Validate.isTrue(f > 0.0F, "Invalid volume");
        float g = GsonHelper.getAsFloat(object, "pitch", 1.0F);
        Validate.isTrue(g > 0.0F, "Invalid pitch");
        int i = GsonHelper.getAsInt(object, "weight", 1);
        Validate.isTrue(i > 0, "Invalid weight");
        boolean bl = GsonHelper.getAsBoolean(object, "preload", false);
        boolean bl2 = GsonHelper.getAsBoolean(object, "stream", false);
        int j = GsonHelper.getAsInt(object, "attenuation_distance", 16);
        return new Sound(resourceLocation, ConstantFloat.of(f), ConstantFloat.of(g), i, type, bl2, bl, j);
    }

    private Sound.Type getType(JsonObject object, Sound.Type defaultValue) {
        Sound.Type type = defaultValue;
        if (object.has("type")) {
            type = Sound.Type.getByName(GsonHelper.getAsString(object, "type"));
            Validate.notNull(type, "Invalid type");
        }

        return type;
    }
}
