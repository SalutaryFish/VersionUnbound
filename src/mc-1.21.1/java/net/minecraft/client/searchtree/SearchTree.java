package net.minecraft.client.searchtree;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.stream.Stream;

@FunctionalInterface
public interface SearchTree<T> {
    static <T> SearchTree<T> empty() {
        return string -> List.of();
    }

    static <T> SearchTree<T> plainText(List<T> contents, Function<T, Stream<String>> filter) {
        if (contents.isEmpty()) {
            return empty();
        }

        SuffixArray<T> suffixArray = new SuffixArray<>();

        for (T object : contents) {
            filter.apply(object).forEach(string -> suffixArray.add(object, string.toLowerCase(Locale.ROOT)));
        }

        suffixArray.generate();
        return suffixArray::search;
    }

    /**
     * Searches this search tree for the given text.
     * <p>
     * If the query does not contain a {@code :}, then only {@link #byName} is searched. If it does contain a colon, both {@link #byName} and {@link #byId} are searched and the results are merged using a {@link MergingIterator}.
     * @return A list of all matching items in this search tree.
     */
    List<T> search(String var1);
}
