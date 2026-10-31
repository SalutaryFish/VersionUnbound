package net.minecraft.client.gui.font.providers;

import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.blaze3d.font.SheetGlyphInfo;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.datafixers.util.Either;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.client.gui.font.CodepointMap;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;

public class BitmapProvider implements GlyphProvider {
    static final Logger LOGGER = LogUtils.getLogger();
    private final NativeImage image;
    private final CodepointMap<BitmapProvider.Glyph> glyphs;

    BitmapProvider(NativeImage image, CodepointMap<BitmapProvider.Glyph> glyphs) {
        this.image = image;
        this.glyphs = glyphs;
    }

    @Override
    public void close() {
        this.image.close();
    }

    @Nullable
    @Override
    public GlyphInfo getGlyph(int character) {
        return this.glyphs.get(character);
    }

    @Override
    public IntSet getSupportedGlyphs() {
        return IntSets.unmodifiable(this.glyphs.keySet());
    }

    public record Definition(ResourceLocation file, int height, int ascent, int[][] codepointGrid) implements GlyphProviderDefinition {
        private static final Codec<int[][]> CODEPOINT_GRID_CODEC = Codec.STRING.listOf().xmap(list -> {
            int i = list.size();
            int[][] is = new int[i][];

            for (int j = 0; j < i; j++) {
                is[j] = list.get(j).codePoints().toArray();
            }

            return is;
        }, is -> {
            List<String> list = new ArrayList<>(is.length);

            for (int[] js : is) {
                list.add(new String(js, 0, js.length));
            }

            return list;
        }).validate(BitmapProvider.Definition::validateDimensions);
        public static final MapCodec<BitmapProvider.Definition> CODEC = RecordCodecBuilder.<BitmapProvider.Definition>mapCodec(
                instance -> instance.group(
                        ResourceLocation.CODEC.fieldOf("file").forGetter(BitmapProvider.Definition::file),
                        Codec.INT.optionalFieldOf("height", 8).forGetter(BitmapProvider.Definition::height),
                        Codec.INT.fieldOf("ascent").forGetter(BitmapProvider.Definition::ascent),
                        CODEPOINT_GRID_CODEC.fieldOf("chars").forGetter(BitmapProvider.Definition::codepointGrid)
                    )
                    .apply(instance, BitmapProvider.Definition::new)
            )
            .validate(BitmapProvider.Definition::validate);

        private static DataResult<int[][]> validateDimensions(int[][] dimensions) {
            int i = dimensions.length;
            if (i == 0) {
                return DataResult.error(() -> "Expected to find data in codepoint grid");
            }

            int[] is = dimensions[0];
            int j = is.length;
            if (j == 0) {
                return DataResult.error(() -> "Expected to find data in codepoint grid");
            }

            for (int k = 1; k < i; k++) {
                int[] js = dimensions[k];
                if (js.length != j) {
                    return DataResult.error(
                        () -> "Lines in codepoint grid have to be the same length (found: " + js.length + " codepoints, expected: " + j + "), pad with \\u0000"
                    );
                }
            }

            return DataResult.success(dimensions);
        }

        private static DataResult<BitmapProvider.Definition> validate(BitmapProvider.Definition definition) {
            return definition.ascent > definition.height
                ? DataResult.error(() -> "Ascent " + definition.ascent + " higher than height " + definition.height)
                : DataResult.success(definition);
        }

        @Override
        public GlyphProviderType type() {
            return GlyphProviderType.BITMAP;
        }

        @Override
        public Either<GlyphProviderDefinition.Loader, GlyphProviderDefinition.Reference> unpack() {
            return Either.left(this::load);
        }

        private GlyphProvider load(ResourceManager resoureManager) throws IOException {
            ResourceLocation resourceLocation = this.file.withPrefix("textures/");

            try (InputStream inputStream = resoureManager.open(resourceLocation)) {
                NativeImage nativeImage = NativeImage.read(NativeImage.Format.RGBA, inputStream);
                int i = nativeImage.getWidth();
                int j = nativeImage.getHeight();
                int k = i / this.codepointGrid[0].length;
                int l = j / this.codepointGrid.length;
                float f = (float)this.height / l;
                CodepointMap<BitmapProvider.Glyph> codepointMap = new CodepointMap<>(BitmapProvider.Glyph[]::new, BitmapProvider.Glyph[][]::new);

                for (int m = 0; m < this.codepointGrid.length; m++) {
                    int n = 0;

                    for (int o : this.codepointGrid[m]) {
                        int p = n++;
                        if (o != 0) {
                            int q = this.getActualGlyphWidth(nativeImage, k, l, p, m);
                            BitmapProvider.Glyph glyph = codepointMap.put(
                                o, new BitmapProvider.Glyph(f, nativeImage, p * k, m * l, k, l, (int)(0.5 + q * f) + 1, this.ascent)
                            );
                            if (glyph != null) {
                                BitmapProvider.LOGGER.warn("Codepoint '{}' declared multiple times in {}", Integer.toHexString(o), resourceLocation);
                            }
                        }
                    }
                }

                return new BitmapProvider(nativeImage, codepointMap);
            }
        }

        private int getActualGlyphWidth(NativeImage image, int width, int height, int x, int y) {
            int i;
            for (i = width - 1; i >= 0; i--) {
                int j = x * width + i;

                for (int k = 0; k < height; k++) {
                    int l = y * height + k;
                    if (image.getLuminanceOrAlpha(j, l) != 0) {
                        return i + 1;
                    }
                }
            }

            return i + 1;
        }
    }

    record Glyph(float scale, NativeImage image, int offsetX, int offsetY, int width, int height, int advance, int ascent) implements GlyphInfo {
        @Override
        public float getAdvance() {
            return this.advance;
        }

        @Override
        public BakedGlyph bake(Function<SheetGlyphInfo, BakedGlyph> glyphProvider) {
            return glyphProvider.apply(new SheetGlyphInfo() {
                @Override
                public float getOversample() {
                    return 1.0F / Glyph.this.scale;
                }

                @Override
                public int getPixelWidth() {
                    return Glyph.this.width;
                }

                @Override
                public int getPixelHeight() {
                    return Glyph.this.height;
                }

                @Override
                public float getBearingTop() {
                    return Glyph.this.ascent;
                }

                @Override
                public void upload(int i, int j) {
                    Glyph.this.image.upload(0, i, j, Glyph.this.offsetX, Glyph.this.offsetY, Glyph.this.width, Glyph.this.height, false, false);
                }

                @Override
                public boolean isColored() {
                    return Glyph.this.image.format().components() > 1;
                }
            });
        }
    }
}
