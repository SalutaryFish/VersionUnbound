package net.minecraft.commands;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.mojang.brigadier.Message;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.Level;

public interface SharedSuggestionProvider {
    Collection<String> getOnlinePlayerNames();

    default Collection<String> getCustomTabSugggestions() {
        return this.getOnlinePlayerNames();
    }

    default Collection<String> getSelectedEntities() {
        return Collections.emptyList();
    }

    Collection<String> getAllTeams();

    Stream<ResourceLocation> getAvailableSounds();

    Stream<ResourceLocation> getRecipeNames();

    CompletableFuture<Suggestions> customSuggestion(CommandContext<?> var1);

    default Collection<SharedSuggestionProvider.TextCoordinates> getRelevantCoordinates() {
        return Collections.singleton(SharedSuggestionProvider.TextCoordinates.DEFAULT_GLOBAL);
    }

    default Collection<SharedSuggestionProvider.TextCoordinates> getAbsoluteCoordinates() {
        return Collections.singleton(SharedSuggestionProvider.TextCoordinates.DEFAULT_GLOBAL);
    }

    Set<ResourceKey<Level>> levels();

    RegistryAccess registryAccess();

    FeatureFlagSet enabledFeatures();

    default void suggestRegistryElements(Registry<?> registry, SharedSuggestionProvider.ElementSuggestionType type, SuggestionsBuilder builder) {
        if (type.shouldSuggestTags()) {
            suggestResource(registry.getTagNames().map(TagKey::location), builder, "#");
        }

        if (type.shouldSuggestElements()) {
            suggestResource(registry.keySet(), builder);
        }
    }

    CompletableFuture<Suggestions> suggestRegistryElements(
        ResourceKey<? extends Registry<?>> var1, SharedSuggestionProvider.ElementSuggestionType var2, SuggestionsBuilder var3, CommandContext<?> var4
    );

    boolean hasPermission(int var1);

    static <T> void filterResources(Iterable<T> resources, String input, Function<T, ResourceLocation> locationFunction, Consumer<T> resourceConsumer) {
        boolean bl = input.indexOf(58) > -1;

        for (T object : resources) {
            ResourceLocation resourceLocation = locationFunction.apply(object);
            if (bl) {
                String string = resourceLocation.toString();
                if (matchesSubStr(input, string)) {
                    resourceConsumer.accept(object);
                }
            } else if (matchesSubStr(input, resourceLocation.getNamespace())
                || resourceLocation.getNamespace().equals("minecraft") && matchesSubStr(input, resourceLocation.getPath())) {
                resourceConsumer.accept(object);
            }
        }
    }

    static <T> void filterResources(
        Iterable<T> resources, String remaining, String prefix, Function<T, ResourceLocation> locationFunction, Consumer<T> resourceConsumer
    ) {
        if (remaining.isEmpty()) {
            resources.forEach(resourceConsumer);
        } else {
            String string = Strings.commonPrefix(remaining, prefix);
            if (!string.isEmpty()) {
                String string2 = remaining.substring(string.length());
                filterResources(resources, string2, locationFunction, resourceConsumer);
            }
        }
    }

    static CompletableFuture<Suggestions> suggestResource(Iterable<ResourceLocation> resources, SuggestionsBuilder builder, String prefix) {
        String string = builder.getRemaining().toLowerCase(Locale.ROOT);
        filterResources(resources, string, prefix, resourceLocation -> resourceLocation, resourceLocation -> builder.suggest(prefix + resourceLocation));
        return builder.buildFuture();
    }

    static CompletableFuture<Suggestions> suggestResource(Stream<ResourceLocation> resources, SuggestionsBuilder builder, String prefix) {
        return suggestResource(resources::iterator, builder, prefix);
    }

    static CompletableFuture<Suggestions> suggestResource(Iterable<ResourceLocation> resources, SuggestionsBuilder builder) {
        String string = builder.getRemaining().toLowerCase(Locale.ROOT);
        filterResources(resources, string, resourceLocation -> resourceLocation, resourceLocation -> builder.suggest(resourceLocation.toString()));
        return builder.buildFuture();
    }

    static <T> CompletableFuture<Suggestions> suggestResource(
        Iterable<T> resources, SuggestionsBuilder builder, Function<T, ResourceLocation> locationFunction, Function<T, Message> suggestionFunction
    ) {
        String string = builder.getRemaining().toLowerCase(Locale.ROOT);
        filterResources(
            resources, string, locationFunction, resource -> builder.suggest(locationFunction.apply(resource).toString(), suggestionFunction.apply(resource))
        );
        return builder.buildFuture();
    }

    static CompletableFuture<Suggestions> suggestResource(Stream<ResourceLocation> resourceLocations, SuggestionsBuilder builder) {
        return suggestResource(resourceLocations::iterator, builder);
    }

    static <T> CompletableFuture<Suggestions> suggestResource(
        Stream<T> resources, SuggestionsBuilder builder, Function<T, ResourceLocation> locationFunction, Function<T, Message> suggestionFunction
    ) {
        return suggestResource(resources::iterator, builder, locationFunction, suggestionFunction);
    }

