package net.minecraft.client.renderer.block.model;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.lang.reflect.Type;
import net.minecraft.world.item.ItemDisplayContext;

public class ItemTransforms {
    public static final ItemTransforms NO_TRANSFORMS = new ItemTransforms();
    public final ItemTransform thirdPersonLeftHand;
    public final ItemTransform thirdPersonRightHand;
    public final ItemTransform firstPersonLeftHand;
    public final ItemTransform firstPersonRightHand;
    public final ItemTransform head;
    public final ItemTransform gui;
    public final ItemTransform ground;
    public final ItemTransform fixed;

    private ItemTransforms() {
        this(
            ItemTransform.NO_TRANSFORM,
            ItemTransform.NO_TRANSFORM,
            ItemTransform.NO_TRANSFORM,
            ItemTransform.NO_TRANSFORM,
            ItemTransform.NO_TRANSFORM,
            ItemTransform.NO_TRANSFORM,
            ItemTransform.NO_TRANSFORM,
            ItemTransform.NO_TRANSFORM
        );
    }

    public ItemTransforms(ItemTransforms transforms) {
        this.thirdPersonLeftHand = transforms.thirdPersonLeftHand;
        this.thirdPersonRightHand = transforms.thirdPersonRightHand;
        this.firstPersonLeftHand = transforms.firstPersonLeftHand;
        this.firstPersonRightHand = transforms.firstPersonRightHand;
        this.head = transforms.head;
        this.gui = transforms.gui;
        this.ground = transforms.ground;
        this.fixed = transforms.fixed;
    }

    public ItemTransforms(
        ItemTransform thirdPersonLeftHand,
        ItemTransform thirdPersonRightHand,
        ItemTransform firstPersonLeftHand,
        ItemTransform firstPersonRightHand,
        ItemTransform head,
        ItemTransform gui,
        ItemTransform ground,
        ItemTransform fixed
    ) {
        this.thirdPersonLeftHand = thirdPersonLeftHand;
        this.thirdPersonRightHand = thirdPersonRightHand;
        this.firstPersonLeftHand = firstPersonLeftHand;
        this.firstPersonRightHand = firstPersonRightHand;
        this.head = head;
        this.gui = gui;
        this.ground = ground;
        this.fixed = fixed;
    }

    public ItemTransform getTransform(ItemDisplayContext displayContext) {
        return switch (displayContext) {
            case THIRD_PERSON_LEFT_HAND -> this.thirdPersonLeftHand;
            case THIRD_PERSON_RIGHT_HAND -> this.thirdPersonRightHand;
            case FIRST_PERSON_LEFT_HAND -> this.firstPersonLeftHand;
            case FIRST_PERSON_RIGHT_HAND -> this.firstPersonRightHand;
            case HEAD -> this.head;
            case GUI -> this.gui;
            case GROUND -> this.ground;
            case FIXED -> this.fixed;
            default -> ItemTransform.NO_TRANSFORM;
        };
    }

    public boolean hasTransform(ItemDisplayContext displayContext) {
        return this.getTransform(displayContext) != ItemTransform.NO_TRANSFORM;
    }

    protected static class Deserializer implements JsonDeserializer<ItemTransforms> {
        public ItemTransforms deserialize(JsonElement json, Type type, JsonDeserializationContext context) throws JsonParseException {
            JsonObject jsonObject = json.getAsJsonObject();
            ItemTransform itemTransform = this.getTransform(context, jsonObject, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND);
            ItemTransform itemTransform2 = this.getTransform(context, jsonObject, ItemDisplayContext.THIRD_PERSON_LEFT_HAND);
            if (itemTransform2 == ItemTransform.NO_TRANSFORM) {
                itemTransform2 = itemTransform;
            }

            ItemTransform itemTransform3 = this.getTransform(context, jsonObject, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND);
            ItemTransform itemTransform4 = this.getTransform(context, jsonObject, ItemDisplayContext.FIRST_PERSON_LEFT_HAND);
            if (itemTransform4 == ItemTransform.NO_TRANSFORM) {
                itemTransform4 = itemTransform3;
            }

            ItemTransform itemTransform5 = this.getTransform(context, jsonObject, ItemDisplayContext.HEAD);
            ItemTransform itemTransform6 = this.getTransform(context, jsonObject, ItemDisplayContext.GUI);
            ItemTransform itemTransform7 = this.getTransform(context, jsonObject, ItemDisplayContext.GROUND);
            ItemTransform itemTransform8 = this.getTransform(context, jsonObject, ItemDisplayContext.FIXED);
            return new ItemTransforms(
                itemTransform2, itemTransform, itemTransform4, itemTransform3, itemTransform5, itemTransform6, itemTransform7, itemTransform8
            );
        }

        private ItemTransform getTransform(JsonDeserializationContext deserializationContext, JsonObject json, ItemDisplayContext displayContext) {
            String string = displayContext.getSerializedName();
            return json.has(string) ? deserializationContext.deserialize(json.get(string), ItemTransform.class) : ItemTransform.NO_TRANSFORM;
        }
    }
}
