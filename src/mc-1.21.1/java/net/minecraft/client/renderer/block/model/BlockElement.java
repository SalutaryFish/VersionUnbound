package net.minecraft.client.renderer.block.model;

import com.google.common.collect.Maps;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.lang.reflect.Type;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

public class BlockElement {
    private static final boolean DEFAULT_RESCALE = false;
    private static final float MIN_EXTENT = -16.0F;
    private static final float MAX_EXTENT = 32.0F;
    public final Vector3f from;
    public final Vector3f to;
    public final Map<Direction, BlockElementFace> faces;
    public final BlockElementRotation rotation;
    public final boolean shade;

    public BlockElement(Vector3f from, Vector3f to, Map<Direction, BlockElementFace> faces, @Nullable BlockElementRotation rotation, boolean shade) {
        this.from = from;
        this.to = to;
        this.faces = faces;
        this.rotation = rotation;
        this.shade = shade;
        this.fillUvs();
    }

    private void fillUvs() {
        for (Entry<Direction, BlockElementFace> entry : this.faces.entrySet()) {
            float[] fs = this.uvsByFace(entry.getKey());
            entry.getValue().uv().setMissingUv(fs);
        }
    }

    private float[] uvsByFace(Direction face) {
        switch (face) {
            case DOWN:
                return new float[]{this.from.x(), 16.0F - this.to.z(), this.to.x(), 16.0F - this.from.z()};
            case UP:
                return new float[]{this.from.x(), this.from.z(), this.to.x(), this.to.z()};
            case NORTH:
            default:
                return new float[]{16.0F - this.to.x(), 16.0F - this.to.y(), 16.0F - this.from.x(), 16.0F - this.from.y()};
            case SOUTH:
                return new float[]{this.from.x(), 16.0F - this.to.y(), this.to.x(), 16.0F - this.from.y()};
            case WEST:
                return new float[]{this.from.z(), 16.0F - this.to.y(), this.to.z(), 16.0F - this.from.y()};
            case EAST:
                return new float[]{16.0F - this.to.z(), 16.0F - this.to.y(), 16.0F - this.from.z(), 16.0F - this.from.y()};
        }
    }

    protected static class Deserializer implements JsonDeserializer<BlockElement> {
        private static final boolean DEFAULT_SHADE = true;

        public BlockElement deserialize(JsonElement json, Type type, JsonDeserializationContext context) throws JsonParseException {
            JsonObject jsonObject = json.getAsJsonObject();
            Vector3f vector3f = this.getFrom(jsonObject);
            Vector3f vector3f2 = this.getTo(jsonObject);
            BlockElementRotation blockElementRotation = this.getRotation(jsonObject);
            Map<Direction, BlockElementFace> map = this.getFaces(context, jsonObject);
            if (jsonObject.has("shade") && !GsonHelper.isBooleanValue(jsonObject, "shade")) {
                throw new JsonParseException("Expected shade to be a Boolean");
            }

            boolean bl = GsonHelper.getAsBoolean(jsonObject, "shade", true);
            return new BlockElement(vector3f, vector3f2, map, blockElementRotation, bl);
        }

        @Nullable
        private BlockElementRotation getRotation(JsonObject json) {
            BlockElementRotation blockElementRotation = null;
            if (json.has("rotation")) {
                JsonObject jsonObject = GsonHelper.getAsJsonObject(json, "rotation");
                Vector3f vector3f = this.getVector3f(jsonObject, "origin");
                vector3f.mul(0.0625F);
                Direction.Axis axis = this.getAxis(jsonObject);
                float f = this.getAngle(jsonObject);
                boolean bl = GsonHelper.getAsBoolean(jsonObject, "rescale", false);
                blockElementRotation = new BlockElementRotation(vector3f, axis, f, bl);
            }

            return blockElementRotation;
        }

        private float getAngle(JsonObject json) {
            float f = GsonHelper.getAsFloat(json, "angle");
            if (f != 0.0F && Mth.abs(f) != 22.5F && Mth.abs(f) != 45.0F) {
                throw new JsonParseException("Invalid rotation " + f + " found, only -45/-22.5/0/22.5/45 allowed");
            } else {
                return f;
            }
        }

        private Direction.Axis getAxis(JsonObject json) {
            String string = GsonHelper.getAsString(json, "axis");
            Direction.Axis axis = Direction.Axis.byName(string.toLowerCase(Locale.ROOT));
            if (axis == null) {
                throw new JsonParseException("Invalid rotation axis: " + string);
            } else {
                return axis;
            }
        }

        private Map<Direction, BlockElementFace> getFaces(JsonDeserializationContext context, JsonObject json) {
            Map<Direction, BlockElementFace> map = this.filterNullFromFaces(context, json);
            if (map.isEmpty()) {
                throw new JsonParseException("Expected between 1 and 6 unique faces, got 0");
            } else {
                return map;
            }
        }

        private Map<Direction, BlockElementFace> filterNullFromFaces(JsonDeserializationContext context, JsonObject json) {
            Map<Direction, BlockElementFace> map = Maps.newEnumMap(Direction.class);
            JsonObject jsonObject = GsonHelper.getAsJsonObject(json, "faces");

            for (Entry<String, JsonElement> entry : jsonObject.entrySet()) {
                Direction direction = this.getFacing(entry.getKey());
                map.put(direction, context.deserialize(entry.getValue(), BlockElementFace.class));
            }

            return map;
        }

        private Direction getFacing(String name) {
            Direction direction = Direction.byName(name);
            if (direction == null) {
                throw new JsonParseException("Unknown facing: " + name);
            } else {
                return direction;
            }
        }

        private Vector3f getTo(JsonObject json) {
            Vector3f vector3f = this.getVector3f(json, "to");
            if (!(vector3f.x() < -16.0F)
                && !(vector3f.y() < -16.0F)
                && !(vector3f.z() < -16.0F)
                && !(vector3f.x() > 32.0F)
                && !(vector3f.y() > 32.0F)
                && !(vector3f.z() > 32.0F)) {
                return vector3f;
            } else {
                throw new JsonParseException("'to' specifier exceeds the allowed boundaries: " + vector3f);
            }
        }

        private Vector3f getFrom(JsonObject json) {
            Vector3f vector3f = this.getVector3f(json, "from");
            if (!(vector3f.x() < -16.0F)
                && !(vector3f.y() < -16.0F)
                && !(vector3f.z() < -16.0F)
                && !(vector3f.x() > 32.0F)
                && !(vector3f.y() > 32.0F)
                && !(vector3f.z() > 32.0F)) {
                return vector3f;
            } else {
                throw new JsonParseException("'from' specifier exceeds the allowed boundaries: " + vector3f);
            }
        }

        private Vector3f getVector3f(JsonObject json, String memberName) {
            JsonArray jsonArray = GsonHelper.getAsJsonArray(json, memberName);
            if (jsonArray.size() != 3) {
                throw new JsonParseException("Expected 3 " + memberName + " values, found: " + jsonArray.size());
            }

            float[] fs = new float[3];

            for (int i = 0; i < fs.length; i++) {
                fs[i] = GsonHelper.convertToFloat(jsonArray.get(i), memberName + "[" + i + "]");
            }

            return new Vector3f(fs[0], fs[1], fs[2]);
        }
    }
}
