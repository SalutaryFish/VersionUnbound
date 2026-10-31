package net.minecraft.commands;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.internal.Streams;
import com.google.gson.stream.JsonReader;
import com.mojang.brigadier.StringReader;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import java.lang.reflect.Field;
import net.minecraft.CharPredicate;
import net.minecraft.Util;
import net.minecraft.core.HolderLookup;

public class ParserUtils {
    private static final Field JSON_READER_POS = Util.make(() -> {
        try {
            Field field = JsonReader.class.getDeclaredField("pos");
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException noSuchFieldException) {
            throw new IllegalStateException("Couldn't get field 'pos' for JsonReader", noSuchFieldException);
        }
    });
    private static final Field JSON_READER_LINESTART = Util.make(() -> {
        try {
            Field field = JsonReader.class.getDeclaredField("lineStart");
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException noSuchFieldException) {
            throw new IllegalStateException("Couldn't get field 'lineStart' for JsonReader", noSuchFieldException);
        }
    });

    private static int getPos(JsonReader reader) {
        try {
            return JSON_READER_POS.getInt(reader) - JSON_READER_LINESTART.getInt(reader);
        } catch (IllegalAccessException illegalAccessException) {
            throw new IllegalStateException("Couldn't read position of JsonReader", illegalAccessException);
        }
    }

    public static <T> T parseJson(HolderLookup.Provider registries, StringReader reader, Codec<T> codec) {
        JsonReader jsonReader = new JsonReader(new java.io.StringReader(reader.getRemaining()));
        jsonReader.setLenient(false);

        try {
            JsonElement jsonElement = Streams.parse(jsonReader);
            return codec.parse(registries.createSerializationContext(JsonOps.INSTANCE), jsonElement).getOrThrow(JsonParseException::new);
        } catch (StackOverflowError stackOverflowError) {
            throw new JsonParseException(stackOverflowError);
        } finally {
            reader.setCursor(reader.getCursor() + getPos(jsonReader));
        }
    }

    public static String readWhile(StringReader reader, CharPredicate predicate) {
        int i = reader.getCursor();

        while (reader.canRead() && predicate.test(reader.peek())) {
            reader.skip();
        }

        return reader.getString().substring(i, reader.getCursor());
    }
}
