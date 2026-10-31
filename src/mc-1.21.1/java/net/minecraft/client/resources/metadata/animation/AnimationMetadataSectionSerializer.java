package net.minecraft.client.resources.metadata.animation;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import javax.annotation.Nullable;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.util.GsonHelper;
import org.apache.commons.lang3.Validate;

public class AnimationMetadataSectionSerializer implements MetadataSectionSerializer<AnimationMetadataSection> {
    public AnimationMetadataSection fromJson(JsonObject json) {
        Builder<AnimationFrame> builder = ImmutableList.builder();
        int i = GsonHelper.getAsInt(json, "frametime", 1);
        if (i != 1) {
            Validate.inclusiveBetween(1L, 2147483647L, i, "Invalid default frame time");
        }

        if (json.has("frames")) {
            try {
                JsonArray jsonArray = GsonHelper.getAsJsonArray(json, "frames");

                for (int j = 0; j < jsonArray.size(); j++) {
                    JsonElement jsonElement = jsonArray.get(j);
                    AnimationFrame animationFrame = this.getFrame(j, jsonElement);
                    if (animationFrame != null) {
                        builder.add(animationFrame);
                    }
                }
            } catch (ClassCastException classCastException) {
                throw new JsonParseException("Invalid animation->frames: expected array, was " + json.get("frames"), classCastException);
            }
        }

        int k = GsonHelper.getAsInt(json, "width", -1);
        int j = GsonHelper.getAsInt(json, "height", -1);
        if (k != -1) {
            Validate.inclusiveBetween(1L, 2147483647L, k, "Invalid width");
        }

        if (j != -1) {
            Validate.inclusiveBetween(1L, 2147483647L, j, "Invalid height");
        }

        boolean bl = GsonHelper.getAsBoolean(json, "interpolate", false);
        return new AnimationMetadataSection(builder.build(), k, j, i, bl);
    }

    @Nullable
    private AnimationFrame getFrame(int frame, JsonElement element) {
        if (element.isJsonPrimitive()) {
            return new AnimationFrame(GsonHelper.convertToInt(element, "frames[" + frame + "]"));
        }

        if (element.isJsonObject()) {
            JsonObject jsonObject = GsonHelper.convertToJsonObject(element, "frames[" + frame + "]");
            int i = GsonHelper.getAsInt(jsonObject, "time", -1);
            if (jsonObject.has("time")) {
                Validate.inclusiveBetween(1L, 2147483647L, i, "Invalid frame time");
            }

            int j = GsonHelper.getAsInt(jsonObject, "index");
            Validate.inclusiveBetween(0L, 2147483647L, j, "Invalid frame index");
            return new AnimationFrame(j, i);
        } else {
            return null;
        }
    }

    @Override
    public String getMetadataSectionName() {
        return "animation";
    }
}
