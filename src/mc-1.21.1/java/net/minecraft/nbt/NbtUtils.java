package net.minecraft.nbt;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.StateHolder;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.FluidState;
import org.slf4j.Logger;

public final class NbtUtils {
    private static final Comparator<ListTag> YXZ_LISTTAG_INT_COMPARATOR = Comparator.<ListTag>comparingInt(listTag -> listTag.getInt(1))
        .thenComparingInt(listTag -> listTag.getInt(0))
        .thenComparingInt(listTag -> listTag.getInt(2));
    private static final Comparator<ListTag> YXZ_LISTTAG_DOUBLE_COMPARATOR = Comparator.<ListTag>comparingDouble(listTag -> listTag.getDouble(1))
        .thenComparingDouble(listTag -> listTag.getDouble(0))
        .thenComparingDouble(listTag -> listTag.getDouble(2));
    public static final String SNBT_DATA_TAG = "data";
    private static final char PROPERTIES_START = '{';
    private static final char PROPERTIES_END = '}';
    private static final String ELEMENT_SEPARATOR = ",";
    private static final char KEY_VALUE_SEPARATOR = ':';
    private static final Splitter COMMA_SPLITTER = Splitter.on(",");
    private static final Splitter COLON_SPLITTER = Splitter.on(':').limit(2);
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int INDENT = 2;
    private static final int NOT_FOUND = -1;

    private NbtUtils() {
    }

    @VisibleForTesting
    public static boolean compareNbt(@Nullable Tag tag, @Nullable Tag other, boolean compareListTag) {
        if (tag == other) {
            return true;
        }

        if (tag == null) {
            return true;
        }

        if (other == null) {
            return false;
        }

        if (!tag.getClass().equals(other.getClass())) {
            return false;
        }

        if (tag instanceof CompoundTag compoundTag) {
            CompoundTag compoundTag2 = (CompoundTag)other;
            if (compoundTag2.size() < compoundTag.size()) {
                return false;
            }

            for (String string : compoundTag.getAllKeys()) {
                Tag tag2 = compoundTag.get(string);
                if (!compareNbt(tag2, compoundTag2.get(string), compareListTag)) {
                    return false;
                }
            }

            return true;
        } else if (tag instanceof ListTag listTag && compareListTag) {
            ListTag listTag2 = (ListTag)other;
            if (listTag.isEmpty()) {
                return listTag2.isEmpty();
            }

            if (listTag2.size() < listTag.size()) {
                return false;
            }

            for (Tag tag3 : listTag) {
                boolean bl = false;

                for (Tag tag4 : listTag2) {
                    if (compareNbt(tag3, tag4, compareListTag)) {
                        bl = true;
                        break;
                    }
                }

                if (!bl) {
                    return false;
                }
            }

            return true;
        } else {
            return tag.equals(other);
        }
    }

    public static IntArrayTag createUUID(UUID uuid) {
        return new IntArrayTag(UUIDUtil.uuidToIntArray(uuid));
    }

    public static UUID loadUUID(Tag tag) {
        if (tag.getType() != IntArrayTag.TYPE) {
            throw new IllegalArgumentException("Expected UUID-Tag to be of type " + IntArrayTag.TYPE.getName() + ", but found " + tag.getType().getName() + ".");
        } else {
            int[] is = ((IntArrayTag)tag).getAsIntArray();
            if (is.length != 4) {
                throw new IllegalArgumentException("Expected UUID-Array to be of length 4, but found " + is.length + ".");
            } else {
                return UUIDUtil.uuidFromIntArray(is);
            }
        }
    }

    public static Optional<BlockPos> readBlockPos(CompoundTag tag, String key) {
        int[] is = tag.getIntArray(key);
        return is.length == 3 ? Optional.of(new BlockPos(is[0], is[1], is[2])) : Optional.empty();
    }

    public static Tag writeBlockPos(BlockPos pos) {
        return new IntArrayTag(new int[]{pos.getX(), pos.getY(), pos.getZ()});
    }

