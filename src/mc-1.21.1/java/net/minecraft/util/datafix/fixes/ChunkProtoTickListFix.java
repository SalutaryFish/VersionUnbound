package net.minecraft.util.datafix.fixes;

import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.types.templates.List.ListType;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.apache.commons.lang3.mutable.MutableInt;

public class ChunkProtoTickListFix extends DataFix {
    private static final int SECTION_WIDTH = 16;
    private static final ImmutableSet<String> ALWAYS_WATERLOGGED = ImmutableSet.of(
        "minecraft:bubble_column", "minecraft:kelp", "minecraft:kelp_plant", "minecraft:seagrass", "minecraft:tall_seagrass"
    );

    public ChunkProtoTickListFix(Schema outputSchema) {
        super(outputSchema, false);
    }

    @Override
    protected TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.CHUNK);
        OpticFinder<?> opticFinder = type.findField("Level");
        OpticFinder<?> opticFinder2 = opticFinder.type().findField("Sections");
        OpticFinder<?> opticFinder3 = ((ListType)opticFinder2.type()).getElement().finder();
        OpticFinder<?> opticFinder4 = opticFinder3.type().findField("block_states");
        OpticFinder<?> opticFinder5 = opticFinder3.type().findField("biomes");
        OpticFinder<?> opticFinder6 = opticFinder4.type().findField("palette");
        OpticFinder<?> opticFinder7 = opticFinder.type().findField("TileTicks");
        return this.fixTypeEverywhereTyped(
            "ChunkProtoTickListFix",
            type,
            typed -> typed.updateTyped(
                opticFinder,
                typedx -> {
                    typedx = typedx.update(
                        DSL.remainderFinder(),
                        dynamicx -> DataFixUtils.orElse(
                            dynamicx.get("LiquidTicks").result().map(dynamic2x -> dynamicx.set("fluid_ticks", dynamic2x).remove("LiquidTicks")), dynamicx
                        )
                    );
                    Dynamic<?> dynamic = typedx.get(DSL.remainderFinder());
                    MutableInt mutableInt = new MutableInt();
                    Int2ObjectMap<Supplier<ChunkProtoTickListFix.PoorMansPalettedContainer>> int2ObjectMap = new Int2ObjectArrayMap<>();
                    typedx.getOptionalTyped(opticFinder2)
                        .ifPresent(
                            typedxx -> typedxx.getAllTyped(opticFinder3)
                                .forEach(
                                    typedxxx -> {
                                        Dynamic<?> dynamicx = typedxxx.get(DSL.remainderFinder());
                                        int ix = dynamicx.get("Y").asInt(Integer.MAX_VALUE);
                                        if (ix != Integer.MAX_VALUE) {
                                            if (typedxxx.getOptionalTyped(opticFinder5).isPresent()) {
                                                mutableInt.setValue(Math.min(ix, mutableInt.getValue()));
                                            }

                                            typedxxx.getOptionalTyped(opticFinder4)
                                                .ifPresent(
                                                    typedxxxx -> int2ObjectMap.put(
                                                        ix,
                                                        Suppliers.memoize(
                                                            () -> {
                                                                List<? extends Dynamic<?>> list = typedxxxx.getOptionalTyped(opticFinder6)
                                                                    .map(
                                                                        typedxxxxxx -> typedxxxxxx.write()
                                                                            .result()
                                                                            .map(dynamicxx -> dynamicxx.asList(Function.identity()))
                                                                            .orElse(Collections.emptyList())
                                                                    )
                                                                    .orElse(Collections.emptyList());
                                                                long[] ls = typedxxxx.get(DSL.remainderFinder()).get("data").asLongStream().toArray();
                                                                return new ChunkProtoTickListFix.PoorMansPalettedContainer(list, ls);
                                                            }
                                                        )
                                                    )
                                                );
                                        }
                                    }
                                )
                        );
                    byte b = mutableInt.getValue().byteValue();
                    typedx = typedx.update(DSL.remainderFinder(), dynamicx -> dynamicx.update("yPos", dynamicxx -> dynamicxx.createByte(b)));
                    if (!typedx.getOptionalTyped(opticFinder7).isPresent() && !dynamic.get("fluid_ticks").result().isPresent()) {
                        int i = dynamic.get("xPos").asInt(0);
                        int j = dynamic.get("zPos").asInt(0);
                        Dynamic<?> dynamic2 = this.makeTickList(dynamic, int2ObjectMap, b, i, j, "LiquidsToBeTicked", ChunkProtoTickListFix::getLiquid);
                        Dynamic<?> dynamic3 = this.makeTickList(dynamic, int2ObjectMap, b, i, j, "ToBeTicked", ChunkProtoTickListFix::getBlock);
                        Optional<? extends Pair<? extends Typed<?>, ?>> optional = opticFinder7.type().readTyped(dynamic3).result();
                        if (optional.isPresent()) {
                            typedx = typedx.set(opticFinder7, (Typed<?>)optional.get().getFirst());
                        }

                        return typedx.update(
                            DSL.remainderFinder(), dynamic2x -> dynamic2x.remove("ToBeTicked").remove("LiquidsToBeTicked").set("fluid_ticks", dynamic2)
                        );
                    } else {
                        return typedx;
                    }
                }
            )
        );
    }

    private Dynamic<?> makeTickList(
        Dynamic<?> data,
        Int2ObjectMap<Supplier<ChunkProtoTickListFix.PoorMansPalettedContainer>> palette,
        byte y,
        int x,
        int z,
        String name,
        Function<Dynamic<?>, String> idGetter
    ) {
        Stream<Dynamic<?>> stream = Stream.empty();
        List<? extends Dynamic<?>> list = data.get(name).asList(Function.identity());

        for (int i = 0; i < list.size(); i++) {
            int j = i + y;
            Supplier<ChunkProtoTickListFix.PoorMansPalettedContainer> supplier = palette.get(j);
            Stream<? extends Dynamic<?>> stream2 = list.get(i)
                .asStream()
                .mapToInt(dynamic -> dynamic.asShort((short)-1))
                .filter(ix -> ix > 0)
                .mapToObj(l -> this.createTick(data, supplier, x, j, z, l, idGetter));
            stream = Stream.concat(stream, stream2);
        }

        return data.createList(stream);
    }

    private static String getBlock(@Nullable Dynamic<?> data) {
        return data != null ? data.get("Name").asString("minecraft:air") : "minecraft:air";
    }

    private static String getLiquid(@Nullable Dynamic<?> data) {
        if (data == null) {
            return "minecraft:empty";
        } else {
            String string = data.get("Name").asString("");
            if ("minecraft:water".equals(string)) {
                return data.get("Properties").get("level").asInt(0) == 0 ? "minecraft:water" : "minecraft:flowing_water";
            } else if ("minecraft:lava".equals(string)) {
                return data.get("Properties").get("level").asInt(0) == 0 ? "minecraft:lava" : "minecraft:flowing_lava";
            } else {
                return !ALWAYS_WATERLOGGED.contains(string) && !data.get("Properties").get("waterlogged").asBoolean(false)
                    ? "minecraft:empty"
                    : "minecraft:water";
            }
        }
    }

    private Dynamic<?> createTick(
        Dynamic<?> data,
        @Nullable Supplier<ChunkProtoTickListFix.PoorMansPalettedContainer> palette,
        int x,
        int y,
        int z,
        int index,
        Function<Dynamic<?>, String> idGetter
    ) {
        int i = index & 15;
        int j = index >>> 4 & 15;
        int k = index >>> 8 & 15;
        String string = idGetter.apply(palette != null ? palette.get().get(i, j, k) : null);
        return data.createMap(
            ImmutableMap.builder()
                .put(data.createString("i"), data.createString(string))
                .put(data.createString("x"), data.createInt(x * 16 + i))
                .put(data.createString("y"), data.createInt(y * 16 + j))
                .put(data.createString("z"), data.createInt(z * 16 + k))
                .put(data.createString("t"), data.createInt(0))
                .put(data.createString("p"), data.createInt(0))
                .build()
        );
    }

    public static final class PoorMansPalettedContainer {
        private static final long SIZE_BITS = 4L;
        private final List<? extends Dynamic<?>> palette;
        private final long[] data;
        private final int bits;
        private final long mask;
        private final int valuesPerLong;

        public PoorMansPalettedContainer(List<? extends Dynamic<?>> palette, long[] data) {
            this.palette = palette;
            this.data = data;
            this.bits = Math.max(4, ChunkHeightAndBiomeFix.ceillog2(palette.size()));
            this.mask = (1L << this.bits) - 1L;
            this.valuesPerLong = (char)(64 / this.bits);
        }

        @Nullable
        public Dynamic<?> get(int x, int y, int z) {
            int i = this.palette.size();
            if (i < 1) {
                return null;
            } else if (i == 1) {
                return (Dynamic<?>)this.palette.get(0);
            } else {
                int j = this.getIndex(x, y, z);
                int k = j / this.valuesPerLong;
                if (k >= 0 && k < this.data.length) {
                    long l = this.data[k];
                    int m = (j - k * this.valuesPerLong) * this.bits;
                    int n = (int)(l >> m & this.mask);
                    return (Dynamic<?>)(n >= 0 && n < i ? this.palette.get(n) : null);
                } else {
                    return null;
                }
            }
        }

        private int getIndex(int x, int y, int z) {
            return (y << 4 | z) << 4 | x;
        }

        public List<? extends Dynamic<?>> palette() {
            return this.palette;
        }

        public long[] data() {
            return this.data;
        }
    }
}
