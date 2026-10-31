package net.minecraft.commands.arguments;

import com.google.common.collect.Lists;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CollectionTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import org.apache.commons.lang3.mutable.MutableBoolean;

public class NbtPathArgument implements ArgumentType<NbtPathArgument.NbtPath> {
    private static final Collection<String> EXAMPLES = Arrays.asList("foo", "foo.bar", "foo[0]", "[0]", "[]", "{foo=bar}");
    public static final SimpleCommandExceptionType ERROR_INVALID_NODE = new SimpleCommandExceptionType(Component.translatable("arguments.nbtpath.node.invalid"));
    public static final SimpleCommandExceptionType ERROR_DATA_TOO_DEEP = new SimpleCommandExceptionType(Component.translatable("arguments.nbtpath.too_deep"));
    public static final DynamicCommandExceptionType ERROR_NOTHING_FOUND = new DynamicCommandExceptionType(
        path -> Component.translatableEscape("arguments.nbtpath.nothing_found", path)
    );
    static final DynamicCommandExceptionType ERROR_EXPECTED_LIST = new DynamicCommandExceptionType(
        actualType -> Component.translatableEscape("commands.data.modify.expected_list", actualType)
    );
    static final DynamicCommandExceptionType ERROR_INVALID_INDEX = new DynamicCommandExceptionType(
        invalidIndex -> Component.translatableEscape("commands.data.modify.invalid_index", invalidIndex)
    );
    private static final char INDEX_MATCH_START = '[';
    private static final char INDEX_MATCH_END = ']';
    private static final char KEY_MATCH_START = '{';
    private static final char KEY_MATCH_END = '}';
    private static final char QUOTED_KEY_START = '"';
    private static final char SINGLE_QUOTED_KEY_START = '\'';

    public static NbtPathArgument nbtPath() {
        return new NbtPathArgument();
    }

    public static NbtPathArgument.NbtPath getPath(CommandContext<CommandSourceStack> context, String name) {
        return context.getArgument(name, NbtPathArgument.NbtPath.class);
    }

    public NbtPathArgument.NbtPath parse(StringReader reader) throws CommandSyntaxException {
        List<NbtPathArgument.Node> list = Lists.newArrayList();
        int i = reader.getCursor();
        Object2IntMap<NbtPathArgument.Node> object2IntMap = new Object2IntOpenHashMap<>();
        boolean bl = true;

        while (reader.canRead() && reader.peek() != ' ') {
            NbtPathArgument.Node node = parseNode(reader, bl);
            list.add(node);
            object2IntMap.put(node, reader.getCursor() - i);
            bl = false;
            if (reader.canRead()) {
                char c = reader.peek();
                if (c != ' ' && c != '[' && c != '{') {
                    reader.expect('.');
                }
            }
        }

        return new NbtPathArgument.NbtPath(reader.getString().substring(i, reader.getCursor()), list.toArray(new NbtPathArgument.Node[0]), object2IntMap);
    }

    private static NbtPathArgument.Node parseNode(StringReader reader, boolean first) throws CommandSyntaxException {
        return switch (reader.peek()) {
            case '"', '\'' -> readObjectNode(reader, reader.readString());
            case '[' -> {
                reader.skip();
                int i = reader.peek();
                if (i == 123) {
                    CompoundTag compoundTag2 = new TagParser(reader).readStruct();
                    reader.expect(']');
                    yield new NbtPathArgument.MatchElementNode(compoundTag2);
                } else if (i == 93) {
                    reader.skip();
                    yield NbtPathArgument.AllElementsNode.INSTANCE;
                } else {
                    int j = reader.readInt();
                    reader.expect(']');
                    yield new NbtPathArgument.IndexedElementNode(j);
                }
            }
            case '{' -> {
                if (!first) {
                    throw ERROR_INVALID_NODE.createWithContext(reader);
                }

                CompoundTag compoundTag = new TagParser(reader).readStruct();
                yield new NbtPathArgument.MatchRootObjectNode(compoundTag);
            }
            default -> readObjectNode(reader, readUnquotedName(reader));
        };
    }

    private static NbtPathArgument.Node readObjectNode(StringReader reader, String name) throws CommandSyntaxException {
        if (reader.canRead() && reader.peek() == '{') {
            CompoundTag compoundTag = new TagParser(reader).readStruct();
            return new NbtPathArgument.MatchObjectNode(name, compoundTag);
        } else {
            return new NbtPathArgument.CompoundChildNode(name);
        }
    }

