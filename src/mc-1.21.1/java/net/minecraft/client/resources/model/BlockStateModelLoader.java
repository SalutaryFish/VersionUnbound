package net.minecraft.client.resources.model;

import com.google.common.base.Splitter;
import com.google.common.collect.Sets;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BlockModelDefinition;
import net.minecraft.client.renderer.block.model.multipart.MultiPart;
import net.minecraft.client.renderer.block.model.multipart.Selector;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import org.slf4j.Logger;

public class BlockStateModelLoader {
    private static final Logger LOGGER = LogUtils.getLogger();
    static final int SINGLETON_MODEL_GROUP = -1;
    private static final int INVISIBLE_MODEL_GROUP = 0;
    public static final FileToIdConverter BLOCKSTATE_LISTER = FileToIdConverter.json("blockstates");
    private static final Splitter COMMA_SPLITTER = Splitter.on(',');
    private static final Splitter EQUAL_SPLITTER = Splitter.on('=').limit(2);
    private static final StateDefinition<Block, BlockState> ITEM_FRAME_FAKE_DEFINITION = new StateDefinition.Builder<Block, BlockState>(Blocks.AIR)
        .add(BooleanProperty.create("map"))
        .create(Block::defaultBlockState, BlockState::new);
    private static final Map<ResourceLocation, StateDefinition<Block, BlockState>> STATIC_DEFINITIONS = Map.of(
        ResourceLocation.withDefaultNamespace("item_frame"),
        ITEM_FRAME_FAKE_DEFINITION,
        ResourceLocation.withDefaultNamespace("glow_item_frame"),
        ITEM_FRAME_FAKE_DEFINITION
    );
    private final Map<ResourceLocation, List<BlockStateModelLoader.LoadedJson>> blockStateResources;
    private final ProfilerFiller profiler;
    private final BlockColors blockColors;
    private final BiConsumer<ModelResourceLocation, UnbakedModel> discoveredModelOutput;
    private int nextModelGroup = 1;
    private final Object2IntMap<BlockState> modelGroups = Util.make(
        new Object2IntOpenHashMap<>(), object2IntOpenHashMap -> object2IntOpenHashMap.defaultReturnValue(-1)
    );
    private final BlockStateModelLoader.LoadedModel missingModel;
    private final BlockModelDefinition.Context context = new BlockModelDefinition.Context();

    public BlockStateModelLoader(
        Map<ResourceLocation, List<BlockStateModelLoader.LoadedJson>> blockStateResources,
        ProfilerFiller profiler,
        UnbakedModel missingModel,
        BlockColors blockColors,
        BiConsumer<ModelResourceLocation, UnbakedModel> discoveredModelOutput
    ) {
        this.blockStateResources = blockStateResources;
        this.profiler = profiler;
        this.blockColors = blockColors;
        this.discoveredModelOutput = discoveredModelOutput;
        BlockStateModelLoader.ModelGroupKey modelGroupKey = new BlockStateModelLoader.ModelGroupKey(List.of(missingModel), List.of());
        this.missingModel = new BlockStateModelLoader.LoadedModel(missingModel, () -> modelGroupKey);
    }

    public void loadAllBlockStates() {
        this.profiler.push("static_definitions");
        STATIC_DEFINITIONS.forEach(this::loadBlockStateDefinitions);
        this.profiler.popPush("blocks");

        for (Block block : BuiltInRegistries.BLOCK) {
            this.loadBlockStateDefinitions(block.builtInRegistryHolder().key().location(), block.getStateDefinition());
        }

        this.profiler.pop();
    }

