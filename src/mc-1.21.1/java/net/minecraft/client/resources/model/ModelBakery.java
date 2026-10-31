package net.minecraft.client.resources.model;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.logging.LogUtils;
import com.mojang.math.Transformation;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.ItemModelGenerator;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

public class ModelBakery {
    public static final Material FIRE_0 = new Material(TextureAtlas.LOCATION_BLOCKS, ResourceLocation.withDefaultNamespace("block/fire_0"));
    public static final Material FIRE_1 = new Material(TextureAtlas.LOCATION_BLOCKS, ResourceLocation.withDefaultNamespace("block/fire_1"));
    public static final Material LAVA_FLOW = new Material(TextureAtlas.LOCATION_BLOCKS, ResourceLocation.withDefaultNamespace("block/lava_flow"));
    public static final Material WATER_FLOW = new Material(TextureAtlas.LOCATION_BLOCKS, ResourceLocation.withDefaultNamespace("block/water_flow"));
    public static final Material WATER_OVERLAY = new Material(TextureAtlas.LOCATION_BLOCKS, ResourceLocation.withDefaultNamespace("block/water_overlay"));
    public static final Material BANNER_BASE = new Material(Sheets.BANNER_SHEET, ResourceLocation.withDefaultNamespace("entity/banner_base"));
    public static final Material SHIELD_BASE = new Material(Sheets.SHIELD_SHEET, ResourceLocation.withDefaultNamespace("entity/shield_base"));
    public static final Material NO_PATTERN_SHIELD = new Material(Sheets.SHIELD_SHEET, ResourceLocation.withDefaultNamespace("entity/shield_base_nopattern"));
    public static final int DESTROY_STAGE_COUNT = 10;
    public static final List<ResourceLocation> DESTROY_STAGES = IntStream.range(0, 10)
        .mapToObj(index -> ResourceLocation.withDefaultNamespace("block/destroy_stage_" + index))
        .collect(Collectors.toList());
    public static final List<ResourceLocation> BREAKING_LOCATIONS = DESTROY_STAGES.stream()
        .map(resourceLocation -> resourceLocation.withPath(string -> "textures/" + string + ".png"))
        .collect(Collectors.toList());
    public static final List<RenderType> DESTROY_TYPES = BREAKING_LOCATIONS.stream().map(RenderType::crumbling).collect(Collectors.toList());
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String BUILTIN_SLASH = "builtin/";
    private static final String BUILTIN_SLASH_GENERATED = "builtin/generated";
    private static final String BUILTIN_BLOCK_ENTITY = "builtin/entity";
    private static final String MISSING_MODEL_NAME = "missing";
    public static final ResourceLocation MISSING_MODEL_LOCATION = ResourceLocation.withDefaultNamespace("builtin/missing");
    public static final ModelResourceLocation MISSING_MODEL_VARIANT = new ModelResourceLocation(MISSING_MODEL_LOCATION, "missing");
    public static final FileToIdConverter MODEL_LISTER = FileToIdConverter.json("models");
    @VisibleForTesting
    public static final String MISSING_MODEL_MESH = ("{    'textures': {       'particle': '"
            + MissingTextureAtlasSprite.getLocation().getPath()
            + "',       'missingno': '"
            + MissingTextureAtlasSprite.getLocation().getPath()
            + "'    },    'elements': [         {  'from': [ 0, 0, 0 ],            'to': [ 16, 16, 16 ],            'faces': {                'down':  { 'uv': [ 0, 0, 16, 16 ], 'cullface': 'down',  'texture': '#missingno' },                'up':    { 'uv': [ 0, 0, 16, 16 ], 'cullface': 'up',    'texture': '#missingno' },                'north': { 'uv': [ 0, 0, 16, 16 ], 'cullface': 'north', 'texture': '#missingno' },                'south': { 'uv': [ 0, 0, 16, 16 ], 'cullface': 'south', 'texture': '#missingno' },                'west':  { 'uv': [ 0, 0, 16, 16 ], 'cullface': 'west',  'texture': '#missingno' },                'east':  { 'uv': [ 0, 0, 16, 16 ], 'cullface': 'east',  'texture': '#missingno' }            }        }    ]}")
        .replace('\'', '"');
    private static final Map<String, String> BUILTIN_MODELS = Map.of("missing", MISSING_MODEL_MESH);
    public static final BlockModel GENERATION_MARKER = Util.make(
        BlockModel.fromString("{\"gui_light\": \"front\"}"), blockModel -> blockModel.name = "generation marker"
    );
    public static final BlockModel BLOCK_ENTITY_MARKER = Util.make(
        BlockModel.fromString("{\"gui_light\": \"side\"}"), blockModel -> blockModel.name = "block entity marker"
    );
    static final ItemModelGenerator ITEM_MODEL_GENERATOR = new ItemModelGenerator();
    private final Map<ResourceLocation, BlockModel> modelResources;
    private final Set<ResourceLocation> loadingStack = new HashSet<>();
    private final Map<ResourceLocation, UnbakedModel> unbakedCache = new HashMap<>();
    final Map<ModelBakery.BakedCacheKey, BakedModel> bakedCache = new HashMap<>();
    private final Map<ModelResourceLocation, UnbakedModel> topLevelModels = new HashMap<>();
    private final Map<ModelResourceLocation, BakedModel> bakedTopLevelModels = new HashMap<>();
    private final UnbakedModel missingModel;
    private final Object2IntMap<BlockState> modelGroups;

