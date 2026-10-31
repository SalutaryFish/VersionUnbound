package net.minecraft.client.renderer.block.model;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.lang.reflect.Type;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.util.GsonHelper;

public record BlockElementFace(@Nullable Direction cullForDirection, int tintIndex, String texture, BlockFaceUV uv) {
    public static final int NO_TINT = -1;

    protected static class Deserializer implements JsonDeserializer<BlockElementFace> {
        private static final int DEFAULT_TINT_INDEX = -1;

        public BlockElementFace deserialize(JsonElement json, Type type, JsonDeserializationContext context) throws JsonParseException {
            JsonObject jsonObject = json.getAsJsonObject();
            Direction direction = this.getCullFacing(jsonObject);
            int i = this.getTintIndex(jsonObject);
            String string = this.getTexture(jsonObject);
            BlockFaceUV blockFaceUV = context.deserialize(jsonObject, BlockFaceUV.class);
            return new BlockElementFace(direction, i, string, blockFaceUV);
        }

        protected int getTintIndex(JsonObject json) {
            return GsonHelper.getAsInt(json, "tintindex", -1);
        }

        private String getTexture(JsonObject json) {
            return GsonHelper.getAsString(json, "texture");
        }

        @Nullable
        private Direction getCullFacing(JsonObject json) {
            String string = GsonHelper.getAsString(json, "cullface", "");
            return Direction.byName(string);
        }
    }
}
