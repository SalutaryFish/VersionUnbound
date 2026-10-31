package net.minecraft.client.resources.model;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.io.Reader;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.Util;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.slf4j.Logger;

public class ModelManager implements PreparableReloadListener, AutoCloseable {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<ResourceLocation, ResourceLocation> VANILLA_ATLASES = Map.of(
        Sheets.BANNER_SHEET,
        ResourceLocation.withDefaultNamespace("banner_patterns"),
        Sheets.BED_SHEET,
        ResourceLocation.withDefaultNamespace("beds"),
        Sheets.CHEST_SHEET,
        ResourceLocation.withDefaultNamespace("chests"),
        Sheets.SHIELD_SHEET,
        ResourceLocation.withDefaultNamespace("shield_patterns"),
        Sheets.SIGN_SHEET,
        ResourceLocation.withDefaultNamespace("signs"),
        Sheets.SHULKER_SHEET,
        ResourceLocation.withDefaultNamespace("shulker_boxes"),
        Sheets.ARMOR_TRIMS_SHEET,
        ResourceLocation.withDefaultNamespace("armor_trims"),
        Sheets.DECORATED_POT_SHEET,
        ResourceLocation.withDefaultNamespace("decorated_pot"),
        TextureAtlas.LOCATION_BLOCKS,
        ResourceLocation.withDefaultNamespace("blocks")
    );
    private Map<ModelResourceLocation, BakedModel> bakedRegistry;
    private final AtlasSet atlases;
    private final BlockModelShaper blockModelShaper;
    private final BlockColors blockColors;
    private int maxMipmapLevels;
    private BakedModel missingModel;
    private Object2IntMap<BlockState> modelGroups;

    public ModelManager(TextureManager textureManager, BlockColors blockColors, int maxMipmapLevels) {
        this.blockColors = blockColors;
        this.maxMipmapLevels = maxMipmapLevels;
        this.blockModelShaper = new BlockModelShaper(this);
        this.atlases = new AtlasSet(VANILLA_ATLASES, textureManager);
    }

    public BakedModel getModel(ModelResourceLocation modelLocation) {
        return this.bakedRegistry.getOrDefault(modelLocation, this.missingModel);
    }

    public BakedModel getMissingModel() {
        return this.missingModel;
    }

    public BlockModelShaper getBlockModelShaper() {
        return this.blockModelShaper;
    }

    @Override
    public final CompletableFuture<Void> reload(
        PreparableReloadListener.PreparationBarrier preparationBarrier,
        ResourceManager resourceManager,
        ProfilerFiller preparationsProfiler,
        ProfilerFiller reloadProfiler,
        Executor backgroundExecutor,
        Executor gameExecutor
    ) {
        preparationsProfiler.startTick();
        CompletableFuture<Map<ResourceLocation, BlockModel>> completableFuture = loadBlockModels(resourceManager, backgroundExecutor);
        CompletableFuture<Map<ResourceLocation, List<BlockStateModelLoader.LoadedJson>>> completableFuture2 = loadBlockStates(
            resourceManager, backgroundExecutor
        );
        CompletableFuture<ModelBakery> completableFuture3 = completableFuture.thenCombineAsync(
            completableFuture2,
            (modelResources, blockStateResources) -> new ModelBakery(
                this.blockColors,
                preparationsProfiler,
                (Map<ResourceLocation, BlockModel>)modelResources,
                (Map<ResourceLocation, List<BlockStateModelLoader.LoadedJson>>)blockStateResources
            ),
            backgroundExecutor
        );
        Map<ResourceLocation, CompletableFuture<AtlasSet.StitchResult>> map = this.atlases
            .scheduleLoad(resourceManager, this.maxMipmapLevels, backgroundExecutor);
        return CompletableFuture.allOf(Stream.concat(map.values().stream(), Stream.of(completableFuture3)).toArray(CompletableFuture[]::new))
            .thenApplyAsync(
                void_ -> this.loadModels(
                    preparationsProfiler,
                    map.entrySet().stream().collect(Collectors.toMap(Entry::getKey, entry -> entry.getValue().join())),
                    completableFuture3.join()
                ),
                backgroundExecutor
            )
            .thenCompose(reloadState -> reloadState.readyForUpload.thenApply(void_ -> (ModelManager.ReloadState)reloadState))
            .thenCompose(preparationBarrier::wait)
            .thenAcceptAsync(reloadState -> this.apply(reloadState, reloadProfiler), gameExecutor);
    }

    private static CompletableFuture<Map<ResourceLocation, BlockModel>> loadBlockModels(ResourceManager resourceManager, Executor executor) {
        return CompletableFuture.<Map<ResourceLocation, Resource>>supplyAsync(() -> ModelBakery.MODEL_LISTER.listMatchingResources(resourceManager), executor)
            .thenCompose(
                modelResources -> {
                    List<CompletableFuture<Pair<ResourceLocation, BlockModel>>> list = new ArrayList<>(modelResources.size());

                    for (Entry<ResourceLocation, Resource> entry : modelResources.entrySet()) {
                        list.add(CompletableFuture.supplyAsync(() -> {
                            try (Reader reader = entry.getValue().openAsReader()) {
                                return Pair.of(entry.getKey(), BlockModel.fromStream(reader));
                            } catch (Exception exception) {
                                LOGGER.error("Failed to load model {}", entry.getKey(), exception);
                                return null;
                            }
                        }, executor));
                    }

                    return Util.sequence(list)
                        .thenApply(
                            modelResourcePairs -> modelResourcePairs.stream()
                                .filter(Objects::nonNull)
                                .collect(Collectors.toUnmodifiableMap(Pair::getFirst, Pair::getSecond))
                        );
                }
            );
    }