    public static BlockState readBlockState(HolderGetter<Block> blockGetter, CompoundTag tag) {
        if (!tag.contains("Name", 8)) {
            return Blocks.AIR.defaultBlockState();
        }

        ResourceLocation resourceLocation = ResourceLocation.parse(tag.getString("Name"));
        Optional<? extends Holder<Block>> optional = blockGetter.get(ResourceKey.create(Registries.BLOCK, resourceLocation));
        if (optional.isEmpty()) {
            return Blocks.AIR.defaultBlockState();
        }

        Block block = optional.get().value();
        BlockState blockState = block.defaultBlockState();
        if (tag.contains("Properties", 10)) {
            CompoundTag compoundTag = tag.getCompound("Properties");
            StateDefinition<Block, BlockState> stateDefinition = block.getStateDefinition();

            for (String string : compoundTag.getAllKeys()) {
                Property<?> property = stateDefinition.getProperty(string);
                if (property != null) {
                    blockState = setValueHelper(blockState, property, string, compoundTag, tag);
                }
            }
        }

        return blockState;
    }

    private static <S extends StateHolder<?, S>, T extends Comparable<T>> S setValueHelper(
        S stateHolder, Property<T> property, String propertyName, CompoundTag propertiesTag, CompoundTag blockStateTag
    ) {
        Optional<T> optional = property.getValue(propertiesTag.getString(propertyName));
        if (optional.isPresent()) {
            return stateHolder.setValue(property, optional.get());
        }

        LOGGER.warn("Unable to read property: {} with value: {} for blockstate: {}", propertyName, propertiesTag.getString(propertyName), blockStateTag);
        return stateHolder;
    }