    public ModelBakery(
        BlockColors blockColors,
        ProfilerFiller profilerFiller,
        Map<ResourceLocation, BlockModel> modelResources,
        Map<ResourceLocation, List<BlockStateModelLoader.LoadedJson>> blockStateResources
    ) {
        this.modelResources = modelResources;
        profilerFiller.push("missing_model");

        try {
            this.missingModel = this.loadBlockModel(MISSING_MODEL_LOCATION);
            this.registerModel(MISSING_MODEL_VARIANT, this.missingModel);
        } catch (IOException iOException) {
            LOGGER.error("Error loading missing model, should never happen :(", iOException);
            throw new RuntimeException(iOException);
        }

        BlockStateModelLoader blockStateModelLoader = new BlockStateModelLoader(
            blockStateResources, profilerFiller, this.missingModel, blockColors, this::registerModelAndLoadDependencies
        );
        blockStateModelLoader.loadAllBlockStates();
        this.modelGroups = blockStateModelLoader.getModelGroups();
        profilerFiller.popPush("items");

        for (ResourceLocation resourceLocation : BuiltInRegistries.ITEM.keySet()) {
            this.loadItemModelAndDependencies(resourceLocation);
        }

        profilerFiller.popPush("special");
        this.loadSpecialItemModelAndDependencies(ItemRenderer.TRIDENT_IN_HAND_MODEL);
        this.loadSpecialItemModelAndDependencies(ItemRenderer.SPYGLASS_IN_HAND_MODEL);
        this.topLevelModels.values().forEach(unbakedModel -> unbakedModel.resolveParents(this::getModel));
        profilerFiller.pop();
    }

    public void bakeModels(ModelBakery.TextureGetter textureGetter) {
        this.topLevelModels.forEach((modelResourceLocation, unbakedModel) -> {
            BakedModel bakedModel = null;

            try {
                bakedModel = new ModelBakery.ModelBakerImpl(textureGetter, modelResourceLocation).bakeUncached(unbakedModel, BlockModelRotation.X0_Y0);
            } catch (Exception exception) {
                LOGGER.warn("Unable to bake model: '{}': {}", modelResourceLocation, exception);
            }

            if (bakedModel != null) {
                this.bakedTopLevelModels.put(modelResourceLocation, bakedModel);
            }
        });
    }

    UnbakedModel getModel(ResourceLocation modelLocation) {
        if (this.unbakedCache.containsKey(modelLocation)) {
            return this.unbakedCache.get(modelLocation);
        }

        if (this.loadingStack.contains(modelLocation)) {
            throw new IllegalStateException("Circular reference while loading " + modelLocation);
        }

        this.loadingStack.add(modelLocation);

        while (!this.loadingStack.isEmpty()) {
            ResourceLocation resourceLocation = this.loadingStack.iterator().next();

            try {
                if (!this.unbakedCache.containsKey(resourceLocation)) {
                    UnbakedModel unbakedModel = this.loadBlockModel(resourceLocation);
                    this.unbakedCache.put(resourceLocation, unbakedModel);
                    this.loadingStack.addAll(unbakedModel.getDependencies());
                }
            } catch (Exception exception) {
                LOGGER.warn("Unable to load model: '{}' referenced from: {}: {}", resourceLocation, modelLocation, exception);
                this.unbakedCache.put(resourceLocation, this.missingModel);
            } finally {
                this.loadingStack.remove(resourceLocation);
            }
        }

        return this.unbakedCache.getOrDefault(modelLocation, this.missingModel);
    }

