package net.minecraft.client.gui.font.providers;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.blaze3d.font.SheetGlyphInfo;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.datafixers.util.Either;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.bytes.ByteList;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.io.IOException;
import java.io.InputStream;
import java.nio.IntBuffer;
import java.util.List;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.annotation.Nullable;
import net.minecraft.client.gui.font.CodepointMap;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.FastBufferedInputStream;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;

public class UnihexProvider implements GlyphProvider {
    static final Logger LOGGER = LogUtils.getLogger();
    private static final int GLYPH_HEIGHT = 16;
    private static final int DIGITS_PER_BYTE = 2;
    private static final int DIGITS_FOR_WIDTH_8 = 32;
    private static final int DIGITS_FOR_WIDTH_16 = 64;
    private static final int DIGITS_FOR_WIDTH_24 = 96;
    private static final int DIGITS_FOR_WIDTH_32 = 128;
    private final CodepointMap<UnihexProvider.Glyph> glyphs;

    UnihexProvider(CodepointMap<UnihexProvider.Glyph> glyph) {
        this.glyphs = glyph;
    }

    @Nullable
    @Override
    public GlyphInfo getGlyph(int character) {
        return this.glyphs.get(character);
    }

    @Override
    public IntSet getSupportedGlyphs() {
        return this.glyphs.keySet();
    }

    @VisibleForTesting
    static void unpackBitsToBytes(IntBuffer buffer, int lineData, int left, int right) {
        int i = 32 - left - 1;
        int j = 32 - right - 1;

        for (int k = i; k >= j; k--) {
            if (k < 32 && k >= 0) {
                boolean bl = (lineData >> k & 1) != 0;
                buffer.put(bl ? -1 : 0);
            } else {
                buffer.put(0);
            }
        }
    }

    static void unpackBitsToBytes(IntBuffer buffer, UnihexProvider.LineData lineData, int left, int right) {
        for (int i = 0; i < 16; i++) {
            int j = lineData.line(i);
            unpackBitsToBytes(buffer, j, left, right);
        }
    }

    @VisibleForTesting
    static void readFromStream(InputStream stream, UnihexProvider.ReaderOutput output) throws IOException {
        int i = 0;
        ByteList byteList = new ByteArrayList(128);

        while (true) {
            boolean bl = copyUntil(stream, byteList, 58);
            int j = byteList.size();
            if (j == 0 && !bl) {
                return;
            }

            if (!bl || j != 4 && j != 5 && j != 6) {
                throw new IllegalArgumentException("Invalid entry at line " + i + ": expected 4, 5 or 6 hex digits followed by a colon");
            }

            int k = 0;

            for (int l = 0; l < j; l++) {
                k = k << 4 | decodeHex(i, byteList.getByte(l));
            }

            byteList.clear();
            copyUntil(stream, byteList, 10);
            int l = byteList.size();

            UnihexProvider.LineData lineData = switch (l) {
                case 32 -> UnihexProvider.ByteContents.read(i, byteList);
                case 64 -> UnihexProvider.ShortContents.read(i, byteList);
                case 96 -> UnihexProvider.IntContents.read24(i, byteList);
                case 128 -> UnihexProvider.IntContents.read32(i, byteList);
                default -> throw new IllegalArgumentException(
                    "Invalid entry at line " + i + ": expected hex number describing (8,16,24,32) x 16 bitmap, followed by a new line"
                );
            };
            output.accept(k, lineData);
            i++;
            byteList.clear();
        }
    }

    static int decodeHex(int lineNumber, ByteList byteList, int index) {
        return decodeHex(lineNumber, byteList.getByte(index));
    }

    private static int decodeHex(int lineNumber, byte data) {
        return switch (data) {
            case 48 -> 0;
            case 49 -> 1;
            case 50 -> 2;
            case 51 -> 3;
            case 52 -> 4;
            case 53 -> 5;
            case 54 -> 6;
            case 55 -> 7;
            case 56 -> 8;
            case 57 -> 9;
            default -> throw new IllegalArgumentException("Invalid entry at line " + lineNumber + ": expected hex digit, got " + (char)data);
            case 65 -> 10;
            case 66 -> 11;
            case 67 -> 12;
            case 68 -> 13;
            case 69 -> 14;
            case 70 -> 15;
        };
    }

    private static boolean copyUntil(InputStream stream, ByteList byteList, int value) throws IOException {
        while (true) {
            int i = stream.read();
            if (i == -1) {
                return false;
            }

            if (i == value) {
                return true;
            }

            byteList.add((byte)i);
        }
    }

    record ByteContents(byte[] contents) implements UnihexProvider.LineData {
        @Override
        public int line(int index) {
            return this.contents[index] << 24;
        }

        static UnihexProvider.LineData read(int index, ByteList byteList) {
            byte[] bs = new byte[16];
            int i = 0;

            for (int j = 0; j < 16; j++) {
                int k = UnihexProvider.decodeHex(index, byteList, i++);
                int l = UnihexProvider.decodeHex(index, byteList, i++);
                byte b = (byte)(k << 4 | l);
                bs[j] = b;
            }

            return new UnihexProvider.ByteContents(bs);
        }

