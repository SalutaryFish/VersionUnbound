package net.minecraft.util.datafix.fixes;

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
import java.util.List;
import java.util.stream.LongStream;
import net.minecraft.util.Mth;

public class BitStorageAlignFix extends DataFix {
    private static final int BIT_TO_LONG_SHIFT = 6;
    private static final int SECTION_WIDTH = 16;
    private static final int SECTION_HEIGHT = 16;
    private static final int SECTION_SIZE = 4096;
    private static final int HEIGHTMAP_BITS = 9;
    private static final int HEIGHTMAP_SIZE = 256;

    public BitStorageAlignFix(Schema outputSchema) {
        super(outputSchema, false);
    }

    @Override
    protected TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.CHUNK);
        Type<?> type2 = type.findFieldType("Level");
        OpticFinder<?> opticFinder = DSL.fieldFinder("Level", type2);
        OpticFinder<?> opticFinder2 = opticFinder.type().findField("Sections");
        Type<?> type3 = ((ListType)opticFinder2.type()).getElement();
        OpticFinder<?> opticFinder3 = DSL.typeFinder(type3);
        Type<Pair<String, Dynamic<?>>> type4 = DSL.named(References.BLOCK_STATE.typeName(), DSL.remainderType());
        OpticFinder<List<Pair<String, Dynamic<?>>>> opticFinder4 = DSL.fieldFinder("Palette", DSL.list(type4));
        return this.fixTypeEverywhereTyped(
            "BitStorageAlignFix",
            type,
            this.getOutputSchema().getType(References.CHUNK),
            typed -> typed.updateTyped(opticFinder, typedx -> this.updateHeightmaps(updateSections(opticFinder2, opticFinder3, opticFinder4, typedx)))
        );
    }

    private Typed<?> updateHeightmaps(Typed<?> data) {
        return data.update(
            DSL.remainderFinder(),
            dynamic -> dynamic.update(
                "Heightmaps", dynamic2 -> dynamic2.updateMapValues(pair -> pair.mapSecond(dynamic2x -> updateBitStorage(dynamic, dynamic2x, 256, 9)))
            )
        );
    }

    private static Typed<?> updateSections(
        OpticFinder<?> sections, OpticFinder<?> opticFinder, OpticFinder<List<Pair<String, Dynamic<?>>>> palette, Typed<?> data
    ) {
        return data.updateTyped(
            sections,
            typed -> typed.updateTyped(
                opticFinder,
                typedx -> {
                    int i = typedx.getOptional(palette).map(list -> Math.max(4, DataFixUtils.ceillog2(list.size()))).orElse(0);
                    return i != 0 && !Mth.isPowerOfTwo(i)
                        ? typedx.update(
                            DSL.remainderFinder(), dynamic -> dynamic.update("BlockStates", dynamic2 -> updateBitStorage(dynamic, dynamic2, 4096, i))
                        )
                        : typedx;
                }
            )
        );
    }

    private static Dynamic<?> updateBitStorage(Dynamic<?> output, Dynamic<?> data, int numBits, int bitWidth) {
        long[] ls = data.asLongStream().toArray();
        long[] ms = addPadding(numBits, bitWidth, ls);
        return output.createLongList(LongStream.of(ms));
    }

    public static long[] addPadding(int numBits, int bitWidth, long[] inputData) {
        int i = inputData.length;
        if (i == 0) {
            return inputData;
        }

        long l = (1L << bitWidth) - 1L;
        int j = 64 / bitWidth;
        int k = (numBits + j - 1) / j;
        long[] ls = new long[k];
        int m = 0;
        int n = 0;
        long o = 0L;
        int p = 0;
        long q = inputData[0];
        long r = i > 1 ? inputData[1] : 0L;

        for (int s = 0; s < numBits; s++) {
            int t = s * bitWidth;
            int u = t >> 6;
            int v = (s + 1) * bitWidth - 1 >> 6;
            int w = t ^ u << 6;
            if (u != p) {
                q = r;
                r = u + 1 < i ? inputData[u + 1] : 0L;
                p = u;
            }

            long x;
            if (u == v) {
                x = q >>> w & l;
            } else {
                int y = 64 - w;
                x = (q >>> w | r << y) & l;
            }

            int y = n + bitWidth;
            if (y >= 64) {
                ls[m++] = o;
                o = x;
                n = bitWidth;
            } else {
                o |= x << n;
                n = y;
            }
        }

        if (o != 0L) {
            ls[m] = o;
        }

        return ls;
    }
}
