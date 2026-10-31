package net.minecraft.nbt;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Lifecycle;
import java.util.List;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;

public class TagParser {
    public static final SimpleCommandExceptionType ERROR_TRAILING_DATA = new SimpleCommandExceptionType(Component.translatable("argument.nbt.trailing"));
    public static final SimpleCommandExceptionType ERROR_EXPECTED_KEY = new SimpleCommandExceptionType(Component.translatable("argument.nbt.expected.key"));
    public static final SimpleCommandExceptionType ERROR_EXPECTED_VALUE = new SimpleCommandExceptionType(Component.translatable("argument.nbt.expected.value"));
    public static final Dynamic2CommandExceptionType ERROR_INSERT_MIXED_LIST = new Dynamic2CommandExceptionType(
        (listElementType, elementType) -> Component.translatableEscape("argument.nbt.list.mixed", listElementType, elementType)
    );
    public static final Dynamic2CommandExceptionType ERROR_INSERT_MIXED_ARRAY = new Dynamic2CommandExceptionType(
        (elementType, arrayType) -> Component.translatableEscape("argument.nbt.array.mixed", elementType, arrayType)
    );
    public static final DynamicCommandExceptionType ERROR_INVALID_ARRAY = new DynamicCommandExceptionType(
        arrayType -> Component.translatableEscape("argument.nbt.array.invalid", arrayType)
    );
    public static final char ELEMENT_SEPARATOR = ',';
    public static final char NAME_VALUE_SEPARATOR = ':';
    private static final char LIST_OPEN = '[';
    private static final char LIST_CLOSE = ']';
    private static final char STRUCT_CLOSE = '}';
    private static final char STRUCT_OPEN = '{';
    private static final Pattern DOUBLE_PATTERN_NOSUFFIX = Pattern.compile("[-+]?(?:[0-9]+[.]|[0-9]*[.][0-9]+)(?:e[-+]?[0-9]+)?", 2);
    private static final Pattern DOUBLE_PATTERN = Pattern.compile("[-+]?(?:[0-9]+[.]?|[0-9]*[.][0-9]+)(?:e[-+]?[0-9]+)?d", 2);
    private static final Pattern FLOAT_PATTERN = Pattern.compile("[-+]?(?:[0-9]+[.]?|[0-9]*[.][0-9]+)(?:e[-+]?[0-9]+)?f", 2);
    private static final Pattern BYTE_PATTERN = Pattern.compile("[-+]?(?:0|[1-9][0-9]*)b", 2);
    private static final Pattern LONG_PATTERN = Pattern.compile("[-+]?(?:0|[1-9][0-9]*)l", 2);
    private static final Pattern SHORT_PATTERN = Pattern.compile("[-+]?(?:0|[1-9][0-9]*)s", 2);
    private static final Pattern INT_PATTERN = Pattern.compile("[-+]?(?:0|[1-9][0-9]*)");
    public static final Codec<CompoundTag> AS_CODEC = Codec.STRING.comapFlatMap(string -> {
        try {
            return DataResult.success(new TagParser(new StringReader(string)).readSingleStruct(), Lifecycle.stable());
        } catch (CommandSyntaxException commandSyntaxException) {
            return DataResult.error(commandSyntaxException::getMessage);
        }
    }, CompoundTag::toString);
    public static final Codec<CompoundTag> LENIENT_CODEC = Codec.withAlternative(AS_CODEC, CompoundTag.CODEC);
    private final StringReader reader;

    public static CompoundTag parseTag(String text) throws CommandSyntaxException {
        return new TagParser(new StringReader(text)).readSingleStruct();
    }

    @VisibleForTesting
    CompoundTag readSingleStruct() throws CommandSyntaxException {
        CompoundTag compoundTag = this.readStruct();
        this.reader.skipWhitespace();
        if (this.reader.canRead()) {
            throw ERROR_TRAILING_DATA.createWithContext(this.reader);
        } else {
            return compoundTag;
        }
    }

    public TagParser(StringReader reader) {
        this.reader = reader;
    }

    protected String readKey() throws CommandSyntaxException {
        this.reader.skipWhitespace();
        if (!this.reader.canRead()) {
            throw ERROR_EXPECTED_KEY.createWithContext(this.reader);
        } else {
            return this.reader.readString();
        }
    }

    protected Tag readTypedValue() throws CommandSyntaxException {
        this.reader.skipWhitespace();
        int i = this.reader.getCursor();
        if (StringReader.isQuotedStringStart(this.reader.peek())) {
            return StringTag.valueOf(this.reader.readQuotedString());
        } else {
            String string = this.reader.readUnquotedString();
            if (string.isEmpty()) {
                this.reader.setCursor(i);
                throw ERROR_EXPECTED_VALUE.createWithContext(this.reader);
            } else {
                return this.type(string);
            }
        }
    }

    private Tag type(String value) {
        try {
            if (FLOAT_PATTERN.matcher(value).matches()) {
                return FloatTag.valueOf(Float.parseFloat(value.substring(0, value.length() - 1)));
            }

            if (BYTE_PATTERN.matcher(value).matches()) {
                return ByteTag.valueOf(Byte.parseByte(value.substring(0, value.length() - 1)));
            }

            if (LONG_PATTERN.matcher(value).matches()) {
                return LongTag.valueOf(Long.parseLong(value.substring(0, value.length() - 1)));
            }

            if (SHORT_PATTERN.matcher(value).matches()) {
                return ShortTag.valueOf(Short.parseShort(value.substring(0, value.length() - 1)));
            }

            if (INT_PATTERN.matcher(value).matches()) {
                return IntTag.valueOf(Integer.parseInt(value));
            }

            if (DOUBLE_PATTERN.matcher(value).matches()) {
                return DoubleTag.valueOf(Double.parseDouble(value.substring(0, value.length() - 1)));
            }

            if (DOUBLE_PATTERN_NOSUFFIX.matcher(value).matches()) {
                return DoubleTag.valueOf(Double.parseDouble(value));
            }

            if ("true".equalsIgnoreCase(value)) {
                return ByteTag.ONE;
            }

            if ("false".equalsIgnoreCase(value)) {
                return ByteTag.ZERO;
            }
        } catch (NumberFormatException var3) {
        }

        return StringTag.valueOf(value);
    }

