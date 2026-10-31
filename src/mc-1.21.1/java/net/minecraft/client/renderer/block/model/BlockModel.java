package net.minecraft.client.renderer.block.model;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.datafixers.util.Either;
import com.mojang.logging.LogUtils;
import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BuiltInModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.SimpleBakedModel;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemDisplayContext;
import org.slf4j.Logger;

public class BlockModel implements UnbakedModel {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final FaceBakery FACE_BAKERY = new FaceBakery();
    @VisibleForTesting
    static final Gson GSON = new GsonBuilder()
        .registerTypeAdapter(BlockModel.class, new BlockModel.Deserializer())
        .registerTypeAdapter(BlockElement.class, new BlockElement.Deserializer())
        .registerTypeAdapter(BlockElementFace.class, new BlockElementFace.Deserializer())
        .registerTypeAdapter(BlockFaceUV.class, new BlockFaceUV.Deserializer())
        .registerTypeAdapter(ItemTransform.class, new ItemTransform.Deserializer())
        .registerTypeAdapter(ItemTransforms.class, new ItemTransforms.Deserializer())
        .registerTypeAdapter(ItemOverride.class, new ItemOverride.Deserializer())
        .create();
    private static final char REFERENCE_CHAR = '#';
    public static final String PARTICLE_TEXTURE_REFERENCE = "particle";
    private static final boolean DEFAULT_AMBIENT_OCCLUSION = true;
    private final List<BlockElement> elements;
    @Nullable
    private final BlockModel.GuiLight guiLight;
    @Nullable
    private final Boolean hasAmbientOcclusion;
    private final ItemTransforms transforms;
    private final List<ItemOverride> overrides;
    public String name = "";
    @VisibleForTesting
    protected final Map<String, Either<Material, String>> textureMap;
    @Nullable
    protected BlockModel parent;
    @Nullable
    protected ResourceLocation parentLocation;

    public static BlockModel fromStream(Reader reader) {
        return GsonHelper.fromJson(GSON, reader, BlockModel.class);
    }

    public static BlockModel fromString(String jsonString) {
        return fromStream(new StringReader(jsonString));
    }

    public BlockModel(
        @Nullable ResourceLocation parentLocation,
        List<BlockElement> elements,
        Map<String, Either<Material, String>> textureMap,
        @Nullable Boolean hasAmbientOcclusion,
        @Nullable BlockModel.GuiLight guiLight,
        ItemTransforms transforms,
        List<ItemOverride> overrides
    ) {
        this.elements = elements;
        this.hasAmbientOcclusion = hasAmbientOcclusion;
        this.guiLight = guiLight;
        this.textureMap = textureMap;
        this.parentLocation = parentLocation;
        this.transforms = transforms;
        this.overrides = overrides;
    }

    public List<BlockElement> getElements() {
        return this.elements.isEmpty() && this.parent != null ? this.parent.getElements() : this.elements;
    }

    public boolean hasAmbientOcclusion() {
        if (this.hasAmbientOcclusion != null) {
            return this.hasAmbientOcclusion;
        } else {
            return this.parent != null ? this.parent.hasAmbientOcclusion() : true;
        }
    }

    public BlockModel.GuiLight getGuiLight() {
        if (this.guiLight != null) {
            return this.guiLight;
        } else {
            return this.parent != null ? this.parent.getGuiLight() : BlockModel.GuiLight.SIDE;
        }
    }

    public boolean isResolved() {
        return this.parentLocation == null || this.parent != null && this.parent.isResolved();
    }

    public List<ItemOverride> getOverrides() {
        return this.overrides;
    }

    private ItemOverrides getItemOverrides(ModelBaker baker, BlockModel model) {
        return this.overrides.isEmpty() ? ItemOverrides.EMPTY : new ItemOverrides(baker, model, this.overrides);
    }

    @Override
    public Collection<ResourceLocation> getDependencies() {
        Set<ResourceLocation> set = Sets.newHashSet();

        for (ItemOverride itemOverride : this.overrides) {
            set.add(itemOverride.getModel());
        }

        if (this.parentLocation != null) {
            set.add(this.parentLocation);
        }

        return set;
    }

