package net.minecraft.client.renderer.block.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.blaze3d.vertex.PoseStack;
import java.lang.reflect.Type;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class ItemTransform {
    public static final ItemTransform NO_TRANSFORM = new ItemTransform(new Vector3f(), new Vector3f(), new Vector3f(1.0F, 1.0F, 1.0F));
    public final Vector3f rotation;
    public final Vector3f translation;
    public final Vector3f scale;

    public ItemTransform(Vector3f rotation, Vector3f translation, Vector3f scale) {
        this.rotation = new Vector3f(rotation);
        this.translation = new Vector3f(translation);
        this.scale = new Vector3f(scale);
    }

    public void apply(boolean leftHand, PoseStack poseStack) {
        if (this != NO_TRANSFORM) {
            float f = this.rotation.x();
            float g = this.rotation.y();
            float h = this.rotation.z();
            if (leftHand) {
                g = -g;
                h = -h;
            }

            int i = leftHand ? -1 : 1;
            poseStack.translate(i * this.translation.x(), this.translation.y(), this.translation.z());
            poseStack.mulPose(new Quaternionf().rotationXYZ(f * (float) (Math.PI / 180.0), g * (float) (Math.PI / 180.0), h * (float) (Math.PI / 180.0)));
            poseStack.scale(this.scale.x(), this.scale.y(), this.scale.z());
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (this.getClass() != other.getClass()) {
            return false;
        }

        ItemTransform itemTransform = (ItemTransform)other;
        return this.rotation.equals(itemTransform.rotation) && this.scale.equals(itemTransform.scale) && this.translation.equals(itemTransform.translation);
    }

    @Override
    public int hashCode() {
        int i = this.rotation.hashCode();
        i = 31 * i + this.translation.hashCode();
        return 31 * i + this.scale.hashCode();
    }

    protected static class Deserializer implements JsonDeserializer<ItemTransform> {
        private static final Vector3f DEFAULT_ROTATION = new Vector3f(0.0F, 0.0F, 0.0F);
        private static final Vector3f DEFAULT_TRANSLATION = new Vector3f(0.0F, 0.0F, 0.0F);
        private static final Vector3f DEFAULT_SCALE = new Vector3f(1.0F, 1.0F, 1.0F);
        public static final float MAX_TRANSLATION = 5.0F;
        public static final float MAX_SCALE = 4.0F;

        public ItemTransform deserialize(JsonElement json, Type type, JsonDeserializationContext context) throws JsonParseException {
            JsonObject jsonObject = json.getAsJsonObject();
            Vector3f vector3f = this.getVector3f(jsonObject, "rotation", DEFAULT_ROTATION);
            Vector3f vector3f2 = this.getVector3f(jsonObject, "translation", DEFAULT_TRANSLATION);
            vector3f2.mul(0.0625F);
            vector3f2.set(Mth.clamp(vector3f2.x, -5.0F, 5.0F), Mth.clamp(vector3f2.y, -5.0F, 5.0F), Mth.clamp(vector3f2.z, -5.0F, 5.0F));
            Vector3f vector3f3 = this.getVector3f(jsonObject, "scale", DEFAULT_SCALE);
            vector3f3.set(Mth.clamp(vector3f3.x, -4.0F, 4.0F), Mth.clamp(vector3f3.y, -4.0F, 4.0F), Mth.clamp(vector3f3.z, -4.0F, 4.0F));
            return new ItemTransform(vector3f, vector3f2, vector3f3);
        }

        private Vector3f getVector3f(JsonObject json, String key, Vector3f fallback) {
            if (!json.has(key)) {
                return fallback;
            }

            JsonArray jsonArray = GsonHelper.getAsJsonArray(json, key);
            if (jsonArray.size() != 3) {
                throw new JsonParseException("Expected 3 " + key + " values, found: " + jsonArray.size());
            }

            float[] fs = new float[3];

            for (int i = 0; i < fs.length; i++) {
                fs[i] = GsonHelper.convertToFloat(jsonArray.get(i), key + "[" + i + "]");
            }

            return new Vector3f(fs[0], fs[1], fs[2]);
        }
    }
}
