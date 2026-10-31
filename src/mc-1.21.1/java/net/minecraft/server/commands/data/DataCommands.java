package net.minecraft.server.commands.data;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.CompoundTagArgument;
import net.minecraft.commands.arguments.NbtPathArgument;
import net.minecraft.commands.arguments.NbtTagArgument;
import net.minecraft.nbt.CollectionTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public class DataCommands {
    private static final SimpleCommandExceptionType ERROR_MERGE_UNCHANGED = new SimpleCommandExceptionType(Component.translatable("commands.data.merge.failed"));
    private static final DynamicCommandExceptionType ERROR_GET_NOT_NUMBER = new DynamicCommandExceptionType(
        nbtPath -> Component.translatableEscape("commands.data.get.invalid", nbtPath)
    );
    private static final DynamicCommandExceptionType ERROR_GET_NON_EXISTENT = new DynamicCommandExceptionType(
        nbtPath -> Component.translatableEscape("commands.data.get.unknown", nbtPath)
    );
    private static final SimpleCommandExceptionType ERROR_MULTIPLE_TAGS = new SimpleCommandExceptionType(Component.translatable("commands.data.get.multiple"));
    private static final DynamicCommandExceptionType ERROR_EXPECTED_OBJECT = new DynamicCommandExceptionType(
        unexpectedNbt -> Component.translatableEscape("commands.data.modify.expected_object", unexpectedNbt)
    );
    private static final DynamicCommandExceptionType ERROR_EXPECTED_VALUE = new DynamicCommandExceptionType(
        unexpectedNbt -> Component.translatableEscape("commands.data.modify.expected_value", unexpectedNbt)
    );
    private static final Dynamic2CommandExceptionType ERROR_INVALID_SUBSTRING = new Dynamic2CommandExceptionType(
        (startIndex, endIndex) -> Component.translatableEscape("commands.data.modify.invalid_substring", startIndex, endIndex)
    );
    public static final List<Function<String, DataCommands.DataProvider>> ALL_PROVIDERS = ImmutableList.of(
        EntityDataAccessor.PROVIDER, BlockDataAccessor.PROVIDER, StorageDataAccessor.PROVIDER
    );
    public static final List<DataCommands.DataProvider> TARGET_PROVIDERS = ALL_PROVIDERS.stream()
        .map(dataProviderFactory -> dataProviderFactory.apply("target"))
        .collect(ImmutableList.toImmutableList());
    public static final List<DataCommands.DataProvider> SOURCE_PROVIDERS = ALL_PROVIDERS.stream()
        .map(dataProviderFactory -> dataProviderFactory.apply("source"))
        .collect(ImmutableList.toImmutableList());

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> literalArgumentBuilder = Commands.literal("data").requires(source -> source.hasPermission(2));

        for (DataCommands.DataProvider dataProvider : TARGET_PROVIDERS) {
            literalArgumentBuilder.then(
                    dataProvider.wrap(
                        Commands.literal("merge"),
                        argumentBuilder -> argumentBuilder.then(
                            Commands.argument("nbt", CompoundTagArgument.compoundTag())
                                .executes(
                                    context -> mergeData(context.getSource(), dataProvider.access(context), CompoundTagArgument.getCompoundTag(context, "nbt"))
                                )
                        )
                    )
                )
                .then(
                    dataProvider.wrap(
                        Commands.literal("get"),
                        argumentBuilder -> argumentBuilder.executes(context -> getData(context.getSource(), dataProvider.access(context)))
                            .then(
                                Commands.argument("path", NbtPathArgument.nbtPath())
                                    .executes(context -> getData(context.getSource(), dataProvider.access(context), NbtPathArgument.getPath(context, "path")))
                                    .then(
                                        Commands.argument("scale", DoubleArgumentType.doubleArg())
                                            .executes(
                                                context -> getNumeric(
                                                    context.getSource(),
                                                    dataProvider.access(context),
                                                    NbtPathArgument.getPath(context, "path"),
                                                    DoubleArgumentType.getDouble(context, "scale")
                                                )
                                            )
                                    )
                            )
                    )
                )
                .then(
                    dataProvider.wrap(
                        Commands.literal("remove"),
                        argumentBuilder -> argumentBuilder.then(
                            Commands.argument("path", NbtPathArgument.nbtPath())
                                .executes(context -> removeData(context.getSource(), dataProvider.access(context), NbtPathArgument.getPath(context, "path")))
                        )
                    )
                )
                .then(
                    decorateModification(
                        (argumentBuilder, dataManipulatorDecorator) -> argumentBuilder.then(
                                Commands.literal("insert")
                                    .then(
                                        Commands.argument("index", IntegerArgumentType.integer())
                                            .then(
                                                dataManipulatorDecorator.create(
                                                    (context, sourceCompoundTag, nbtPath, tags) -> nbtPath.insert(
                                                        IntegerArgumentType.getInteger(context, "index"), sourceCompoundTag, tags
                                                    )
                                                )
                                            )
                                    )
                            )
                            .then(
                                Commands.literal("prepend")
                                    .then(
                                        dataManipulatorDecorator.create(
                                            (context, sourceCompoundTag, nbtPath, tags) -> nbtPath.insert(0, sourceCompoundTag, tags)
                                        )
                                    )
                            )
                            .then(
                                Commands.literal("append")
                                    .then(
                                        dataManipulatorDecorator.create(
                                            (context, sourceCompoundTag, nbtPath, tags) -> nbtPath.insert(-1, sourceCompoundTag, tags)
                                        )
                                    )
                            )
                            .then(
                                Commands.literal("set")
                                    .then(
                                        dataManipulatorDecorator.create(
                                            (context, sourceCompoundTag, nbtPath, tags) -> nbtPath.set(sourceCompoundTag, Iterables.getLast(tags))
                                        )
                                    )
                            )
                            .then(Commands.literal("merge").then(dataManipulatorDecorator.create((context, sourceCompoundTag, nbtPath, tags) -> {
                                CompoundTag compoundTag = new CompoundTag();

                                for (Tag tag : tags) {
                                    if (NbtPathArgument.NbtPath.isTooDeep(tag, 0)) {
                                        throw NbtPathArgument.ERROR_DATA_TOO_DEEP.create();
                                    }

                                    if (!(tag instanceof CompoundTag compoundTag2)) {
                                        throw ERROR_EXPECTED_OBJECT.create(tag);
                                    }

                                    compoundTag.merge(compoundTag2);
                                }

                                Collection<Tag> collection = nbtPath.getOrCreate(sourceCompoundTag, CompoundTag::new);
                                int i = 0;

                                for (Tag tag2 : collection) {
                                    if (!(tag2 instanceof CompoundTag compoundTag3)) {
                                        throw ERROR_EXPECTED_OBJECT.create(tag2);
                                    }

                                    CompoundTag compoundTag4 = compoundTag3.copy();
                                    compoundTag3.merge(compoundTag);
                                    i += compoundTag4.equals(compoundTag3) ? 0 : 1;
                                }

                                return i;
                            })))
                    )
                );
        }

        dispatcher.register(literalArgumentBuilder);
    }

    private static String getAsText(Tag tag) throws CommandSyntaxException {
        if (tag.getType().isValue()) {
            return tag.getAsString();
        } else {
            throw ERROR_EXPECTED_VALUE.create(tag);
        }
    }

    private static List<Tag> stringifyTagList(List<Tag> tagList, DataCommands.StringProcessor processor) throws CommandSyntaxException {
        List<Tag> list = new ArrayList<>(tagList.size());

        for (Tag tag : tagList) {
            String string = getAsText(tag);
            list.add(StringTag.valueOf(processor.process(string)));
        }

        return list;
    }

    private static ArgumentBuilder<CommandSourceStack, ?> decorateModification(
        BiConsumer<ArgumentBuilder<CommandSourceStack, ?>, DataCommands.DataManipulatorDecorator> decorator
    ) {
        LiteralArgumentBuilder<CommandSourceStack> literalArgumentBuilder = Commands.literal("modify");

        for (DataCommands.DataProvider dataProvider : TARGET_PROVIDERS) {
            dataProvider.wrap(
                literalArgumentBuilder,
                argumentBuilder -> {
                    ArgumentBuilder<CommandSourceStack, ?> argumentBuilder2 = Commands.argument("targetPath", NbtPathArgument.nbtPath());

                    for (DataCommands.DataProvider dataProvider2 : SOURCE_PROVIDERS) {
                        decorator.accept(
                            argumentBuilder2,
                            dataManipulator -> dataProvider2.wrap(
                                Commands.literal("from"),
                                subArgBuilder -> subArgBuilder.executes(
                                        context -> manipulateData(context, dataProvider, dataManipulator, getSingletonSource(context, dataProvider2))
                                    )
                                    .then(
                                        Commands.argument("sourcePath", NbtPathArgument.nbtPath())
                                            .executes(
                                                context -> manipulateData(context, dataProvider, dataManipulator, resolveSourcePath(context, dataProvider2))
                                            )
                                    )
                            )
                        );
                        decorator.accept(
                            argumentBuilder2,
                            dataManipulator -> dataProvider2.wrap(
                                Commands.literal("string"),
                                subArgBuilder -> subArgBuilder.executes(
                                        context -> manipulateData(
                                            context,
                                            dataProvider,
                                            dataManipulator,
                                            stringifyTagList(getSingletonSource(context, dataProvider2), tagString -> tagString)
                                        )
                                    )
                                    .then(
                                        Commands.argument("sourcePath", NbtPathArgument.nbtPath())
                                            .executes(
                                                context -> manipulateData(
                                                    context,
                                                    dataProvider,
                                                    dataManipulator,
                                                    stringifyTagList(resolveSourcePath(context, dataProvider2), tagString -> tagString)
                                                )
                                            )
                                            .then(
                                                Commands.argument("start", IntegerArgumentType.integer())
                                                    .executes(
                                                        context -> manipulateData(
                                                            context,
                                                            dataProvider,
                                                            dataManipulator,
                                                            stringifyTagList(
                                                                resolveSourcePath(context, dataProvider2),
                                                                tagString -> substring(tagString, IntegerArgumentType.getInteger(context, "start"))
                                                            )
                                                        )
                                                    )
                                                    .then(
                                                        Commands.argument("end", IntegerArgumentType.integer())
                                                            .executes(
                                                                context -> manipulateData(
                                                                    context,
                                                                    dataProvider,
                                                                    dataManipulator,
                                                                    stringifyTagList(
                                                                        resolveSourcePath(context, dataProvider2),
                                                                        tagString -> substring(
                                                                            tagString,
                                                                            IntegerArgumentType.getInteger(context, "start"),
                                                                            IntegerArgumentType.getInteger(context, "end")
                                                                        )
                                                                    )
                                                                )
                                                            )
                                                    )
                                            )
                                    )
                            )
                        );
                    }

                    decorator.accept(
                        argumentBuilder2,
                        dataManipulator -> Commands.literal("value").then(Commands.argument("value", NbtTagArgument.nbtTag()).executes(context -> {
                            List<Tag> list = Collections.singletonList(NbtTagArgument.getNbtTag(context, "value"));
                            return manipulateData(context, dataProvider, dataManipulator, list);
                        }))
                    );
                    return argumentBuilder.then(argumentBuilder2);
                }
            );
        }

        return literalArgumentBuilder;
    }

    private static String validatedSubstring(String source, int start, int end) throws CommandSyntaxException {
        if (start >= 0 && end <= source.length() && start <= end) {
            return source.substring(start, end);
        } else {
            throw ERROR_INVALID_SUBSTRING.create(start, end);
        }
    }

    private static String substring(String source, int start, int end) throws CommandSyntaxException {
        int i = source.length();
        int j = getOffset(start, i);
        int k = getOffset(end, i);
        return validatedSubstring(source, j, k);
    }

    private static String substring(String source, int start) throws CommandSyntaxException {
        int i = source.length();
        return validatedSubstring(source, getOffset(start, i), i);
    }

    private static int getOffset(int index, int length) {
        return index >= 0 ? index : length + index;
    }

    private static List<Tag> getSingletonSource(CommandContext<CommandSourceStack> context, DataCommands.DataProvider dataProvider) throws CommandSyntaxException {
        DataAccessor dataAccessor = dataProvider.access(context);
        return Collections.singletonList(dataAccessor.getData());
    }

    private static List<Tag> resolveSourcePath(CommandContext<CommandSourceStack> context, DataCommands.DataProvider dataProvider) throws CommandSyntaxException {
        DataAccessor dataAccessor = dataProvider.access(context);
        NbtPathArgument.NbtPath nbtPath = NbtPathArgument.getPath(context, "sourcePath");
        return nbtPath.get(dataAccessor.getData());
    }

    private static int manipulateData(
        CommandContext<CommandSourceStack> source, DataCommands.DataProvider dataProvider, DataCommands.DataManipulator dataManipulator, List<Tag> tags
    ) throws CommandSyntaxException {
        DataAccessor dataAccessor = dataProvider.access(source);
        NbtPathArgument.NbtPath nbtPath = NbtPathArgument.getPath(source, "targetPath");
        CompoundTag compoundTag = dataAccessor.getData();
        int i = dataManipulator.modify(source, compoundTag, nbtPath, tags);
        if (i == 0) {
            throw ERROR_MERGE_UNCHANGED.create();
        }

        dataAccessor.setData(compoundTag);
        source.getSource().sendSuccess(() -> dataAccessor.getModifiedSuccess(), true);
        return i;
    }

    /**
     * Removes the tag at the end of the path.
     *
     * @return 1
     */
    private static int removeData(CommandSourceStack source, DataAccessor accessor, NbtPathArgument.NbtPath path) throws CommandSyntaxException {
        CompoundTag compoundTag = accessor.getData();
        int i = path.remove(compoundTag);
        if (i == 0) {
            throw ERROR_MERGE_UNCHANGED.create();
        }

        accessor.setData(compoundTag);
        source.sendSuccess(() -> accessor.getModifiedSuccess(), true);
        return i;
    }

    public static Tag getSingleTag(NbtPathArgument.NbtPath path, DataAccessor accessor) throws CommandSyntaxException {
        Collection<Tag> collection = path.get(accessor.getData());
        Iterator<Tag> iterator = collection.iterator();
        Tag tag = iterator.next();
        if (iterator.hasNext()) {
            throw ERROR_MULTIPLE_TAGS.create();
        } else {
            return tag;
        }
    }

    /**
     * Gets a value, which can be of any known NBT type.
     *
     * @return The value associated with the element: length for strings, size for lists and compounds, and numeric value for primitives.
     */
    private static int getData(CommandSourceStack source, DataAccessor accessor, NbtPathArgument.NbtPath path) throws CommandSyntaxException {
        Tag tag = getSingleTag(path, accessor);
        int i;
        if (tag instanceof NumericTag) {
            i = Mth.floor(((NumericTag)tag).getAsDouble());
        } else if (tag instanceof CollectionTag) {
            i = ((CollectionTag)tag).size();
        } else if (tag instanceof CompoundTag) {
            i = ((CompoundTag)tag).size();
        } else {
            if (!(tag instanceof StringTag)) {
                throw ERROR_GET_NON_EXISTENT.create(path.toString());
            }

            i = tag.getAsString().length();
        }

        source.sendSuccess(() -> accessor.getPrintSuccess(tag), false);
        return i;
    }

    /**
     * Gets a single numeric element, scaled by the given amount.
     *
     * @return The element's value, scaled by scale.
     */
    private static int getNumeric(CommandSourceStack source, DataAccessor accessor, NbtPathArgument.NbtPath path, double scale) throws CommandSyntaxException {
        Tag tag = getSingleTag(path, accessor);
        if (!(tag instanceof NumericTag)) {
            throw ERROR_GET_NOT_NUMBER.create(path.toString());
        }

        int i = Mth.floor(((NumericTag)tag).getAsDouble() * scale);
        source.sendSuccess(() -> accessor.getPrintSuccess(path, scale, i), false);
        return i;
    }

    /**
     * Gets all NBT on the object, and applies syntax highlighting.
     *
     * @return 1
     */
    private static int getData(CommandSourceStack source, DataAccessor accessor) throws CommandSyntaxException {
        CompoundTag compoundTag = accessor.getData();
        source.sendSuccess(() -> accessor.getPrintSuccess(compoundTag), false);
        return 1;
    }

    /**
     * Merges the given NBT into the targeted object's NBT.
     *
     * @return 1
     */
    private static int mergeData(CommandSourceStack source, DataAccessor accessor, CompoundTag nbt) throws CommandSyntaxException {
        CompoundTag compoundTag = accessor.getData();
        if (NbtPathArgument.NbtPath.isTooDeep(nbt, 0)) {
            throw NbtPathArgument.ERROR_DATA_TOO_DEEP.create();
        }

        CompoundTag compoundTag2 = compoundTag.copy().merge(nbt);
        if (compoundTag.equals(compoundTag2)) {
            throw ERROR_MERGE_UNCHANGED.create();
        }

        accessor.setData(compoundTag2);
        source.sendSuccess(() -> accessor.getModifiedSuccess(), true);
        return 1;
    }

    @FunctionalInterface
    interface DataManipulator {
        int modify(CommandContext<CommandSourceStack> var1, CompoundTag var2, NbtPathArgument.NbtPath var3, List<Tag> var4) throws CommandSyntaxException;
    }

    @FunctionalInterface
    interface DataManipulatorDecorator {
        ArgumentBuilder<CommandSourceStack, ?> create(DataCommands.DataManipulator var1);
    }

    public interface DataProvider {
        /**
         * Creates an accessor based on the command context. This should only refer to arguments registered in {@link createArgument}.
         */
        DataAccessor access(CommandContext<CommandSourceStack> var1) throws CommandSyntaxException;

        /**
         * Creates an argument used for accessing data related to this type of thing, including a literal to distinguish from other types.
         */
        ArgumentBuilder<CommandSourceStack, ?> wrap(
            ArgumentBuilder<CommandSourceStack, ?> var1, Function<ArgumentBuilder<CommandSourceStack, ?>, ArgumentBuilder<CommandSourceStack, ?>> var2
        );
    }

    @FunctionalInterface
    interface StringProcessor {
        String process(String var1) throws CommandSyntaxException;
    }
}