    @Override
    public void resolveParents(Function<ResourceLocation, UnbakedModel> resolver) {
        Set<UnbakedModel> set = Sets.newLinkedHashSet();

        for (BlockModel blockModel = this; blockModel.parentLocation != null && blockModel.parent == null; blockModel = blockModel.parent) {
            set.add(blockModel);
            UnbakedModel unbakedModel = resolver.apply(blockModel.parentLocation);
            if (unbakedModel == null) {
                LOGGER.warn("No parent '{}' while loading model '{}'", this.parentLocation, blockModel);
            }

            if (set.contains(unbakedModel)) {
                LOGGER.warn(
                    "Found 'parent' loop while loading model '{}' in chain: {} -> {}",
                    blockModel,
                    set.stream().map(Object::toString).collect(Collectors.joining(" -> ")),
                    this.parentLocation
                );
                unbakedModel = null;
            }

            if (unbakedModel == null) {
                blockModel.parentLocation = ModelBakery.MISSING_MODEL_LOCATION;
                unbakedModel = resolver.apply(blockModel.parentLocation);
            }

            if (!(unbakedModel instanceof BlockModel)) {
                throw new IllegalStateException("BlockModel parent has to be a block model.");
            }

            blockModel.parent = (BlockModel)unbakedModel;
        }

        this.overrides.forEach(override -> {
            UnbakedModel unbakedModelx = resolver.apply(override.getModel());
            if (!Objects.equals(unbakedModelx, this)) {
                unbakedModelx.resolveParents(resolver);
            }
        });
    }

    @Override
    public BakedModel bake(ModelBaker baker, Function<Material, TextureAtlasSprite> spriteGetter, ModelState state) {
        return this.bake(baker, this, spriteGetter, state, true);
    }

    public BakedModel bake(ModelBaker baker, BlockModel model, Function<Material, TextureAtlasSprite> spriteGetter, ModelState state, boolean guiLight3d) {
        TextureAtlasSprite textureAtlasSprite = spriteGetter.apply(this.getMaterial("particle"));
        if (this.getRootModel() == ModelBakery.BLOCK_ENTITY_MARKER) {
            return new BuiltInModel(this.getTransforms(), this.getItemOverrides(baker, model), textureAtlasSprite, this.getGuiLight().lightLikeBlock());
        }

        SimpleBakedModel.Builder builder = new SimpleBakedModel.Builder(this, this.getItemOverrides(baker, model), guiLight3d).particle(textureAtlasSprite);

        for (BlockElement blockElement : this.getElements()) {
            for (Direction direction : blockElement.faces.keySet()) {
                BlockElementFace blockElementFace = blockElement.faces.get(direction);
                TextureAtlasSprite textureAtlasSprite2 = spriteGetter.apply(this.getMaterial(blockElementFace.texture()));
                if (blockElementFace.cullForDirection() == null) {
                    builder.addUnculledFace(bakeFace(blockElement, blockElementFace, textureAtlasSprite2, direction, state));
                } else {
                    builder.addCulledFace(
                        Direction.rotate(state.getRotation().getMatrix(), blockElementFace.cullForDirection()),
                        bakeFace(blockElement, blockElementFace, textureAtlasSprite2, direction, state)
                    );
                }
            }
        }

        return builder.build();
    }

    private static BakedQuad bakeFace(BlockElement element, BlockElementFace face, TextureAtlasSprite sprite, Direction facing, ModelState state) {
        return FACE_BAKERY.bakeQuad(element.from, element.to, face, sprite, facing, state, element.rotation, element.shade);
    }

    public boolean hasTexture(String textureName) {
        return !MissingTextureAtlasSprite.getLocation().equals(this.getMaterial(textureName).texture());
    }

    public Material getMaterial(String name) {
        if (isTextureReference(name)) {
            name = name.substring(1);
        }

        List<String> list = Lists.newArrayList();

        while (true) {
            Either<Material, String> either = this.findTextureEntry(name);
            Optional<Material> optional = either.left();
            if (optional.isPresent()) {
                return optional.get();
            }

            name = either.right().get();
            if (list.contains(name)) {
                LOGGER.warn("Unable to resolve texture due to reference chain {}->{} in {}", Joiner.on("->").join(list), name, this.name);
                return new Material(TextureAtlas.LOCATION_BLOCKS, MissingTextureAtlasSprite.getLocation());
            }

            list.add(name);
        }
    }

    private Either<Material, String> findTextureEntry(String name) {
        for (BlockModel blockModel = this; blockModel != null; blockModel = blockModel.parent) {
            Either<Material, String> either = blockModel.textureMap.get(name);
            if (either != null) {
                return either;
            }
        }

        return Either.left(new Material(TextureAtlas.LOCATION_BLOCKS, MissingTextureAtlasSprite.getLocation()));
    }

    static boolean isTextureReference(String str) {
        return str.charAt(0) == '#';
    }

    public BlockModel getRootModel() {
        return this.parent == null ? this : this.parent.getRootModel();
    }

    public ItemTransforms getTransforms() {
        ItemTransform itemTransform = this.getTransform(ItemDisplayContext.THIRD_PERSON_LEFT_HAND);
        ItemTransform itemTransform2 = this.getTransform(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND);
        ItemTransform itemTransform3 = this.getTransform(ItemDisplayContext.FIRST_PERSON_LEFT_HAND);
        ItemTransform itemTransform4 = this.getTransform(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND);
        ItemTransform itemTransform5 = this.getTransform(ItemDisplayContext.HEAD);
        ItemTransform itemTransform6 = this.getTransform(ItemDisplayContext.GUI);
        ItemTransform itemTransform7 = this.getTransform(ItemDisplayContext.GROUND);
        ItemTransform itemTransform8 = this.getTransform(ItemDisplayContext.FIXED);
        return new ItemTransforms(itemTransform, itemTransform2, itemTransform3, itemTransform4, itemTransform5, itemTransform6, itemTransform7, itemTransform8);
    }