    /**
     * Reads a tag name until the next special character. Throws if the result would be a 0-length string. Does not handle quoted tag names.
     */
    private static String readUnquotedName(StringReader reader) throws CommandSyntaxException {
        int i = reader.getCursor();

        while (reader.canRead() && isAllowedInUnquotedName(reader.peek())) {
            reader.skip();
        }

        if (reader.getCursor() == i) {
            throw ERROR_INVALID_NODE.createWithContext(reader);
        } else {
            return reader.getString().substring(i, reader.getCursor());
        }
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }

    /**
     * @return {@code true} if the given character is normal for a tag name; otherwise {@code false} if it has special meaning for paths.
     */
    private static boolean isAllowedInUnquotedName(char ch) {
        return ch != ' ' && ch != '"' && ch != '\'' && ch != '[' && ch != ']' && ch != '.' && ch != '{' && ch != '}';
    }

    static Predicate<Tag> createTagPredicate(CompoundTag tag) {
        return other -> NbtUtils.compareNbt(tag, other, true);
    }

    static class AllElementsNode implements NbtPathArgument.Node {
        public static final NbtPathArgument.AllElementsNode INSTANCE = new NbtPathArgument.AllElementsNode();

        private AllElementsNode() {
        }

        @Override
        public void getTag(Tag tag, List<Tag> tags) {
            if (tag instanceof CollectionTag) {
                tags.addAll((CollectionTag)tag);
            }
        }

        @Override
        public void getOrCreateTag(Tag tag, Supplier<Tag> supplier, List<Tag> tags) {
            if (tag instanceof CollectionTag<?> collectionTag) {
                if (collectionTag.isEmpty()) {
                    Tag tag2 = supplier.get();
                    if (collectionTag.addTag(0, tag2)) {
                        tags.add(tag2);
                    }
                } else {
                    tags.addAll((Collection<? extends Tag>)collectionTag);
                }
            }
        }

        @Override
        public Tag createPreferredParentTag() {
            return new ListTag();
        }

        @Override
        public int setTag(Tag tag, Supplier<Tag> supplier) {
            if (!(tag instanceof CollectionTag<?> collectionTag)) {
                return 0;
            } else {
                int i = collectionTag.size();
                if (i == 0) {
                    collectionTag.addTag(0, supplier.get());
                    return 1;
                }

                Tag tag2 = supplier.get();
                int j = i - (int)collectionTag.stream().filter(tag2::equals).count();
                if (j == 0) {
                    return 0;
                }

                collectionTag.clear();
                if (!collectionTag.addTag(0, tag2)) {
                    return 0;
                }

                for (int k = 1; k < i; k++) {
                    collectionTag.addTag(k, supplier.get());
                }

                return j;
            }
        }

        @Override
        public int removeTag(Tag tag) {
            if (tag instanceof CollectionTag<?> collectionTag) {
                int i = collectionTag.size();
                if (i > 0) {
                    collectionTag.clear();
                    return i;
                }
            }

            return 0;
        }
    }

    static class CompoundChildNode implements NbtPathArgument.Node {
        private final String name;

        public CompoundChildNode(String name) {
            this.name = name;
        }

        @Override
        public void getTag(Tag tag, List<Tag> tags) {
            if (tag instanceof CompoundTag) {
                Tag tag2 = ((CompoundTag)tag).get(this.name);
                if (tag2 != null) {
                    tags.add(tag2);
                }
            }
        }

        @Override
        public void getOrCreateTag(Tag tag, Supplier<Tag> supplier, List<Tag> tags) {
            if (tag instanceof CompoundTag compoundTag) {
                Tag tag2;
                if (compoundTag.contains(this.name)) {
                    tag2 = compoundTag.get(this.name);
                } else {
                    tag2 = supplier.get();
                    compoundTag.put(this.name, tag2);
                }

                tags.add(tag2);
            }
        }

        @Override
        public Tag createPreferredParentTag() {
            return new CompoundTag();
        }

        @Override
        public int setTag(Tag tag, Supplier<Tag> supplier) {
            if (tag instanceof CompoundTag compoundTag) {
                Tag tag2 = supplier.get();
                Tag tag3 = compoundTag.put(this.name, tag2);
                if (!tag2.equals(tag3)) {
                    return 1;
                }
            }

            return 0;
        }

        @Override
        public int removeTag(Tag tag) {
            if (tag instanceof CompoundTag compoundTag && compoundTag.contains(this.name)) {
                compoundTag.remove(this.name);
                return 1;
            } else {
                return 0;
            }
        }
    }

    static class IndexedElementNode implements NbtPathArgument.Node {
        private final int index;

