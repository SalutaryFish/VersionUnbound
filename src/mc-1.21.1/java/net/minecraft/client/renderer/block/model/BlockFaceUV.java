package net.minecraft.client.renderer.block.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.lang.reflect.Type;
import javax.annotation.Nullable;
import net.minecraft.util.GsonHelper;

public class BlockFaceUV {
    public float[] uvs;
    public final int rotation;

    public BlockFaceUV(@Nullable float[] uvs, int rotation) {
        this.uvs = uvs;
        this.rotation = rotation;
    }

    public float getU(int index) {
        if (this.uvs == null) {
            throw new NullPointerException("uvs");
        }

        int i = this.getShiftedIndex(index);
        return this.uvs[i != 0 && i != 1 ? 2 : 0];
    }

    public float getV(int index) {
        if (this.uvs == null) {
            throw new NullPointerException("uvs");
        }

        int i = this.getShiftedIndex(index);
        return this.uvs[i != 0 && i != 3 ? 3 : 1];
    }

    private int getShiftedIndex(int index) {
        return (index + this.rotation / 90) % 4;
    }

    public int getReverseIndex(int index) {
        return (index + 4 - this.rotation / 90) % 4;
    }

    public void setMissingUv(float[] uvs) {
        if (this.uvs == null) {
            this.uvs = uvs;
        }
    }

    protected static class Deserializer implements JsonDeserializer<BlockFaceUV> {
        private static final int DEFAULT_ROTATION = 0;

        public BlockFaceUV deserialize(JsonElement json, Type type, JsonDeserializationContext context) throws JsonParseException {
            JsonObject jsonObject = json.getAsJsonObject();
            float[] fs = this.getUVs(jsonObject);
            int i = this.getRotation(jsonObject);
            return new BlockFaceUV(fs, i);
        }

        protected int getRotation(JsonObject json) {
            int i = GsonHelper.getAsInt(json, "rotation", 0);
            if (i >= 0 && i % 90 == 0 && i / 90 <= 3) {
                return i;
            } else {
                throw new JsonParseException("Invalid rotation " + i + " found, only 0/90/180/270 allowed");
            }
        }

        @Nullable
        private float[] getUVs(JsonObject json) {
            if (!json.has("uv")) {
                return null;
            }

            JsonArray jsonArray = GsonHelper.getAsJsonArray(json, "uv");
            if (jsonArray.size() != 4) {
                throw new JsonParseException("Expected 4 uv values, found: " + jsonArray.size());
            }

            float[] fs = new float[4];

            for (int i = 0; i < fs.length; i++) {
                fs[i] = GsonHelper.convertToFloat(jsonArray.get(i), "uv[" + i + "]");
            }

            return fs;
        }
    }
}
