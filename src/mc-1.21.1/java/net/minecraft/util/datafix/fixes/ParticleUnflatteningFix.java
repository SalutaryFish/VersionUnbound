package net.minecraft.util.datafix.fixes;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Dynamic;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.TagParser;
import net.minecraft.util.Mth;
import net.minecraft.util.datafix.schemas.NamespacedSchema;
import org.slf4j.Logger;

public class ParticleUnflatteningFix extends DataFix {
    private static final Logger LOGGER = LogUtils.getLogger();

    public ParticleUnflatteningFix(Schema outputSchema) {
        super(outputSchema, true);
    }

    @Override
    protected TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.PARTICLE);
        Type<?> type2 = this.getOutputSchema().getType(References.PARTICLE);
        return this.writeFixAndRead("ParticleUnflatteningFix", type, type2, this::fix);
    }

    private <T> Dynamic<T> fix(Dynamic<T> tag) {
        Optional<String> optional = tag.asString().result();
        if (optional.isEmpty()) {
            return tag;
        }

        String string = optional.get();
        String[] strings = string.split(" ", 2);
        String string2 = NamespacedSchema.ensureNamespaced(strings[0]);
        Dynamic<T> dynamic = tag.createMap(Map.of(tag.createString("type"), tag.createString(string2)));

        return switch (string2) {
            case "minecraft:item" -> strings.length > 1 ? this.updateItem(dynamic, strings[1]) : dynamic;
            case "minecraft:block", "minecraft:block_marker", "minecraft:falling_dust", "minecraft:dust_pillar" -> strings.length > 1
                ? this.updateBlock(dynamic, strings[1])
                : dynamic;
            case "minecraft:dust" -> strings.length > 1 ? this.updateDust(dynamic, strings[1]) : dynamic;
            case "minecraft:dust_color_transition" -> strings.length > 1 ? this.updateDustTransition(dynamic, strings[1]) : dynamic;
            case "minecraft:sculk_charge" -> strings.length > 1 ? this.updateSculkCharge(dynamic, strings[1]) : dynamic;
            case "minecraft:vibration" -> strings.length > 1 ? this.updateVibration(dynamic, strings[1]) : dynamic;
            case "minecraft:shriek" -> strings.length > 1 ? this.updateShriek(dynamic, strings[1]) : dynamic;
            default -> dynamic;
        };
    }

    private <T> Dynamic<T> updateItem(Dynamic<T> tag, String item) {
        int i = item.indexOf("{");
        Dynamic<T> dynamic = tag.createMap(Map.of(tag.createString("Count"), tag.createInt(1)));
        if (i == -1) {
            dynamic = dynamic.set("id", tag.createString(item));
        } else {
            dynamic = dynamic.set("id", tag.createString(item.substring(0, i)));
            CompoundTag compoundTag = parseTag(item.substring(i));
            if (compoundTag != null) {
                dynamic = dynamic.set("tag", new Dynamic<>(NbtOps.INSTANCE, compoundTag).convert(tag.getOps()));
            }
        }

        return tag.set("item", dynamic);
    }

    @Nullable
    private static CompoundTag parseTag(String tag) {
        try {
            return TagParser.parseTag(tag);
        } catch (Exception exception) {
            LOGGER.warn("Failed to parse tag: {}", tag, exception);
            return null;
        }
    }

    private <T> Dynamic<T> updateBlock(Dynamic<T> tag, String block) {
        int i = block.indexOf("[");
        Dynamic<T> dynamic = tag.emptyMap();
        if (i == -1) {
            dynamic = dynamic.set("Name", tag.createString(NamespacedSchema.ensureNamespaced(block)));
        } else {
            dynamic = dynamic.set("Name", tag.createString(NamespacedSchema.ensureNamespaced(block.substring(0, i))));
            Map<Dynamic<T>, Dynamic<T>> map = parseBlockProperties(tag, block.substring(i));
            if (!map.isEmpty()) {
                dynamic = dynamic.set("Properties", tag.createMap(map));
            }
        }

        return tag.set("block_state", dynamic);
    }

    private static <T> Map<Dynamic<T>, Dynamic<T>> parseBlockProperties(Dynamic<T> tag, String properties) {
        try {
            Map<Dynamic<T>, Dynamic<T>> map = new HashMap<>();
            StringReader stringReader = new StringReader(properties);
            stringReader.expect('[');
            stringReader.skipWhitespace();

            while (stringReader.canRead() && stringReader.peek() != ']') {
                stringReader.skipWhitespace();
                String string = stringReader.readString();
                stringReader.skipWhitespace();
                stringReader.expect('=');
                stringReader.skipWhitespace();
                String string2 = stringReader.readString();
                stringReader.skipWhitespace();
                map.put(tag.createString(string), tag.createString(string2));
                if (stringReader.canRead()) {
                    if (stringReader.peek() != ',') {
                        break;
                    }

                    stringReader.skip();
                }
            }

            stringReader.expect(']');
            return map;
        } catch (Exception exception) {
            LOGGER.warn("Failed to parse block properties: {}", properties, exception);
            return Map.of();
        }
    }

    private static <T> Dynamic<T> readVector(Dynamic<T> tag, StringReader reader) throws CommandSyntaxException {
        float f = reader.readFloat();
        reader.expect(' ');
        float g = reader.readFloat();
        reader.expect(' ');
        float h = reader.readFloat();
        return tag.createList(Stream.of(f, g, h).map(tag::createFloat));
    }

    private <T> Dynamic<T> updateDust(Dynamic<T> tag, String options) {
        try {
            StringReader stringReader = new StringReader(options);
            Dynamic<T> dynamic = readVector(tag, stringReader);
            stringReader.expect(' ');
            float f = stringReader.readFloat();
            return tag.set("color", dynamic).set("scale", tag.createFloat(f));
        } catch (Exception exception) {
            LOGGER.warn("Failed to parse particle options: {}", options, exception);
            return tag;
        }
    }

    private <T> Dynamic<T> updateDustTransition(Dynamic<T> tag, String options) {
        try {
            StringReader stringReader = new StringReader(options);
            Dynamic<T> dynamic = readVector(tag, stringReader);
            stringReader.expect(' ');
            float f = stringReader.readFloat();
            stringReader.expect(' ');
            Dynamic<T> dynamic2 = readVector(tag, stringReader);
            return tag.set("from_color", dynamic).set("to_color", dynamic2).set("scale", tag.createFloat(f));
        } catch (Exception exception) {
            LOGGER.warn("Failed to parse particle options: {}", options, exception);
            return tag;
        }
    }

    private <T> Dynamic<T> updateSculkCharge(Dynamic<T> tag, String options) {
        try {
            StringReader stringReader = new StringReader(options);
            float f = stringReader.readFloat();
            return tag.set("roll", tag.createFloat(f));
        } catch (Exception exception) {
            LOGGER.warn("Failed to parse particle options: {}", options, exception);
            return tag;
        }
    }

    private <T> Dynamic<T> updateVibration(Dynamic<T> tag, String options) {
        try {
            StringReader stringReader = new StringReader(options);
            float f = (float)stringReader.readDouble();
            stringReader.expect(' ');
            float g = (float)stringReader.readDouble();
            stringReader.expect(' ');
            float h = (float)stringReader.readDouble();
            stringReader.expect(' ');
            int i = stringReader.readInt();
            Dynamic<T> dynamic = (Dynamic<T>)tag.createIntList(IntStream.of(Mth.floor(f), Mth.floor(g), Mth.floor(h)));
            Dynamic<T> dynamic2 = tag.createMap(Map.of(tag.createString("type"), tag.createString("minecraft:block"), tag.createString("pos"), dynamic));
            return tag.set("destination", dynamic2).set("arrival_in_ticks", tag.createInt(i));
        } catch (Exception exception) {
            LOGGER.warn("Failed to parse particle options: {}", options, exception);
            return tag;
        }
    }

    private <T> Dynamic<T> updateShriek(Dynamic<T> tag, String options) {
        try {
            StringReader stringReader = new StringReader(options);
            int i = stringReader.readInt();
            return tag.set("delay", tag.createInt(i));
        } catch (Exception exception) {
            LOGGER.warn("Failed to parse particle options: {}", options, exception);
            return tag;
        }
    }
}
