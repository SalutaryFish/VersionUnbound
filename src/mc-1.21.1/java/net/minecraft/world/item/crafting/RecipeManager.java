package net.minecraft.world.item.crafting;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.ImmutableMultimap.Builder;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

public class RecipeManager extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Logger LOGGER = LogUtils.getLogger();
    private final HolderLookup.Provider registries;
    private Multimap<RecipeType<?>, RecipeHolder<?>> byType = ImmutableMultimap.of();
    private Map<ResourceLocation, RecipeHolder<?>> byName = ImmutableMap.of();
    private boolean hasErrors;

    public RecipeManager(HolderLookup.Provider registries) {
        super(GSON, Registries.elementsDirPath(Registries.RECIPE));
        this.registries = registries;
    }

    protected void apply(Map<ResourceLocation, JsonElement> object, ResourceManager resourceManager, ProfilerFiller profiler) {
        this.hasErrors = false;
        Builder<RecipeType<?>, RecipeHolder<?>> builder = ImmutableMultimap.builder();
        com.google.common.collect.ImmutableMap.Builder<ResourceLocation, RecipeHolder<?>> builder2 = ImmutableMap.builder();
        RegistryOps<JsonElement> registryOps = this.registries.createSerializationContext(JsonOps.INSTANCE);

        for (Entry<ResourceLocation, JsonElement> entry : object.entrySet()) {
            ResourceLocation resourceLocation = entry.getKey();

            try {
                Recipe<?> recipe = Recipe.CODEC.parse(registryOps, entry.getValue()).getOrThrow(JsonParseException::new);
                RecipeHolder<?> recipeHolder = new RecipeHolder<>(resourceLocation, recipe);
                builder.put(recipe.getType(), recipeHolder);
                builder2.put(resourceLocation, recipeHolder);
            } catch (JsonParseException | IllegalArgumentException runtimeException) {
                LOGGER.error("Parsing error loading recipe {}", resourceLocation, runtimeException);
            }
        }

        this.byType = builder.build();
        this.byName = builder2.build();
        LOGGER.info("Loaded {} recipes", this.byType.size());
    }

    public boolean hadErrorsLoading() {
        return this.hasErrors;
    }

    public <I extends RecipeInput, T extends Recipe<I>> Optional<RecipeHolder<T>> getRecipeFor(RecipeType<T> recipeType, I input, Level level) {
        return this.getRecipeFor(recipeType, input, level, (RecipeHolder<T>)null);
    }

    public <I extends RecipeInput, T extends Recipe<I>> Optional<RecipeHolder<T>> getRecipeFor(
        RecipeType<T> recipeType, I input, Level level, @Nullable ResourceLocation lastRecipe
    ) {
        RecipeHolder<T> recipeHolder = lastRecipe != null ? this.byKeyTyped(recipeType, lastRecipe) : null;
        return this.getRecipeFor(recipeType, input, level, recipeHolder);
    }

    public <I extends RecipeInput, T extends Recipe<I>> Optional<RecipeHolder<T>> getRecipeFor(
        RecipeType<T> recipeType, I input, Level level, @Nullable RecipeHolder<T> lastRecipe
    ) {
        if (input.isEmpty()) {
            return Optional.empty();
        } else {
            return lastRecipe != null && lastRecipe.value().matches(input, level)
                ? Optional.of(lastRecipe)
                : this.byType(recipeType).stream().filter(recipeHolder -> recipeHolder.value().matches(input, level)).findFirst();
        }
    }

    public <I extends RecipeInput, T extends Recipe<I>> List<RecipeHolder<T>> getAllRecipesFor(RecipeType<T> recipeType) {
        return List.copyOf(this.byType(recipeType));
    }

    public <I extends RecipeInput, T extends Recipe<I>> List<RecipeHolder<T>> getRecipesFor(RecipeType<T> recipeType, I input, Level level) {
        return this.byType(recipeType)
            .stream()
            .filter(recipeHolder -> recipeHolder.value().matches(input, level))
            .sorted(Comparator.comparing(recipeHolder -> recipeHolder.value().getResultItem(level.registryAccess()).getDescriptionId()))
            .collect(Collectors.toList());
    }

    private <I extends RecipeInput, T extends Recipe<I>> Collection<RecipeHolder<T>> byType(RecipeType<T> type) {
        return (Collection<RecipeHolder<T>>)this.byType.get(type);
    }

    public <I extends RecipeInput, T extends Recipe<I>> NonNullList<ItemStack> getRemainingItemsFor(RecipeType<T> recipeType, I input, Level lvel) {
        Optional<RecipeHolder<T>> optional = this.getRecipeFor(recipeType, input, lvel);
        if (optional.isPresent()) {
            return optional.get().value().getRemainingItems(input);
        }

        NonNullList<ItemStack> nonNullList = NonNullList.withSize(input.size(), ItemStack.EMPTY);

        for (int i = 0; i < nonNullList.size(); i++) {
            nonNullList.set(i, input.getItem(i));
        }

        return nonNullList;
    }

    public Optional<RecipeHolder<?>> byKey(ResourceLocation recipeId) {
        return Optional.ofNullable(this.byName.get(recipeId));
    }

    @Nullable
    private <T extends Recipe<?>> RecipeHolder<T> byKeyTyped(RecipeType<T> type, ResourceLocation name) {
        RecipeHolder<?> recipeHolder = this.byName.get(name);
        return (RecipeHolder<T>)(recipeHolder != null && recipeHolder.value().getType().equals(type) ? recipeHolder : null);
    }

    public Collection<RecipeHolder<?>> getOrderedRecipes() {
        return this.byType.values();
    }

    public Collection<RecipeHolder<?>> getRecipes() {
        return this.byName.values();
    }

    public Stream<ResourceLocation> getRecipeIds() {
        return this.byName.keySet().stream();
    }

    @VisibleForTesting
    protected static RecipeHolder<?> fromJson(ResourceLocation recipeId, JsonObject json, HolderLookup.Provider registries) {
        Recipe<?> recipe = Recipe.CODEC.parse(registries.createSerializationContext(JsonOps.INSTANCE), json).getOrThrow(JsonParseException::new);
        return new RecipeHolder<>(recipeId, recipe);
    }

    public void replaceRecipes(Iterable<RecipeHolder<?>> recipes) {
        this.hasErrors = false;
        Builder<RecipeType<?>, RecipeHolder<?>> builder = ImmutableMultimap.builder();
        com.google.common.collect.ImmutableMap.Builder<ResourceLocation, RecipeHolder<?>> builder2 = ImmutableMap.builder();

        for (RecipeHolder<?> recipeHolder : recipes) {
            RecipeType<?> recipeType = recipeHolder.value().getType();
            builder.put(recipeType, recipeHolder);
            builder2.put(recipeHolder.id(), recipeHolder);
        }

        this.byType = builder.build();
        this.byName = builder2.build();
    }

    public static <I extends RecipeInput, T extends Recipe<I>> RecipeManager.CachedCheck<I, T> createCheck(RecipeType<T> recipeType) {
        return new RecipeManager.CachedCheck<I, T>() {
            @Nullable
            private ResourceLocation lastRecipe;

            @Override
            public Optional<RecipeHolder<T>> getRecipeFor(I recipeInput, Level level) {
                RecipeManager recipeManager = level.getRecipeManager();
                Optional<RecipeHolder<T>> optional = recipeManager.getRecipeFor(recipeType, recipeInput, level, this.lastRecipe);
                if (optional.isPresent()) {
                    RecipeHolder<T> recipeHolder = optional.get();
                    this.lastRecipe = recipeHolder.id();
                    return Optional.of(recipeHolder);
                } else {
                    return Optional.empty();
                }
            }
        };
    }

    public interface CachedCheck<I extends RecipeInput, T extends Recipe<I>> {
        Optional<RecipeHolder<T>> getRecipeFor(I var1, Level var2);
    }
}