    private static CompletableFuture<Map<ResourceLocation, List<BlockStateModelLoader.LoadedJson>>> loadBlockStates(
        ResourceManager resourceManager, Executor executor
    ) {
        return CompletableFuture.<Map<ResourceLocation, List<Resource>>>supplyAsync(
                () -> BlockStateModelLoader.BLOCKSTATE_LISTER.listMatchingResourceStacks(resourceManager), executor
            )
            .thenCompose(
                blockStateResources -> {
                    List<CompletableFuture<Pair<ResourceLocation, List<BlockStateModelLoader.LoadedJson>>>> list = new ArrayList<>(blockStateResources.size());

                    for (Entry<ResourceLocation, List<Resource>> entry : blockStateResources.entrySet()) {
                        list.add(CompletableFuture.supplyAsync(() -> {
                            List<Resource> listx = entry.getValue();
                            List<BlockStateModelLoader.LoadedJson> list2 = new ArrayList<>(listx.size());

                            for (Resource resource : listx) {
                                try (Reader reader = resource.openAsReader()) {
                                    JsonObject jsonObject = GsonHelper.parse(reader);
                                    list2.add(new BlockStateModelLoader.LoadedJson(resource.sourcePackId(), jsonObject));
                                } catch (Exception exception) {
                                    LOGGER.error("Failed to load blockstate {} from pack {}", entry.getKey(), resource.sourcePackId(), exception);
                                }
                            }

                            return Pair.of(entry.getKey(), list2);
                        }, executor));
                    }

                    return Util.sequence(list)
                        .thenApply(
                            loadedBlockStates -> loadedBlockStates.stream()
                                .filter(Objects::nonNull)
                                .collect(Collectors.toUnmodifiableMap(Pair::getFirst, Pair::getSecond))
                        );
                }
            );
    }

    private ModelManager.ReloadState loadModels(
        ProfilerFiller profilerFiller, Map<ResourceLocation, AtlasSet.StitchResult> atlasPreparations, ModelBakery modelBakery
    ) {
        profilerFiller.push("load");
        profilerFiller.popPush("baking");
        Multimap<ModelResourceLocation, Material> multimap = HashMultimap.create();
        modelBakery.bakeModels((modelResourceLocation, material) -> {
            AtlasSet.StitchResult stitchResult = atlasPreparations.get(material.atlasLocation());
            TextureAtlasSprite textureAtlasSprite = stitchResult.getSprite(material.texture());
            if (textureAtlasSprite != null) {
                return textureAtlasSprite;
            }

            multimap.put(modelResourceLocation, material);
            return stitchResult.missing();
        });
        multimap.asMap()
            .forEach(
                (modelResourceLocation, collection) -> LOGGER.warn(
                    "Missing textures in model {}:\n{}",
                    modelResourceLocation,
                    collection.stream()
                        .sorted(Material.COMPARATOR)
                        .map(material -> "    " + material.atlasLocation() + ":" + material.texture())
                        .collect(Collectors.joining("\n"))
                )
            );
        profilerFiller.popPush("dispatch");
        Map<ModelResourceLocation, BakedModel> map = modelBakery.getBakedTopLevelModels();
        BakedModel bakedModel = map.get(ModelBakery.MISSING_MODEL_VARIANT);
        Map<BlockState, BakedModel> map2 = new IdentityHashMap<>();

        for (Block block : BuiltInRegistries.BLOCK) {
            block.getStateDefinition().getPossibleStates().forEach(possibleState -> {
                ResourceLocation resourceLocation = possibleState.getBlock().builtInRegistryHolder().key().location();
                BakedModel bakedModel2 = map.getOrDefault(BlockModelShaper.stateToModelLocation(resourceLocation, possibleState), bakedModel);
                map2.put(possibleState, bakedModel2);
            });
        }

        CompletableFuture<Void> completableFuture = CompletableFuture.allOf(
            atlasPreparations.values().stream().map(AtlasSet.StitchResult::readyForUpload).toArray(CompletableFuture[]::new)
        );
        profilerFiller.pop();
        profilerFiller.endTick();
        return new ModelManager.ReloadState(modelBakery, bakedModel, map2, atlasPreparations, completableFuture);
    }

    private void apply(ModelManager.ReloadState reloadState, ProfilerFiller profiler) {
        profiler.startTick();
        profiler.push("upload");
        reloadState.atlasPreparations.values().forEach(AtlasSet.StitchResult::upload);
        ModelBakery modelBakery = reloadState.modelBakery;
        this.bakedRegistry = modelBakery.getBakedTopLevelModels();
        this.modelGroups = modelBakery.getModelGroups();
        this.missingModel = reloadState.missingModel;
        profiler.popPush("cache");
        this.blockModelShaper.replaceCache(reloadState.modelCache);
        profiler.pop();
        profiler.endTick();
    }

    public boolean requiresRender(BlockState oldState, BlockState newState) {
        if (oldState == newState) {
            return false;
        }

        int i = this.modelGroups.getInt(oldState);
        if (i != -1) {
            int j = this.modelGroups.getInt(newState);
            if (i == j) {
                FluidState fluidState = oldState.getFluidState();
                FluidState fluidState2 = newState.getFluidState();
                return fluidState != fluidState2;
            }
        }

        return true;
    }

    public TextureAtlas getAtlas(ResourceLocation location) {
        return this.atlases.getAtlas(location);
    }

    @Override
    public void close() {
        this.atlases.close();
    }

    public void updateMaxMipLevel(int level) {
        this.maxMipmapLevels = level;
    }

    record ReloadState(
        ModelBakery modelBakery,
        BakedModel missingModel,
        Map<BlockState, BakedModel> modelCache,
        Map<ResourceLocation, AtlasSet.StitchResult> atlasPreparations,
        CompletableFuture<Void> readyForUpload
    ) {
    }
}