    public Tag readValue() throws CommandSyntaxException {
        this.reader.skipWhitespace();
        if (!this.reader.canRead()) {
            throw ERROR_EXPECTED_VALUE.createWithContext(this.reader);
        } else {
            char c = this.reader.peek();
            if (c == '{') {
                return this.readStruct();
            } else {
                return c == '[' ? this.readList() : this.readTypedValue();
            }
        }
    }

    protected Tag readList() throws CommandSyntaxException {
        return this.reader.canRead(3) && !StringReader.isQuotedStringStart(this.reader.peek(1)) && this.reader.peek(2) == ';'
            ? this.readArrayTag()
            : this.readListTag();
    }

    public CompoundTag readStruct() throws CommandSyntaxException {
        this.expect('{');
        CompoundTag compoundTag = new CompoundTag();
        this.reader.skipWhitespace();

        while (this.reader.canRead() && this.reader.peek() != '}') {
            int i = this.reader.getCursor();
            String string = this.readKey();
            if (string.isEmpty()) {
                this.reader.setCursor(i);
                throw ERROR_EXPECTED_KEY.createWithContext(this.reader);
            }

            this.expect(':');
            compoundTag.put(string, this.readValue());
            if (!this.hasElementSeparator()) {
                break;
            }

            if (!this.reader.canRead()) {
                throw ERROR_EXPECTED_KEY.createWithContext(this.reader);
            }
        }

        this.expect('}');
        return compoundTag;
    }

    private Tag readListTag() throws CommandSyntaxException {
        this.expect('[');
        this.reader.skipWhitespace();
        if (!this.reader.canRead()) {
            throw ERROR_EXPECTED_VALUE.createWithContext(this.reader);
        }

        ListTag listTag = new ListTag();
        TagType<?> tagType = null;

        while (this.reader.peek() != ']') {
            int i = this.reader.getCursor();
            Tag tag = this.readValue();
            TagType<?> tagType2 = tag.getType();
            if (tagType == null) {
                tagType = tagType2;
            } else if (tagType2 != tagType) {
                this.reader.setCursor(i);
                throw ERROR_INSERT_MIXED_LIST.createWithContext(this.reader, tagType2.getPrettyName(), tagType.getPrettyName());
            }

            listTag.add(tag);
            if (!this.hasElementSeparator()) {
                break;
            }

            if (!this.reader.canRead()) {
                throw ERROR_EXPECTED_VALUE.createWithContext(this.reader);
            }
        }

        this.expect(']');
        return listTag;
    }

    private Tag readArrayTag() throws CommandSyntaxException {
        this.expect('[');
        int i = this.reader.getCursor();
        char c = this.reader.read();
        this.reader.read();
        this.reader.skipWhitespace();
        if (!this.reader.canRead()) {
            throw ERROR_EXPECTED_VALUE.createWithContext(this.reader);
        }

        if (c == 'B') {
            return new ByteArrayTag(this.readArray(ByteArrayTag.TYPE, ByteTag.TYPE));
        }

        if (c == 'L') {
            return new LongArrayTag(this.readArray(LongArrayTag.TYPE, LongTag.TYPE));
        }

        if (c == 'I') {
            return new IntArrayTag(this.readArray(IntArrayTag.TYPE, IntTag.TYPE));
        }

        this.reader.setCursor(i);
        throw ERROR_INVALID_ARRAY.createWithContext(this.reader, String.valueOf(c));
    }

    private <T extends Number> List<T> readArray(TagType<?> arrayType, TagType<?> elementType) throws CommandSyntaxException {
        List<T> list = Lists.newArrayList();

        while (this.reader.peek() != ']') {
            int i = this.reader.getCursor();
            Tag tag = this.readValue();
            TagType<?> tagType = tag.getType();
            if (tagType != elementType) {
                this.reader.setCursor(i);
                throw ERROR_INSERT_MIXED_ARRAY.createWithContext(this.reader, tagType.getPrettyName(), arrayType.getPrettyName());
            }

            if (elementType == ByteTag.TYPE) {
                list.add((T)((NumericTag)tag).getAsByte());
            } else if (elementType == LongTag.TYPE) {
                list.add((T)((NumericTag)tag).getAsLong());
            } else {
                list.add((T)((NumericTag)tag).getAsInt());
            }

            if (!this.hasElementSeparator()) {
                break;
            }

            if (!this.reader.canRead()) {
                throw ERROR_EXPECTED_VALUE.createWithContext(this.reader);
            }
        }

        this.expect(']');
        return list;
    }

    private boolean hasElementSeparator() {
        this.reader.skipWhitespace();
        if (this.reader.canRead() && this.reader.peek() == ',') {
            this.reader.skip();
            this.reader.skipWhitespace();
            return true;
        } else {
            return false;
        }
    }

    private void expect(char expected) throws CommandSyntaxException {
        this.reader.skipWhitespace();
        this.reader.expect(expected);
    }
}