    public static CompoundTag writeBlockState(BlockState state) {
        CompoundTag compoundTag = new CompoundTag();
        compoundTag.putString("Name", BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
        Map<Property<?>, Comparable<?>> map = state.getValues();
        if (!map.isEmpty()) {
            CompoundTag compoundTag2 = new CompoundTag();

            for (Entry<Property<?>, Comparable<?>> entry : map.entrySet()) {
                Property<?> property = entry.getKey();
                compoundTag2.putString(property.getName(), getName(property, entry.getValue()));
            }

            compoundTag.put("Properties", compoundTag2);
        }

        return compoundTag;
    }

    public static CompoundTag writeFluidState(FluidState state) {
        CompoundTag compoundTag = new CompoundTag();
        compoundTag.putString("Name", BuiltInRegistries.FLUID.getKey(state.getType()).toString());
        Map<Property<?>, Comparable<?>> map = state.getValues();
        if (!map.isEmpty()) {
            CompoundTag compoundTag2 = new CompoundTag();

            for (Entry<Property<?>, Comparable<?>> entry : map.entrySet()) {
                Property<?> property = entry.getKey();
                compoundTag2.putString(property.getName(), getName(property, entry.getValue()));
            }

            compoundTag.put("Properties", compoundTag2);
        }

        return compoundTag;
    }

    private static <T extends Comparable<T>> String getName(Property<T> property, Comparable<?> value) {
        return property.getName((T)value);
    }

    public static String prettyPrint(Tag tag) {
        return prettyPrint(tag, false);
    }

    public static String prettyPrint(Tag tag, boolean prettyPrintArray) {
        return prettyPrint(new StringBuilder(), tag, 0, prettyPrintArray).toString();
    }

    public static StringBuilder prettyPrint(StringBuilder stringBuilder, Tag tag, int indentLevel, boolean prettyPrintArray) {
        switch (tag.getId()) {
            case 0:
                break;
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
            case 6:
            case 8:
                stringBuilder.append(tag);
                break;
            case 7:
                ByteArrayTag byteArrayTag = (ByteArrayTag)tag;
                byte[] bs = byteArrayTag.getAsByteArray();
                int i = bs.length;
                indent(indentLevel, stringBuilder).append("byte[").append(i).append("] {\n");
                if (prettyPrintArray) {
                    indent(indentLevel + 1, stringBuilder);

                    for (int j = 0; j < bs.length; j++) {
                        if (j != 0) {
                            stringBuilder.append(',');
                        }

                        if (j % 16 == 0 && j / 16 > 0) {
                            stringBuilder.append('\n');
                            if (j < bs.length) {
                                indent(indentLevel + 1, stringBuilder);
                            }
                        } else if (j != 0) {
                            stringBuilder.append(' ');
                        }

                        stringBuilder.append(String.format(Locale.ROOT, "0x%02X", bs[j] & 255));
                    }
                } else {
                    indent(indentLevel + 1, stringBuilder).append(" // Skipped, supply withBinaryBlobs true");
                }

                stringBuilder.append('\n');
                indent(indentLevel, stringBuilder).append('}');
                break;
            case 9:
                ListTag listTag = (ListTag)tag;
                int k = listTag.size();
                int i = listTag.getElementType();
                String string = i == 0 ? "undefined" : TagTypes.getType(i).getPrettyName();
                indent(indentLevel, stringBuilder).append("list<").append(string).append(">[").append(k).append("] [");
                if (k != 0) {
                    stringBuilder.append('\n');
                }

                for (int l = 0; l < k; l++) {
                    if (l != 0) {
                        stringBuilder.append(",\n");
                    }

                    indent(indentLevel + 1, stringBuilder);
                    prettyPrint(stringBuilder, listTag.get(l), indentLevel + 1, prettyPrintArray);
                }

                if (k != 0) {
                    stringBuilder.append('\n');
                }

                indent(indentLevel, stringBuilder).append(']');
                break;
            case 10:
                CompoundTag compoundTag = (CompoundTag)tag;
                List<String> list = Lists.newArrayList(compoundTag.getAllKeys());
                Collections.sort(list);
                indent(indentLevel, stringBuilder).append('{');
                if (stringBuilder.length() - stringBuilder.lastIndexOf("\n") > 2 * (indentLevel + 1)) {
                    stringBuilder.append('\n');
                    indent(indentLevel + 1, stringBuilder);
                }

                int i = list.stream().mapToInt(String::length).max().orElse(0);
                String string = Strings.repeat(" ", i);

                for (int l = 0; l < list.size(); l++) {
                    if (l != 0) {
                        stringBuilder.append(",\n");
                    }

                    String string2 = list.get(l);
                    indent(indentLevel + 1, stringBuilder)
                        .append('"')
                        .append(string2)
                        .append('"')
                        .append(string, 0, string.length() - string2.length())
                        .append(": ");
                    prettyPrint(stringBuilder, compoundTag.get(string2), indentLevel + 1, prettyPrintArray);
                }

                if (!list.isEmpty()) {
                    stringBuilder.append('\n');
                }

                indent(indentLevel, stringBuilder).append('}');
                break;
            case 11:
                IntArrayTag intArrayTag = (IntArrayTag)tag;
                int[] is = intArrayTag.getAsIntArray();
                int i = 0;

                for (int m : is) {
                    i = Math.max(i, String.format(Locale.ROOT, "%X", m).length());
                }

                int j = is.length;
                indent(indentLevel, stringBuilder).append("int[").append(j).append("] {\n");
                if (prettyPrintArray) {
                    indent(indentLevel + 1, stringBuilder);

                    for (int l = 0; l < is.length; l++) {
                        if (l != 0) {
                            stringBuilder.append(',');
                        }

                        if (l % 16 == 0 && l / 16 > 0) {
                            stringBuilder.append('\n');
                            if (l < is.length) {
                                indent(indentLevel + 1, stringBuilder);
                            }
                        } else if (l != 0) {
                            stringBuilder.append(' ');
                        }

                        stringBuilder.append(String.format(Locale.ROOT, "0x%0" + i + "X", is[l]));
                    }
                } else {
                    indent(indentLevel + 1, stringBuilder).append(" // Skipped, supply withBinaryBlobs true");
                }

                stringBuilder.append('\n');
                indent(indentLevel, stringBuilder).append('}');
                break;
            case 12:
                LongArrayTag longArrayTag = (LongArrayTag)tag;
                long[] ls = longArrayTag.getAsLongArray();
                long n = 0L;

                for (long o : ls) {
                    n = Math.max(n, String.format(Locale.ROOT, "%X", o).length());
                }

                long p = ls.length;
                indent(indentLevel, stringBuilder).append("long[").append(p).append("] {\n");
                if (prettyPrintArray) {
                    indent(indentLevel + 1, stringBuilder);

                    for (int m = 0; m < ls.length; m++) {
                        if (m != 0) {
                            stringBuilder.append(',');
                        }

                        if (m % 16 == 0 && m / 16 > 0) {
                            stringBuilder.append('\n');
                            if (m < ls.length) {
                                indent(indentLevel + 1, stringBuilder);
                            }
                        } else if (m != 0) {
                            stringBuilder.append(' ');
                        }

                        stringBuilder.append(String.format(Locale.ROOT, "0x%0" + n + "X", ls[m]));
                    }
                } else {
                    indent(indentLevel + 1, stringBuilder).append(" // Skipped, supply withBinaryBlobs true");
                }

                stringBuilder.append('\n');
                indent(indentLevel, stringBuilder).append('}');
                break;
            default:
                stringBuilder.append("<UNKNOWN :(>");
        }

        return stringBuilder;
    }

    private static StringBuilder indent(int indentLevel, StringBuilder stringBuilder) {
        int i = stringBuilder.lastIndexOf("\n") + 1;
        int j = stringBuilder.length() - i;

        for (int k = 0; k < 2 * indentLevel - j; k++) {
            stringBuilder.append(' ');
        }

        return stringBuilder;
    }

    public static Component toPrettyComponent(Tag tag) {
        return new TextComponentTagVisitor("").visit(tag);
    }

    public static String structureToSnbt(CompoundTag tag) {
        return new SnbtPrinterTagVisitor().visit(packStructureTemplate(tag));
    }

    public static CompoundTag snbtToStructure(String text) throws CommandSyntaxException {
        return unpackStructureTemplate(TagParser.parseTag(text));
    }

    @VisibleForTesting
    static CompoundTag packStructureTemplate(CompoundTag tag) {
        boolean bl = tag.contains("palettes", 9);
        ListTag listTag;
        if (bl) {
            listTag = tag.getList("palettes", 9).getList(0);
        } else {
            listTag = tag.getList("palette", 10);
        }

        ListTag listTag2 = listTag.stream()
            .map(CompoundTag.class::cast)
            .map(NbtUtils::packBlockState)
            .map(StringTag::valueOf)
            .collect(Collectors.toCollection(ListTag::new));
        tag.put("palette", listTag2);
        if (bl) {
            ListTag listTag3 = new ListTag();
            ListTag listTag4 = tag.getList("palettes", 9);
            listTag4.stream().map(ListTag.class::cast).forEach(paletteTag -> {
                CompoundTag compoundTag = new CompoundTag();

                for (int i = 0; i < paletteTag.size(); i++) {
                    compoundTag.putString(listTag2.getString(i), packBlockState(paletteTag.getCompound(i)));
                }

                listTag3.add(compoundTag);
            });
            tag.put("palettes", listTag3);
        }

        if (tag.contains("entities", 9)) {
            ListTag listTag3 = tag.getList("entities", 10);
            ListTag listTag4 = listTag3.stream()
                .map(CompoundTag.class::cast)
                .sorted(Comparator.comparing(entityTag -> entityTag.getList("pos", 6), YXZ_LISTTAG_DOUBLE_COMPARATOR))
                .collect(Collectors.toCollection(ListTag::new));
            tag.put("entities", listTag4);
        }

        ListTag listTag3 = tag.getList("blocks", 10)
            .stream()
            .map(CompoundTag.class::cast)
            .sorted(Comparator.comparing(blockTag -> blockTag.getList("pos", 3), YXZ_LISTTAG_INT_COMPARATOR))
            .peek(blockTag -> blockTag.putString("state", listTag2.getString(blockTag.getInt("state"))))
            .collect(Collectors.toCollection(ListTag::new));
        tag.put("data", listTag3);
        tag.remove("blocks");
        return tag;
    }

    @VisibleForTesting
    static CompoundTag unpackStructureTemplate(CompoundTag tag) {
        ListTag listTag = tag.getList("palette", 8);
        Map<String, Tag> map = listTag.stream()
            .map(StringTag.class::cast)
            .map(StringTag::getAsString)
            .collect(ImmutableMap.toImmutableMap(Function.identity(), NbtUtils::unpackBlockState));
        if (tag.contains("palettes", 9)) {
            tag.put(
                "palettes",
                tag.getList("palettes", 10)
                    .stream()
                    .map(CompoundTag.class::cast)
                    .map(
                        paletteTag -> map.keySet()
                            .stream()
                            .map(paletteTag::getString)
                            .map(NbtUtils::unpackBlockState)
                            .collect(Collectors.toCollection(ListTag::new))
                    )
                    .collect(Collectors.toCollection(ListTag::new))
            );
            tag.remove("palette");
        } else {
            tag.put("palette", map.values().stream().collect(Collectors.toCollection(ListTag::new)));
        }

        if (tag.contains("data", 9)) {
            Object2IntMap<String> object2IntMap = new Object2IntOpenHashMap<>();
            object2IntMap.defaultReturnValue(-1);

            for (int i = 0; i < listTag.size(); i++) {
                object2IntMap.put(listTag.getString(i), i);
            }

            ListTag listTag2 = tag.getList("data", 10);

            for (int j = 0; j < listTag2.size(); j++) {
                CompoundTag compoundTag = listTag2.getCompound(j);
                String string = compoundTag.getString("state");
                int k = object2IntMap.getInt(string);
                if (k == -1) {
                    throw new IllegalStateException("Entry " + string + " missing from palette");
                }

                compoundTag.putInt("state", k);
            }

            tag.put("blocks", listTag2);
            tag.remove("data");
        }

        return tag;
    }

    @VisibleForTesting
    static String packBlockState(CompoundTag tag) {
        StringBuilder stringBuilder = new StringBuilder(tag.getString("Name"));
        if (tag.contains("Properties", 10)) {
            CompoundTag compoundTag = tag.getCompound("Properties");
            String string = compoundTag.getAllKeys()
                .stream()
                .sorted()
                .map(propertyKey -> propertyKey + ":" + compoundTag.get(propertyKey).getAsString())
                .collect(Collectors.joining(","));
            stringBuilder.append('{').append(string).append('}');
        }

        return stringBuilder.toString();
    }

    @VisibleForTesting
    static CompoundTag unpackBlockState(String blockStateText) {
        CompoundTag compoundTag = new CompoundTag();
        int i = blockStateText.indexOf(123);
        String string;
        if (i >= 0) {
            string = blockStateText.substring(0, i);
            CompoundTag compoundTag2 = new CompoundTag();
            if (i + 2 <= blockStateText.length()) {
                String string2 = blockStateText.substring(i + 1, blockStateText.indexOf(125, i));
                COMMA_SPLITTER.split(string2).forEach(stateMetadata -> {
                    List<String> list = COLON_SPLITTER.splitToList(stateMetadata);
                    if (list.size() == 2) {
                        compoundTag2.putString(list.get(0), list.get(1));
                    } else {
                        LOGGER.error("Something went wrong parsing: '{}' -- incorrect gamedata!", blockStateText);
                    }
                });
                compoundTag.put("Properties", compoundTag2);
            }
        } else {
            string = blockStateText;
        }

        compoundTag.putString("Name", string);
        return compoundTag;
    }

    public static CompoundTag addCurrentDataVersion(CompoundTag tag) {
        int i = SharedConstants.getCurrentVersion().getDataVersion().getVersion();
        return addDataVersion(tag, i);
    }

    public static CompoundTag addDataVersion(CompoundTag tag, int dataVersion) {
        tag.putInt("DataVersion", dataVersion);
        return tag;
    }

    public static int getDataVersion(CompoundTag tag, int defaultValue) {
        return tag.contains("DataVersion", 99) ? tag.getInt("DataVersion") : defaultValue;
    }
}
