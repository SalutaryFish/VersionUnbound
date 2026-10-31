package net.minecraft.server.dedicated;

import com.google.common.base.MoreObjects;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Properties;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;
import net.minecraft.core.RegistryAccess;
import org.slf4j.Logger;

public abstract class Settings<T extends Settings<T>> {
    private static final Logger LOGGER = LogUtils.getLogger();
    protected final Properties properties;

    public Settings(Properties properties) {
        this.properties = properties;
    }

    public static Properties loadFromFile(Path path) {
        try {
            try (InputStream inputStream = Files.newInputStream(path)) {
                CharsetDecoder charsetDecoder = StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
                Properties properties = new Properties();
                properties.load(new InputStreamReader(inputStream, charsetDecoder));
                return properties;
            } catch (CharacterCodingException characterCodingException) {
                LOGGER.info("Failed to load properties as UTF-8 from file {}, trying ISO_8859_1", path);

                try (Reader reader = Files.newBufferedReader(path, StandardCharsets.ISO_8859_1)) {
                    Properties properties = new Properties();
                    properties.load(reader);
                    return properties;
                }
            }
        } catch (IOException iOException) {
            LOGGER.error("Failed to load properties from file: {}", path, iOException);
            return new Properties();
        }
    }

    public void store(Path path) {
        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            this.properties.store(writer, "Minecraft server properties");
        } catch (IOException iOException) {
            LOGGER.error("Failed to store properties to file: {}", path);
        }
    }

    private static <V extends Number> Function<String, V> wrapNumberDeserializer(Function<String, V> parseFunc) {
        return string -> {
            try {
                return parseFunc.apply(string);
            } catch (NumberFormatException numberFormatException) {
                return null;
            }
        };
    }

    protected static <V> Function<String, V> dispatchNumberOrString(IntFunction<V> byId, Function<String, V> byName) {
        return string -> {
            try {
                return byId.apply(Integer.parseInt(string));
            } catch (NumberFormatException numberFormatException) {
                return byName.apply(string);
            }
        };
    }

    @Nullable
    private String getStringRaw(String key) {
        return (String)this.properties.get(key);
    }

    @Nullable
    protected <V> V getLegacy(String key, Function<String, V> serializer) {
        String string = this.getStringRaw(key);
        if (string == null) {
            return null;
        }

        this.properties.remove(key);
        return serializer.apply(string);
    }

    protected <V> V get(String key, Function<String, V> serializer, Function<V, String> deserializer, V defaultValue) {
        String string = this.getStringRaw(key);
        V object = MoreObjects.firstNonNull(string != null ? serializer.apply(string) : null, defaultValue);
        this.properties.put(key, deserializer.apply(object));
        return object;
    }

    protected <V> Settings<T>.MutableValue<V> getMutable(String key, Function<String, V> serializer, Function<V, String> deserializer, V defaultValue) {
        String string = this.getStringRaw(key);
        V object = MoreObjects.firstNonNull(string != null ? serializer.apply(string) : null, defaultValue);
        this.properties.put(key, deserializer.apply(object));
        return new Settings.MutableValue<>(key, object, deserializer);
    }

    protected <V> V get(String key, Function<String, V> serializer, UnaryOperator<V> modifier, Function<V, String> deserializer, V defaultValue) {
        return this.get(key, string -> {
            V object = serializer.apply(string);
            return object != null ? modifier.apply(object) : null;
        }, deserializer, defaultValue);
    }

    protected <V> V get(String key, Function<String, V> mapper, V value) {
        return this.get(key, mapper, Objects::toString, value);
    }

    protected <V> Settings<T>.MutableValue<V> getMutable(String key, Function<String, V> serializer, V defaultValue) {
        return this.getMutable(key, serializer, Objects::toString, defaultValue);
    }

    protected String get(String key, String defaultValue) {
        return this.get(key, Function.identity(), Function.identity(), defaultValue);
    }

    @Nullable
    protected String getLegacyString(String key) {
        return this.getLegacy(key, Function.identity());
    }

    protected int get(String key, int defaultValue) {
        return this.get(key, wrapNumberDeserializer(Integer::parseInt), Integer.valueOf(defaultValue));
    }

    protected Settings<T>.MutableValue<Integer> getMutable(String key, int defaultValue) {
        return this.getMutable(key, wrapNumberDeserializer(Integer::parseInt), defaultValue);
    }

    protected int get(String key, UnaryOperator<Integer> modifier, int defaultValue) {
        return this.get(key, wrapNumberDeserializer(Integer::parseInt), modifier, Objects::toString, defaultValue);
    }

    protected long get(String key, long defaultValue) {
        return this.get(key, wrapNumberDeserializer(Long::parseLong), defaultValue);
    }

    protected boolean get(String key, boolean defaultValue) {
        return this.get(key, Boolean::valueOf, defaultValue);
    }

    protected Settings<T>.MutableValue<Boolean> getMutable(String key, boolean defaultValue) {
        return this.getMutable(key, Boolean::valueOf, defaultValue);
    }

    @Nullable
    protected Boolean getLegacyBoolean(String key) {
        return this.getLegacy(key, Boolean::valueOf);
    }

    protected Properties cloneProperties() {
        Properties properties = new Properties();
        properties.putAll(this.properties);
        return properties;
    }

    protected abstract T reload(RegistryAccess var1, Properties var2);

    public class MutableValue<V> implements Supplier<V> {
        private final String key;
        private final V value;
        private final Function<V, String> serializer;

        MutableValue(final String key, final V value, final Function<V, String> serializer) {
            this.key = key;
            this.value = value;
            this.serializer = serializer;
        }

        @Override
        public V get() {
            return this.value;
        }

        public T update(RegistryAccess registryAccess, V newValue) {
            Properties properties = Settings.this.cloneProperties();
            properties.put(this.key, this.serializer.apply(newValue));
            return Settings.this.reload(registryAccess, properties);
        }
    }
}
