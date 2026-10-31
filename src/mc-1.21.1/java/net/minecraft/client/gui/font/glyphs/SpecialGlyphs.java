package net.minecraft.client.gui.font.glyphs;

import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.font.SheetGlyphInfo;
import com.mojang.blaze3d.platform.NativeImage;
import java.util.function.Function;
import java.util.function.Supplier;

public enum SpecialGlyphs implements GlyphInfo {
    WHITE(() -> generate(5, 8, (i, j) -> -1)),
    MISSING(() -> {
        int i = 5;
        int j = 8;
        return generate(5, 8, (ix, jx) -> {
            boolean bl = ix == 0 || ix + 1 == 5 || jx == 0 || jx + 1 == 8;
            return bl ? -1 : 0;
        });
    });

    final NativeImage image;

    private static NativeImage generate(int width, int height, SpecialGlyphs.PixelProvider pixelProvider) {
        NativeImage nativeImage = new NativeImage(NativeImage.Format.RGBA, width, height, false);

        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                nativeImage.setPixelRGBA(j, i, pixelProvider.getColor(j, i));
            }
        }

        nativeImage.untrack();
        return nativeImage;
    }

    SpecialGlyphs(final Supplier<NativeImage> image) {
        this.image = image.get();
    }

    @Override
    public float getAdvance() {
        return this.image.getWidth() + 1;
    }

    @Override
    public BakedGlyph bake(Function<SheetGlyphInfo, BakedGlyph> glyphProvider) {
        return glyphProvider.apply(new SheetGlyphInfo() {
            @Override
            public int getPixelWidth() {
                return SpecialGlyphs.this.image.getWidth();
            }

            @Override
            public int getPixelHeight() {
                return SpecialGlyphs.this.image.getHeight();
            }

            @Override
            public float getOversample() {
                return 1.0F;
            }

            @Override
            public void upload(int i, int j) {
                SpecialGlyphs.this.image.upload(0, i, j, false);
            }

            @Override
            public boolean isColored() {
                return true;
            }
        });
    }

    @FunctionalInterface
    interface PixelProvider {
        int getColor(int var1, int var2);
    }
}