    private ItemTransform getTransform(ItemDisplayContext displayContext) {
        return this.parent != null && !this.transforms.hasTransform(displayContext)
            ? this.parent.getTransform(displayContext)
            : this.transforms.getTransform(displayContext);
    }

    @Override
    public String toString() {
        return this.name;
    }

    public static class Deserializer implements JsonDeserializer<BlockModel> {
        public BlockModel deserialize(JsonElement json, Type type, JsonDeserializationContext context) throws JsonParseException {
            JsonObject jsonObject = json.getAsJsonObject();
            List<BlockElement> list = this.getElements(context, jsonObject);
            String string = this.getParentName(jsonObject);
            Map<String, Either<Material, String>> map = this.getTextureMap(jsonObject);
            Boolean boolean_ = this.getAmbientOcclusion(jsonObject);
            ItemTransforms itemTransforms = ItemTransforms.NO_TRANSFORMS;
            if (jsonObject.has("display")) {
                JsonObject jsonObject2 = GsonHelper.getAsJsonObject(jsonObject, "display");
                itemTransforms = context.deserialize(jsonObject2, ItemTransforms.class);
            }

            List<ItemOverride> list2 = this.getOverrides(context, jsonObject);
            BlockModel.GuiLight guiLight = null;
            if (jsonObject.has("gui_light")) {
                guiLight = BlockModel.GuiLight.getByName(GsonHelper.getAsString(jsonObject, "gui_light"));
            }

            ResourceLocation resourceLocation = string.isEmpty() ? null : ResourceLocation.parse(string);
            return new BlockModel(resourceLocation, list, map, boolean_, guiLight, itemTransforms, list2);
        }

        protected List<ItemOverride> getOverrides(JsonDeserializationContext context, JsonObject json) {
            List<ItemOverride> list = Lists.newArrayList();
            if (json.has("overrides")) {
                for (JsonElement jsonElement : GsonHelper.getAsJsonArray(json, "overrides")) {
                    list.add(context.deserialize(jsonElement, ItemOverride.class));
                }
            }

            return list;
        }

        private Map<String, Either<Material, String>> getTextureMap(JsonObject json) {
            ResourceLocation resourceLocation = TextureAtlas.LOCATION_BLOCKS;
            Map<String, Either<Material, String>> map = Maps.newHashMap();
            if (json.has("textures")) {
                JsonObject jsonObject = GsonHelper.getAsJsonObject(json, "textures");

                for (Entry<String, JsonElement> entry : jsonObject.entrySet()) {
                    map.put(entry.getKey(), parseTextureLocationOrReference(resourceLocation, entry.getValue().getAsString()));
                }
            }

            return map;
        }

        private static Either<Material, String> parseTextureLocationOrReference(ResourceLocation location, String name) {
            if (BlockModel.isTextureReference(name)) {
                return Either.right(name.substring(1));
            } else {
                ResourceLocation resourceLocation = ResourceLocation.tryParse(name);
                if (resourceLocation == null) {
                    throw new JsonParseException(name + " is not valid resource location");
                } else {
                    return Either.left(new Material(location, resourceLocation));
                }
            }
        }

        private String getParentName(JsonObject json) {
            return GsonHelper.getAsString(json, "parent", "");
        }

        @Nullable
        protected Boolean getAmbientOcclusion(JsonObject json) {
            return json.has("ambientocclusion") ? GsonHelper.getAsBoolean(json, "ambientocclusion") : null;
        }

        protected List<BlockElement> getElements(JsonDeserializationContext context, JsonObject json) {
            List<BlockElement> list = Lists.newArrayList();
            if (json.has("elements")) {
                for (JsonElement jsonElement : GsonHelper.getAsJsonArray(json, "elements")) {
                    list.add(context.deserialize(jsonElement, BlockElement.class));
                }
            }

            return list;
        }
    }

    public enum GuiLight {
        FRONT("front"),
        SIDE("side");

        private final String name;

        GuiLight(final String name) {
            this.name = name;
        }

        public static BlockModel.GuiLight getByName(String name) {
            for (BlockModel.GuiLight guiLight : values()) {
                if (guiLight.name.equals(name)) {
                    return guiLight;
                }
            }

            throw new IllegalArgumentException("Invalid gui light: " + name);
        }

        public boolean lightLikeBlock() {
            return this == SIDE;
        }
    }

    public static class LoopException extends RuntimeException {
        public LoopException(String message) {
            super(message);
        }
    }
}