        public IndexedElementNode(int index) {
            this.index = index;
        }

        @Override
        public void getTag(Tag tag, List<Tag> tags) {
            if (tag instanceof CollectionTag<?> collectionTag) {
                int i = collectionTag.size();
                int j = this.index < 0 ? i + this.index : this.index;
                if (0 <= j && j < i) {
                    tags.add(collectionTag.get(j));
                }
            }
        }

        @Override
        public void getOrCreateTag(Tag tag, Supplier<Tag> supplier, List<Tag> tags) {
            this.getTag(tag, tags);
        }

        @Override
        public Tag createPreferredParentTag() {
            return new ListTag();
        }

        @Override
        public int setTag(Tag tag, Supplier<Tag> supplier) {
            if (tag instanceof CollectionTag<?> collectionTag) {
                int i = collectionTag.size();
                int j = this.index < 0 ? i + this.index : this.index;
                if (0 <= j && j < i) {
                    Tag tag2 = collectionTag.get(j);
                    Tag tag3 = supplier.get();
                    if (!tag3.equals(tag2) && collectionTag.setTag(j, tag3)) {
                        return 1;
                    }
                }
            }

            return 0;
        }

        @Override
        public int removeTag(Tag tag) {
            if (tag instanceof CollectionTag<?> collectionTag) {
                int i = collectionTag.size();
                int j = this.index < 0 ? i + this.index : this.index;
                if (0 <= j && j < i) {
                    collectionTag.remove(j);
                    return 1;
                }
            }

            return 0;
        }
    }

    static class MatchElementNode implements NbtPathArgument.Node {
        private final CompoundTag pattern;
        private final Predicate<Tag> predicate;

        public MatchElementNode(CompoundTag pattern) {
            this.pattern = pattern;
            this.predicate = NbtPathArgument.createTagPredicate(pattern);
        }

        @Override
        public void getTag(Tag tag, List<Tag> tags) {
            if (tag instanceof ListTag listTag) {
                listTag.stream().filter(this.predicate).forEach(tags::add);
            }
        }

        @Override
        public void getOrCreateTag(Tag tag, Supplier<Tag> supplier, List<Tag> tags) {
            MutableBoolean mutableBoolean = new MutableBoolean();
            if (tag instanceof ListTag listTag) {
                listTag.stream().filter(this.predicate).forEach(currentTag -> {
                    tags.add(currentTag);
                    mutableBoolean.setTrue();
                });
                if (mutableBoolean.isFalse()) {
                    CompoundTag compoundTag = this.pattern.copy();
                    listTag.add(compoundTag);
                    tags.add(compoundTag);
                }
            }
        }

        @Override
        public Tag createPreferredParentTag() {
            return new ListTag();
        }

        @Override
        public int setTag(Tag tag, Supplier<Tag> supplier) {
            int i = 0;
            if (tag instanceof ListTag listTag) {
                int j = listTag.size();
                if (j == 0) {
                    listTag.add(supplier.get());
                    i++;
                } else {
                    for (int k = 0; k < j; k++) {
                        Tag tag2 = listTag.get(k);
                        if (this.predicate.test(tag2)) {
                            Tag tag3 = supplier.get();
                            if (!tag3.equals(tag2) && listTag.setTag(k, tag3)) {
                                i++;
                            }
                        }
                    }
                }
            }

            return i;
        }

        @Override
        public int removeTag(Tag tag) {
            int i = 0;
            if (tag instanceof ListTag listTag) {
                for (int j = listTag.size() - 1; j >= 0; j--) {
                    if (this.predicate.test(listTag.get(j))) {
                        listTag.remove(j);
                        i++;
                    }
                }
            }

            return i;
        }
    }

    static class MatchObjectNode implements NbtPathArgument.Node {
        private final String name;
        private final CompoundTag pattern;
        private final Predicate<Tag> predicate;

        public MatchObjectNode(String name, CompoundTag pattern) {
            this.name = name;
            this.pattern = pattern;
            this.predicate = NbtPathArgument.createTagPredicate(pattern);
        }

        @Override
        public void getTag(Tag tag, List<Tag> tags) {
            if (tag instanceof CompoundTag) {
                Tag tag2 = ((CompoundTag)tag).get(this.name);
                if (this.predicate.test(tag2)) {
                    tags.add(tag2);
                }
            }
        }

        @Override
        public void getOrCreateTag(Tag tag, Supplier<Tag> supplier, List<Tag> tags) {
            if (tag instanceof CompoundTag compoundTag) {
                Tag tag2 = compoundTag.get(this.name);
                if (tag2 == null) {
                    tag2 = this.pattern.copy();
                    compoundTag.put(this.name, tag2);
                    tags.add(tag2);
                } else if (this.predicate.test(tag2)) {
                    tags.add(tag2);
                }
            }
        }