    private void loadBlockStateDefinitions(ResourceLocation blockStateId, StateDefinition<Block, BlockState> stateDefenition) {
        this.context.setDefinition(stateDefenition);
        List<Property<?>> list = List.copyOf(this.blockColors.getColoringProperties(stateDefenition.getOwner()));
        List<BlockState> list2 = stateDefenition.getPossibleStates();
        Map<ModelResourceLocation, BlockState> map = new HashMap<>();
        list2.forEach(blockState -> map.put(BlockModelShaper.stateToModelLocation(blockStateId, blockState), blockState));
        Map<BlockState, BlockStateModelLoader.LoadedModel> map2 = new HashMap<>();
        ResourceLocation resourceLocation = BLOCKSTATE_LISTER.idToFile(blockStateId);

        try {
            for (BlockStateModelLoader.LoadedJson loadedJson : this.blockStateResources.getOrDefault(resourceLocation, List.of())) {
                BlockModelDefinition blockModelDefinition = loadedJson.parse(blockStateId, this.context);
                Map<BlockState, BlockStateModelLoader.LoadedModel> map3 = new IdentityHashMap<>();
                MultiPart multiPart;
                if (blockModelDefinition.isMultiPart()) {
                    multiPart = blockModelDefinition.getMultiPart();
                    list2.forEach(
                        blockState -> map3.put(
                            blockState,
                            new BlockStateModelLoader.LoadedModel(multiPart, () -> BlockStateModelLoader.ModelGroupKey.create(blockState, multiPart, list))
                        )
                    );
                } else {
                    multiPart = null;
                }

                blockModelDefinition.getVariants()
                    .forEach(
                        (string, multiVariant) -> {
                            try {
                                list2.stream()
                                    .filter(predicate(stateDefenition, string))
                                    .forEach(
                                        blockState -> {
                                            BlockStateModelLoader.LoadedModel loadedModel = map3.put(
                                                blockState,
                                                new BlockStateModelLoader.LoadedModel(
                                                    multiVariant, () -> BlockStateModelLoader.ModelGroupKey.create(blockState, multiVariant, list)
                                                )
                                            );
                                            if (loadedModel != null && loadedModel.model != multiPart) {
                                                map3.put(blockState, this.missingModel);
                                                throw new RuntimeException(
                                                    "Overlapping definition with: "
                                                        + blockModelDefinition.getVariants()
                                                            .entrySet()
                                                            .stream()
                                                            .filter(entry -> entry.getValue() == loadedModel.model)
                                                            .findFirst()
                                                            .get()
                                                            .getKey()
                                                );
                                            }
                                        }
                                    );
                            } catch (Exception exception) {
                                LOGGER.warn(
                                    "Exception loading blockstate definition: '{}' in resourcepack: '{}' for variant: '{}': {}",
                                    resourceLocation,
                                    loadedJson.source,
                                    string,
                                    exception.getMessage()
                                );
                            }
                        }
                    );
                map2.putAll(map3);
            }
        } catch (BlockStateModelLoader.BlockStateDefinitionException blockStateDefinitionException) {
            LOGGER.warn("{}", blockStateDefinitionException.getMessage());
        } catch (Exception exception) {
            LOGGER.warn("Exception loading blockstate definition: '{}'", resourceLocation, exception);
        } finally {
            Map<BlockStateModelLoader.ModelGroupKey, Set<BlockState>> map5 = new HashMap<>();
            map.forEach((modelResourceLocation, blockState) -> {
                BlockStateModelLoader.LoadedModel loadedModel = map2.get(blockState);
                if (loadedModel == null) {
                    LOGGER.warn("Exception loading blockstate definition: '{}' missing model for variant: '{}'", resourceLocation, modelResourceLocation);
                    loadedModel = this.missingModel;
                }

                this.discoveredModelOutput.accept(modelResourceLocation, loadedModel.model);

                try {
                    BlockStateModelLoader.ModelGroupKey modelGroupKey = loadedModel.key().get();
                    map5.computeIfAbsent(modelGroupKey, modelGroupKeyx -> Sets.newIdentityHashSet()).add(blockState);
                } catch (Exception exception) {
                    LOGGER.warn("Exception evaluating model definition: '{}'", modelResourceLocation, exception);
                }
            });
            map5.forEach((modelGroupKey, set) -> {
                Iterator<BlockState> iterator = set.iterator();

                while (iterator.hasNext()) {
                    BlockState blockState = iterator.next();
                    if (blockState.getRenderShape() != RenderShape.MODEL) {
                        iterator.remove();
                        this.modelGroups.put(blockState, 0);
                    }
                }

                if (set.size() > 1) {
                    this.registerModelGroup(set);
                }
            });
        }
    }