        @Override
        public int bitWidth() {
            return 8;
        }
    }

    public static class Definition implements GlyphProviderDefinition {
        public static final MapCodec<UnihexProvider.Definition> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    ResourceLocation.CODEC.fieldOf("hex_file").forGetter(definition -> definition.hexFile),
                    UnihexProvider.OverrideRange.CODEC.listOf().fieldOf("size_overrides").forGetter(definition -> definition.sizeOverrides)
                )
                .apply(instance, UnihexProvider.Definition::new)
        );
        private final ResourceLocation hexFile;
        private final List<UnihexProvider.OverrideRange> sizeOverrides;

        private Definition(ResourceLocation hexFile, List<UnihexProvider.OverrideRange> sizeOverrides) {
            this.hexFile = hexFile;
            this.sizeOverrides = sizeOverrides;
        }

        @Override
        public GlyphProviderType type() {
            return GlyphProviderType.UNIHEX;
        }

        @Override
        public Either<GlyphProviderDefinition.Loader, GlyphProviderDefinition.Reference> unpack() {
            return Either.left(this::load);
        }

        private GlyphProvider load(ResourceManager resourceManager) throws IOException {
            try (InputStream inputStream = resourceManager.open(this.hexFile)) {
                return this.loadData(inputStream);
            }
        }

        private UnihexProvider loadData(InputStream inputStream) throws IOException {
            CodepointMap<UnihexProvider.LineData> codepointMap = new CodepointMap<>(UnihexProvider.LineData[]::new, UnihexProvider.LineData[][]::new);
            UnihexProvider.ReaderOutput readerOutput = codepointMap::put;

            try (ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
                ZipEntry zipEntry;
                while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                    String string = zipEntry.getName();
                    if (string.endsWith(".hex")) {
                        UnihexProvider.LOGGER.info("Found {}, loading", string);
                        UnihexProvider.readFromStream(new FastBufferedInputStream(zipInputStream), readerOutput);
                    }
                }

                CodepointMap<UnihexProvider.Glyph> codepointMap2 = new CodepointMap<>(UnihexProvider.Glyph[]::new, UnihexProvider.Glyph[][]::new);

                for (UnihexProvider.OverrideRange overrideRange : this.sizeOverrides) {
                    int i = overrideRange.from;
                    int j = overrideRange.to;
                    UnihexProvider.Dimensions dimensions = overrideRange.dimensions;

                    for (int k = i; k <= j; k++) {
                        UnihexProvider.LineData lineData = codepointMap.remove(k);
                        if (lineData != null) {
                            codepointMap2.put(k, new UnihexProvider.Glyph(lineData, dimensions.left, dimensions.right));
                        }
                    }
                }

                codepointMap.forEach((ix, lineDatax) -> {
                    int jx = lineDatax.calculateWidth();
                    int kx = UnihexProvider.Dimensions.left(jx);
                    int l = UnihexProvider.Dimensions.right(jx);
                    codepointMap2.put(ix, new UnihexProvider.Glyph(lineDatax, kx, l));
                });
                return new UnihexProvider(codepointMap2);
            }
        }
    }

    public record Dimensions(int left, int right) {
        public static final MapCodec<UnihexProvider.Dimensions> MAP_CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    Codec.INT.fieldOf("left").forGetter(UnihexProvider.Dimensions::left),
                    Codec.INT.fieldOf("right").forGetter(UnihexProvider.Dimensions::right)
                )
                .apply(instance, UnihexProvider.Dimensions::new)
        );
        public static final Codec<UnihexProvider.Dimensions> CODEC = MAP_CODEC.codec();

        public int pack() {
            return pack(this.left, this.right);
        }

        public static int pack(int left, int right) {
            return (left & 0xFF) << 8 | right & 0xFF;
        }

        public static int left(int packedDimensions) {
            return (byte)(packedDimensions >> 8);
        }

        public static int right(int packedDimensions) {
            return (byte)packedDimensions;
        }
    }

    record Glyph(UnihexProvider.LineData contents, int left, int right) implements GlyphInfo {
        public int width() {
            return this.right - this.left + 1;
        }

        @Override
        public float getAdvance() {
            return this.width() / 2 + 1;
        }

        @Override
        public float getShadowOffset() {
            return 0.5F;
        }

        @Override
        public float getBoldOffset() {
            return 0.5F;
        }

        @Override
        public BakedGlyph bake(Function<SheetGlyphInfo, BakedGlyph> function) {
            return function.apply(new SheetGlyphInfo() {
                @Override
                public float getOversample() {
                    return 2.0F;
                }

                @Override
                public int getPixelWidth() {
                    return Glyph.this.width();
                }

                @Override
                public int getPixelHeight() {
                    return 16;
                }

                @Override
                public void upload(int i, int j) {
                    IntBuffer intBuffer = MemoryUtil.memAllocInt(Glyph.this.width() * 16);
                    UnihexProvider.unpackBitsToBytes(intBuffer, Glyph.this.contents, Glyph.this.left, Glyph.this.right);
                    intBuffer.rewind();
                    GlStateManager.upload(0, i, j, Glyph.this.width(), 16, NativeImage.Format.RGBA, intBuffer, MemoryUtil::memFree);
                }

                @Override
                public boolean isColored() {
                    return true;
                }
            });
        }
    }

    record IntContents(int[] contents, int bitWidth) implements UnihexProvider.LineData {
        private static final int SIZE_24 = 24;

        @Override
        public int line(int index) {
            return this.contents[index];
        }

        static UnihexProvider.LineData read24(int index, ByteList byteList) {
            int[] is = new int[16];
            int i = 0;
            int j = 0;

            for (int k = 0; k < 16; k++) {
                int l = UnihexProvider.decodeHex(index, byteList, j++);
                int m = UnihexProvider.decodeHex(index, byteList, j++);
                int n = UnihexProvider.decodeHex(index, byteList, j++);
                int o = UnihexProvider.decodeHex(index, byteList, j++);
                int p = UnihexProvider.decodeHex(index, byteList, j++);
                int q = UnihexProvider.decodeHex(index, byteList, j++);
                int r = l << 20 | m << 16 | n << 12 | o << 8 | p << 4 | q;
                is[k] = r << 8;
                i |= r;
            }

            return new UnihexProvider.IntContents(is, 24);
        }

        public static UnihexProvider.LineData read32(int index, ByteList byteList) {
            int[] is = new int[16];
            int i = 0;
            int j = 0;

            for (int k = 0; k < 16; k++) {
                int l = UnihexProvider.decodeHex(index, byteList, j++);
                int m = UnihexProvider.decodeHex(index, byteList, j++);
                int n = UnihexProvider.decodeHex(index, byteList, j++);
                int o = UnihexProvider.decodeHex(index, byteList, j++);
                int p = UnihexProvider.decodeHex(index, byteList, j++);
                int q = UnihexProvider.decodeHex(index, byteList, j++);
                int r = UnihexProvider.decodeHex(index, byteList, j++);
                int s = UnihexProvider.decodeHex(index, byteList, j++);
                int t = l << 28 | m << 24 | n << 20 | o << 16 | p << 12 | q << 8 | r << 4 | s;
                is[k] = t;
                i |= t;
            }

            return new UnihexProvider.IntContents(is, 32);
        }
    }

    public interface LineData {
        int line(int var1);

        int bitWidth();

        default int mask() {
            int i = 0;

            for (int j = 0; j < 16; j++) {
                i |= this.line(j);
            }

            return i;
        }

        default int calculateWidth() {
            int i = this.mask();
            int j = this.bitWidth();
            int k;
            int l;
            if (i == 0) {
                k = 0;
                l = j;
            } else {
                k = Integer.numberOfLeadingZeros(i);
                l = 32 - Integer.numberOfTrailingZeros(i) - 1;
            }

            return UnihexProvider.Dimensions.pack(k, l);
        }
    }

    record OverrideRange(int from, int to, UnihexProvider.Dimensions dimensions) {
        private static final Codec<UnihexProvider.OverrideRange> RAW_CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    ExtraCodecs.CODEPOINT.fieldOf("from").forGetter(UnihexProvider.OverrideRange::from),
                    ExtraCodecs.CODEPOINT.fieldOf("to").forGetter(UnihexProvider.OverrideRange::to),
                    UnihexProvider.Dimensions.MAP_CODEC.forGetter(UnihexProvider.OverrideRange::dimensions)
                )
                .apply(instance, UnihexProvider.OverrideRange::new)
        );
        public static final Codec<UnihexProvider.OverrideRange> CODEC = RAW_CODEC.validate(
            overrideRange -> overrideRange.from >= overrideRange.to
                ? DataResult.error(() -> "Invalid range: [" + overrideRange.from + ";" + overrideRange.to + "]")
                : DataResult.success(overrideRange)
        );
    }

    @FunctionalInterface
    public interface ReaderOutput {
        void accept(int var1, UnihexProvider.LineData var2);
    }

    record ShortContents(short[] contents) implements UnihexProvider.LineData {
        @Override
        public int line(int index) {
            return this.contents[index] << 16;
        }

        static UnihexProvider.LineData read(int index, ByteList byteList) {
            short[] ss = new short[16];
            int i = 0;

            for (int j = 0; j < 16; j++) {
                int k = UnihexProvider.decodeHex(index, byteList, i++);
                int l = UnihexProvider.decodeHex(index, byteList, i++);
                int m = UnihexProvider.decodeHex(index, byteList, i++);
                int n = UnihexProvider.decodeHex(index, byteList, i++);
                short s = (short)(k << 12 | l << 8 | m << 4 | n);
                ss[j] = s;
            }

            return new UnihexProvider.ShortContents(ss);
        }

        @Override
        public int bitWidth() {
            return 16;
        }
    }
}