        @Override
        public Tag createPreferredParentTag() {
            return new CompoundTag();
        }

        @Override
        public int setTag(Tag tag, Supplier<Tag> supplier) {
            if (tag instanceof CompoundTag compoundTag) {
                Tag tag2 = compoundTag.get(this.name);
                if (this.predicate.test(tag2)) {
                    Tag tag3 = supplier.get();
                    if (!tag3.equals(tag2)) {
                        compoundTag.put(this.name, tag3);
                        return 1;
                    }
                }
            }

            return 0;
        }

        @Override
        public int removeTag(Tag tag) {
            if (tag instanceof CompoundTag compoundTag) {
                Tag tag2 = compoundTag.get(this.name);
                if (this.predicate.test(tag2)) {
                    compoundTag.remove(this.name);
                    return 1;
                }
            }

            return 0;
        }
    }

    static class MatchRootObjectNode implements NbtPathArgument.Node {
        private final Predicate<Tag> predicate;

        public MatchRootObjectNode(CompoundTag tag) {
            this.predicate = NbtPathArgument.createTagPredicate(tag);
        }

        @Override
        public void getTag(Tag tag, List<Tag> tags) {
            if (tag instanceof CompoundTag && this.predicate.test(tag)) {
                tags.add(tag);
            }
        }

        @Override
        public void getOrCreateTag(Tag tag, Supplier<Tag> supplier, List<Tag> tags) {
            this.getTag(tag, tags);
        }

        @Override
        public Tag createPreferredParentTag() {
            return new CompoundTag();
        }

        @Override
        public int setTag(Tag tag, Supplier<Tag> supplier) {
            return 0;
        }

        @Override
        public int removeTag(Tag tag) {
            return 0;
        }
    }

    public static class NbtPath {
        private final String original;
        private final Object2IntMap<NbtPathArgument.Node> nodeToOriginalPosition;
        private final NbtPathArgument.Node[] nodes;
        public static final Codec<NbtPathArgument.NbtPath> CODEC = Codec.STRING.comapFlatMap(string -> {
            try {
                NbtPathArgument.NbtPath nbtPath = new NbtPathArgument().parse(new StringReader(string));
                return DataResult.success(nbtPath);
            } catch (CommandSyntaxException commandSyntaxException) {
                return DataResult.error(() -> "Failed to parse path " + string + ": " + commandSyntaxException.getMessage());
            }
        }, NbtPathArgument.NbtPath::asString);

        public static NbtPathArgument.NbtPath of(String path) throws CommandSyntaxException {
            return new NbtPathArgument().parse(new StringReader(path));
        }

        public NbtPath(String original, NbtPathArgument.Node[] nodes, Object2IntMap<NbtPathArgument.Node> nodeToOriginPosition) {
            this.original = original;
            this.nodes = nodes;
            this.nodeToOriginalPosition = nodeToOriginPosition;
        }

        public List<Tag> get(Tag tag) throws CommandSyntaxException {
            List<Tag> list = Collections.singletonList(tag);

            for (NbtPathArgument.Node node : this.nodes) {
                list = node.get(list);
                if (list.isEmpty()) {
                    throw this.createNotFoundException(node);
                }
            }

            return list;
        }

        public int countMatching(Tag tag) {
            List<Tag> list = Collections.singletonList(tag);

            for (NbtPathArgument.Node node : this.nodes) {
                list = node.get(list);
                if (list.isEmpty()) {
                    return 0;
                }
            }

            return list.size();
        }

        private List<Tag> getOrCreateParents(Tag tag) throws CommandSyntaxException {
            List<Tag> list = Collections.singletonList(tag);

            for (int i = 0; i < this.nodes.length - 1; i++) {
                NbtPathArgument.Node node = this.nodes[i];
                int j = i + 1;
                list = node.getOrCreate(list, this.nodes[j]::createPreferredParentTag);
                if (list.isEmpty()) {
                    throw this.createNotFoundException(node);
                }
            }

            return list;
        }

        public List<Tag> getOrCreate(Tag tag, Supplier<Tag> supplier) throws CommandSyntaxException {
            List<Tag> list = this.getOrCreateParents(tag);
            NbtPathArgument.Node node = this.nodes[this.nodes.length - 1];
            return node.getOrCreate(list, supplier);
        }