    private static Predicate<BlockState> predicate(StateDefinition<Block, BlockState> stateDefentition, String properties) {
        Map<Property<?>, Comparable<?>> map = new HashMap<>();

        for (String string : COMMA_SPLITTER.split(properties)) {
            Iterator<String> iterator = EQUAL_SPLITTER.split(string).iterator();
            if (iterator.hasNext()) {
                String string2 = iterator.next();
                Property<?> property = stateDefentition.getProperty(string2);
                if (property != null && iterator.hasNext()) {
                    String string3 = iterator.next();
                    Comparable<?> comparable = getValueHelper((Property<Comparable<?>>)property, string3);
                    if (comparable == null) {
                        throw new RuntimeException(
                            "Unknown value: '" + string3 + "' for blockstate property: '" + string2 + "' " + property.getPossibleValues()
                        );
                    }

                    map.put(property, comparable);
                } else if (!string2.isEmpty()) {
                    throw new RuntimeException("Unknown blockstate property: '" + string2 + "'");
                }
            }
        }

        Block block = stateDefentition.getOwner();
        return blockState -> {
            if (blockState != null && blockState.is(block)) {
                for (Entry<Property<?>, Comparable<?>> entry : map.entrySet()) {
                    if (!Objects.equals(blockState.getValue(entry.getKey()), entry.getValue())) {
                        return false;
                    }
                }

                return true;
            } else {
                return false;
            }
        };
    }

    @Nullable
    static <T extends Comparable<T>> T getValueHelper(Property<T> property, String propertyName) {
        return property.getValue(propertyName).orElse(null);
    }

    private void registerModelGroup(Iterable<BlockState> models) {
        int i = this.nextModelGroup++;
        models.forEach(blockState -> this.modelGroups.put(blockState, i));
    }

    public Object2IntMap<BlockState> getModelGroups() {
        return this.modelGroups;
    }

    static class BlockStateDefinitionException extends RuntimeException {
        public BlockStateDefinitionException(String message) {
            super(message);
        }
    }

    public record LoadedJson(String source, JsonElement data) {
        BlockModelDefinition parse(ResourceLocation blockStateId, BlockModelDefinition.Context context) {
            try {
                return BlockModelDefinition.fromJsonElement(context, this.data);
            } catch (Exception exception) {
                throw new BlockStateModelLoader.BlockStateDefinitionException(
                    String.format(
                        Locale.ROOT,
                        "Exception loading blockstate definition: '%s' in resourcepack: '%s': %s",
                        blockStateId,
                        this.source,
                        exception.getMessage()
                    )
                );
            }
        }
    }

    record LoadedModel(UnbakedModel model, Supplier<BlockStateModelLoader.ModelGroupKey> key) {
    }

    record ModelGroupKey(List<UnbakedModel> models, List<Object> coloringValues) {
        public static BlockStateModelLoader.ModelGroupKey create(BlockState state, MultiPart model, Collection<Property<?>> properties) {
            StateDefinition<Block, BlockState> stateDefinition = state.getBlock().getStateDefinition();
            List<UnbakedModel> list = model.getSelectors()
                .stream()
                .filter(selector -> selector.getPredicate(stateDefinition).test(state))
                .map(Selector::getVariant)
                .collect(Collectors.toUnmodifiableList());
            List<Object> list2 = getColoringValues(state, properties);
            return new BlockStateModelLoader.ModelGroupKey(list, list2);
        }

        public static BlockStateModelLoader.ModelGroupKey create(BlockState state, UnbakedModel model, Collection<Property<?>> properties) {
            List<Object> list = getColoringValues(state, properties);
            return new BlockStateModelLoader.ModelGroupKey(List.of(model), list);
        }

        private static List<Object> getColoringValues(BlockState state, Collection<Property<?>> properties) {
            return properties.stream().map(state::getValue).collect(Collectors.toUnmodifiableList());
        }
    }
}