    private void loadItemModelAndDependencies(ResourceLocation modelLocation) {
        ModelResourceLocation modelResourceLocation = ModelResourceLocation.inventory(modelLocation);
        ResourceLocation resourceLocation = modelLocation.withPrefix("item/");
        UnbakedModel unbakedModel = this.getModel(resourceLocation);
        this.registerModelAndLoadDependencies(modelResourceLocation, unbakedModel);
    }

    private void loadSpecialItemModelAndDependencies(ModelResourceLocation modelLocation) {
        ResourceLocation resourceLocation = modelLocation.id().withPrefix("item/");
        UnbakedModel unbakedModel = this.getModel(resourceLocation);
        this.registerModelAndLoadDependencies(modelLocation, unbakedModel);
    }

    private void registerModelAndLoadDependencies(ModelResourceLocation modelLocation, UnbakedModel model) {
        for (ResourceLocation resourceLocation : model.getDependencies()) {
            this.getModel(resourceLocation);
        }

        this.registerModel(modelLocation, model);
    }

    private void registerModel(ModelResourceLocation modelLocation, UnbakedModel model) {
        this.topLevelModels.put(modelLocation, model);
    }

    private BlockModel loadBlockModel(ResourceLocation location) throws IOException {
        String string = location.getPath();
        if ("builtin/generated".equals(string)) {
            return GENERATION_MARKER;
        }

        if ("builtin/entity".equals(string)) {
            return BLOCK_ENTITY_MARKER;
        }

        if (string.startsWith("builtin/")) {
            String string2 = string.substring("builtin/".length());
            String string3 = BUILTIN_MODELS.get(string2);
            if (string3 == null) {
                throw new FileNotFoundException(location.toString());
            }

            Reader reader = new StringReader(string3);
            BlockModel blockModel = BlockModel.fromStream(reader);
            blockModel.name = location.toString();
            return blockModel;
        } else {
            ResourceLocation resourceLocation = MODEL_LISTER.idToFile(location);
            BlockModel blockModel2 = this.modelResources.get(resourceLocation);
            if (blockModel2 == null) {
                throw new FileNotFoundException(resourceLocation.toString());
            }

            blockModel2.name = location.toString();
            return blockModel2;
        }
    }

    public Map<ModelResourceLocation, BakedModel> getBakedTopLevelModels() {
        return this.bakedTopLevelModels;
    }

    public Object2IntMap<BlockState> getModelGroups() {
        return this.modelGroups;
    }

    record BakedCacheKey(ResourceLocation id, Transformation transformation, boolean isUvLocked) {
    }

    class ModelBakerImpl implements ModelBaker {
        private final Function<Material, TextureAtlasSprite> modelTextureGetter;

        ModelBakerImpl(final ModelBakery.TextureGetter textureGetter, final ModelResourceLocation modelLocation) {
            this.modelTextureGetter = material -> textureGetter.get(modelLocation, material);
        }

        @Override
        public UnbakedModel getModel(ResourceLocation location) {
            return ModelBakery.this.getModel(location);
        }

        @Override
        public BakedModel bake(ResourceLocation location, ModelState transform) {
            ModelBakery.BakedCacheKey bakedCacheKey = new ModelBakery.BakedCacheKey(location, transform.getRotation(), transform.isUvLocked());
            BakedModel bakedModel = ModelBakery.this.bakedCache.get(bakedCacheKey);
            if (bakedModel != null) {
                return bakedModel;
            }

            UnbakedModel unbakedModel = this.getModel(location);
            BakedModel bakedModel2 = this.bakeUncached(unbakedModel, transform);
            ModelBakery.this.bakedCache.put(bakedCacheKey, bakedModel2);
            return bakedModel2;
        }

        @Nullable
        BakedModel bakeUncached(UnbakedModel model, ModelState state) {
            return model instanceof BlockModel blockModel && blockModel.getRootModel() == ModelBakery.GENERATION_MARKER
                ? ModelBakery.ITEM_MODEL_GENERATOR
                    .generateBlockModel(this.modelTextureGetter, blockModel)
                    .bake(this, blockModel, this.modelTextureGetter, state, false)
                : model.bake(this, this.modelTextureGetter, state);
        }
    }

    @FunctionalInterface
    public interface TextureGetter {
        TextureAtlasSprite get(ModelResourceLocation var1, Material var2);
    }
}