        private static int apply(List<Tag> tags, Function<Tag, Integer> function) {
            return tags.stream().map(function).reduce(0, (integer, integer2) -> integer + integer2);
        }

        public static boolean isTooDeep(Tag tag, int currentDepth) {
            if (currentDepth >= 512) {
                return true;
            }

            if (tag instanceof CompoundTag compoundTag) {
                for (String string : compoundTag.getAllKeys()) {
                    Tag tag2 = compoundTag.get(string);
                    if (tag2 != null && isTooDeep(tag2, currentDepth + 1)) {
                        return true;
                    }
                }
            } else if (tag instanceof ListTag) {
                for (Tag tag3 : (ListTag)tag) {
                    if (isTooDeep(tag3, currentDepth + 1)) {
                        return true;
                    }
                }
            }

            return false;
        }

        public int set(Tag tag, Tag other) throws CommandSyntaxException {
            if (isTooDeep(other, this.estimatePathDepth())) {
                throw NbtPathArgument.ERROR_DATA_TOO_DEEP.create();
            }

            Tag tag2 = other.copy();
            List<Tag> list = this.getOrCreateParents(tag);
            if (list.isEmpty()) {
                return 0;
            }

            NbtPathArgument.Node node = this.nodes[this.nodes.length - 1];
            MutableBoolean mutableBoolean = new MutableBoolean(false);
            return apply(list, tag2x -> node.setTag(tag2x, () -> {
                if (mutableBoolean.isFalse()) {
                    mutableBoolean.setTrue();
                    return tag2;
                } else {
                    return tag2.copy();
                }
            }));
        }

        private int estimatePathDepth() {
            return this.nodes.length;
        }

        public int insert(int index, CompoundTag rootTag, List<Tag> tagsToInsert) throws CommandSyntaxException {
            List<Tag> list = new ArrayList<>(tagsToInsert.size());

            for (Tag tag : tagsToInsert) {
                Tag tag2 = tag.copy();
                list.add(tag2);
                if (isTooDeep(tag2, this.estimatePathDepth())) {
                    throw NbtPathArgument.ERROR_DATA_TOO_DEEP.create();
                }
            }

            Collection<Tag> collection = this.getOrCreate(rootTag, ListTag::new);
            int i = 0;
            boolean bl = false;

            for (Tag tag3 : collection) {
                if (!(tag3 instanceof CollectionTag<?> collectionTag)) {
                    throw NbtPathArgument.ERROR_EXPECTED_LIST.create(tag3);
                }

                boolean bl2 = false;
                int j = index < 0 ? collectionTag.size() + index + 1 : index;

                for (Tag tag4 : list) {
                    try {
                        if (collectionTag.addTag(j, bl ? tag4.copy() : tag4)) {
                            j++;
                            bl2 = true;
                        }
                    } catch (IndexOutOfBoundsException indexOutOfBoundsException) {
                        throw NbtPathArgument.ERROR_INVALID_INDEX.create(j);
                    }
                }

                bl = true;
                i += bl2 ? 1 : 0;
            }

            return i;
        }

        public int remove(Tag tag) {
            List<Tag> list = Collections.singletonList(tag);

            for (int i = 0; i < this.nodes.length - 1; i++) {
                list = this.nodes[i].get(list);
            }

            NbtPathArgument.Node node = this.nodes[this.nodes.length - 1];
            return apply(list, node::removeTag);
        }

        private CommandSyntaxException createNotFoundException(NbtPathArgument.Node node) {
            int i = this.nodeToOriginalPosition.getInt(node);
            return NbtPathArgument.ERROR_NOTHING_FOUND.create(this.original.substring(0, i));
        }

        @Override
        public String toString() {
            return this.original;
        }

        public String asString() {
            return this.original;
        }
    }

    interface Node {
        void getTag(Tag var1, List<Tag> var2);

        void getOrCreateTag(Tag var1, Supplier<Tag> var2, List<Tag> var3);

        Tag createPreferredParentTag();

        int setTag(Tag var1, Supplier<Tag> var2);

        int removeTag(Tag var1);

        default List<Tag> get(List<Tag> tags) {
            return this.collect(tags, this::getTag);
        }

        default List<Tag> getOrCreate(List<Tag> tags, Supplier<Tag> supplier) {
            return this.collect(tags, (tag, tagList) -> this.getOrCreateTag(tag, supplier, tagList));
        }

        default List<Tag> collect(List<Tag> tags, BiConsumer<Tag, List<Tag>> consumer) {
            List<Tag> list = Lists.newArrayList();

            for (Tag tag : tags) {
                consumer.accept(tag, list);
            }

            return list;
        }
    }
}