    static CompletableFuture<Suggestions> suggestCoordinates(
        String remaining, Collection<SharedSuggestionProvider.TextCoordinates> coordinates, SuggestionsBuilder builder, Predicate<String> validator
    ) {
        List<String> list = Lists.newArrayList();
        if (Strings.isNullOrEmpty(remaining)) {
            for (SharedSuggestionProvider.TextCoordinates textCoordinates : coordinates) {
                String string = textCoordinates.x + " " + textCoordinates.y + " " + textCoordinates.z;
                if (validator.test(string)) {
                    list.add(textCoordinates.x);
                    list.add(textCoordinates.x + " " + textCoordinates.y);
                    list.add(string);
                }
            }
        } else {
            String[] strings = remaining.split(" ");
            if (strings.length == 1) {
                for (SharedSuggestionProvider.TextCoordinates textCoordinates2 : coordinates) {
                    String string2 = strings[0] + " " + textCoordinates2.y + " " + textCoordinates2.z;
                    if (validator.test(string2)) {
                        list.add(strings[0] + " " + textCoordinates2.y);
                        list.add(string2);
                    }
                }
            } else if (strings.length == 2) {
                for (SharedSuggestionProvider.TextCoordinates textCoordinates2 : coordinates) {
                    String string2 = strings[0] + " " + strings[1] + " " + textCoordinates2.z;
                    if (validator.test(string2)) {
                        list.add(string2);
                    }
                }
            }
        }

        return suggest(list, builder);
    }

    static CompletableFuture<Suggestions> suggest2DCoordinates(
        String remaining, Collection<SharedSuggestionProvider.TextCoordinates> coordinates, SuggestionsBuilder builder, Predicate<String> validator
    ) {
        List<String> list = Lists.newArrayList();
        if (Strings.isNullOrEmpty(remaining)) {
            for (SharedSuggestionProvider.TextCoordinates textCoordinates : coordinates) {
                String string = textCoordinates.x + " " + textCoordinates.z;
                if (validator.test(string)) {
                    list.add(textCoordinates.x);
                    list.add(string);
                }
            }
        } else {
            String[] strings = remaining.split(" ");
            if (strings.length == 1) {
                for (SharedSuggestionProvider.TextCoordinates textCoordinates2 : coordinates) {
                    String string2 = strings[0] + " " + textCoordinates2.z;
                    if (validator.test(string2)) {
                        list.add(string2);
                    }
                }
            }
        }

        return suggest(list, builder);
    }

    static CompletableFuture<Suggestions> suggest(Iterable<String> strings, SuggestionsBuilder builder) {
        String string = builder.getRemaining().toLowerCase(Locale.ROOT);

        for (String string2 : strings) {
            if (matchesSubStr(string, string2.toLowerCase(Locale.ROOT))) {
                builder.suggest(string2);
            }
        }

        return builder.buildFuture();
    }

    static CompletableFuture<Suggestions> suggest(Stream<String> strings, SuggestionsBuilder builder) {
        String string = builder.getRemaining().toLowerCase(Locale.ROOT);
        strings.filter(stringx -> matchesSubStr(string, stringx.toLowerCase(Locale.ROOT))).forEach(builder::suggest);
        return builder.buildFuture();
    }

    static CompletableFuture<Suggestions> suggest(String[] strings, SuggestionsBuilder builder) {
        String string = builder.getRemaining().toLowerCase(Locale.ROOT);

        for (String string2 : strings) {
            if (matchesSubStr(string, string2.toLowerCase(Locale.ROOT))) {
                builder.suggest(string2);
            }
        }

        return builder.buildFuture();
    }

    static <T> CompletableFuture<Suggestions> suggest(
        Iterable<T> resources, SuggestionsBuilder builder, Function<T, String> stringFunction, Function<T, Message> suggestionFunction
    ) {
        String string = builder.getRemaining().toLowerCase(Locale.ROOT);

        for (T object : resources) {
            String string2 = stringFunction.apply(object);
            if (matchesSubStr(string, string2.toLowerCase(Locale.ROOT))) {
                builder.suggest(string2, suggestionFunction.apply(object));
            }
        }

        return builder.buildFuture();
    }

    static boolean matchesSubStr(String input, String substring) {
        for (int i = 0; !substring.startsWith(input, i); i++) {
            int j = substring.indexOf(46, i);
            int k = substring.indexOf(95, i);
            if (Math.max(j, k) < 0) {
                return false;
            }

            if (j >= 0 && k >= 0) {
                i = Math.min(k, j);
            } else {
                i = j >= 0 ? j : k;
            }
        }

        return true;
    }

    enum ElementSuggestionType {
        TAGS,
        ELEMENTS,
        ALL;

        public boolean shouldSuggestTags() {
            return this == TAGS || this == ALL;
        }

        public boolean shouldSuggestElements() {
            return this == ELEMENTS || this == ALL;
        }
    }

    class TextCoordinates {
        public static final SharedSuggestionProvider.TextCoordinates DEFAULT_LOCAL = new SharedSuggestionProvider.TextCoordinates("^", "^", "^");
        public static final SharedSuggestionProvider.TextCoordinates DEFAULT_GLOBAL = new SharedSuggestionProvider.TextCoordinates("~", "~", "~");
        public final String x;
        public final String y;
        public final String z;

        public TextCoordinates(String x, String y, String z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
