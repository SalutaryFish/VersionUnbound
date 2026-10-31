package com.mojang.blaze3d.font;

import com.mojang.blaze3d.platform.NativeImage;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Objects;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.gui.font.providers.FreeTypeUtil;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.freetype.FT_Bitmap;
import org.lwjgl.util.freetype.FT_Face;
import org.lwjgl.util.freetype.FT_GlyphSlot;
import org.lwjgl.util.freetype.FT_Vector;
import org.lwjgl.util.freetype.FreeType;

public class TrueTypeGlyphProvider implements GlyphProvider {
    @Nullable
    private ByteBuffer fontMemory;
    @Nullable
    private FT_Face face;
    final float oversample;
    private final IntSet skip = new IntArraySet();

    public TrueTypeGlyphProvider(ByteBuffer fontMemory, FT_Face face, float size, float oversample, float shiftX, float shiftY, String skip) {
        this.fontMemory = fontMemory;
        this.face = face;
        this.oversample = oversample;
        skip.codePoints().forEach(this.skip::add);
        int i = Math.round(size * oversample);
        FreeType.FT_Set_Pixel_Sizes(face, i, i);
        float f = shiftX * oversample;
        float g = -shiftY * oversample;

        try (MemoryStack memoryStack = MemoryStack.stackPush()) {
            FT_Vector fT_Vector = FreeTypeUtil.setVector(FT_Vector.malloc(memoryStack), f, g);
            FreeType.FT_Set_Transform(face, null, fT_Vector);
        }
    }

    @Nullable
    @Override
    public GlyphInfo getGlyph(int character) {
        FT_Face fT_Face = this.validateFontOpen();
        if (this.skip.contains(character)) {
            return null;
        }

        int i = FreeType.FT_Get_Char_Index(fT_Face, character);
        if (i == 0) {
            return null;
        }

        FreeTypeUtil.assertError(FreeType.FT_Load_Glyph(fT_Face, i, 4194312), "Loading glyph");
        FT_GlyphSlot fT_GlyphSlot = Objects.requireNonNull(fT_Face.glyph(), "Glyph not initialized");
        float f = FreeTypeUtil.x(fT_GlyphSlot.advance());
        FT_Bitmap fT_Bitmap = fT_GlyphSlot.bitmap();
        int j = fT_GlyphSlot.bitmap_left();
        int k = fT_GlyphSlot.bitmap_top();
        int l = fT_Bitmap.width();
        int m = fT_Bitmap.rows();
        return l > 0 && m > 0 ? new TrueTypeGlyphProvider.Glyph(j, k, l, m, f, i) : () -> f / this.oversample;
    }

    FT_Face validateFontOpen() {
        if (this.fontMemory != null && this.face != null) {
            return this.face;
        } else {
            throw new IllegalStateException("Provider already closed");
        }
    }

    @Override
    public void close() {
        if (this.face != null) {
            synchronized (FreeTypeUtil.LIBRARY_LOCK) {
                FreeTypeUtil.checkError(FreeType.FT_Done_Face(this.face), "Deleting face");
            }

            this.face = null;
        }

        MemoryUtil.memFree(this.fontMemory);
        this.fontMemory = null;
    }

    @Override
    public IntSet getSupportedGlyphs() {
        FT_Face fT_Face = this.validateFontOpen();
        IntSet intSet = new IntOpenHashSet();

        try (MemoryStack memoryStack = MemoryStack.stackPush()) {
            IntBuffer intBuffer = memoryStack.mallocInt(1);

            for (long l = FreeType.FT_Get_First_Char(fT_Face, intBuffer); intBuffer.get(0) != 0; l = FreeType.FT_Get_Next_Char(fT_Face, l, intBuffer)) {
                intSet.add((int)l);
            }
        }

        intSet.removeAll(this.skip);
        return intSet;
    }

    class Glyph implements GlyphInfo {
        final int width;
        final int height;
        final float bearingX;
        final float bearingY;
        private final float advance;
        final int index;

        Glyph(final float bearingX, final float bearingY, final int width, final int height, final float advance, final int index) {
            this.width = width;
            this.height = height;
            this.advance = advance / TrueTypeGlyphProvider.this.oversample;
            this.bearingX = bearingX / TrueTypeGlyphProvider.this.oversample;
            this.bearingY = bearingY / TrueTypeGlyphProvider.this.oversample;
            this.index = index;
        }

        @Override
        public float getAdvance() {
            return this.advance;
        }

        @Override
        public BakedGlyph bake(Function<SheetGlyphInfo, BakedGlyph> glyphProvider) {
            return glyphProvider.apply(new SheetGlyphInfo() {
                @Override
                public int getPixelWidth() {
                    return Glyph.this.width;
                }

                @Override
                public int getPixelHeight() {
                    return Glyph.this.height;
                }

                @Override
                public float getOversample() {
                    return TrueTypeGlyphProvider.this.oversample;
                }

                @Override
                public float getBearingLeft() {
                    return Glyph.this.bearingX;
                }

                @Override
                public float getBearingTop() {
                    return Glyph.this.bearingY;
                }

                @Override
                public void upload(int i, int j) {
                    FT_Face fT_Face = TrueTypeGlyphProvider.this.validateFontOpen();
                    NativeImage nativeImage = new NativeImage(NativeImage.Format.LUMINANCE, Glyph.this.width, Glyph.this.height, false);
                    if (nativeImage.copyFromFont(fT_Face, Glyph.this.index)) {
                        nativeImage.upload(0, i, j, 0, 0, Glyph.this.width, Glyph.this.height, false, true);
                    } else {
                        nativeImage.close();
                    }
                }

                @Override
                public boolean isColored() {
                    return false;
                }
            });
        }
    }
}
