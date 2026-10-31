package net.minecraft.client.searchtree;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceLocation;

public interface ResourceLocationSearchTree<T> {
    static <T> ResourceLocationSearchTree<T> empty() {
        return new ResourceLocationSearchTree<T>() {
            @Override
            public List<T> searchNamespace(String string) {
                return List.of();
            }

            @Override
            public List<T> searchPath(String string) {
                return List.of();
            }
        };
    }

    static <T> ResourceLocationSearchTree<T> create(List<T> contents, Function<T, Stream<ResourceLocation>> idGetter) {
        if (contents.isEmpty()) {
            return empty();
        }

        final SuffixArray<T> suffixArray = new SuffixArray<>();
        final SuffixArray<T> suffixArray2 = new SuffixArray<>();

        for (T object : contents) {
            idGetter.apply(object).forEach(contentsx -> {
                suffixArray.add(object, contentsx.getNamespace().toLowerCase(Locale.ROOT));
                suffixArray2.add(object, contentsx.getPath().toLowerCase(Locale.ROOT));
            });
        }

        suffixArray.generate();
        suffixArray2.generate();
        return new ResourceLocationSearchTree<T>() {
            @Override
            public List<T> searchNamespace(String string) {
                return suffixArray.search(string);
            }

            @Override
            public List<T> searchPath(String string) {
                return suffixArray2.search(string);
            }
        };
    }

    List<T> searchNamespace(String var1);

    List<T> searchPath(String var1);
}
