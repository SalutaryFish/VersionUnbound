package com.mojang.blaze3d.platform;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntUnaryOperator;
import javax.annotation.Nullable;
import net.minecraft.client.gui.font.providers.FreeTypeUtil;
import net.minecraft.util.FastColor;
import net.minecraft.util.PngInfo;
import org.apache.commons.io.IOUtils;
import org.lwjgl.stb.STBIWriteCallback;
import org.lwjgl.stb.STBImage;
import org.lwjgl.stb.STBImageResize;
import org.lwjgl.stb.STBImageWrite;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.freetype.FT_Bitmap;
import org.lwjgl.util.freetype.FT_Face;
import org.lwjgl.util.freetype.FT_GlyphSlot;
import org.lwjgl.util.freetype.FreeType;
import org.slf4j.Logger;

public final class NativeImage implements AutoCloseable {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<StandardOpenOption> OPEN_OPTIONS = EnumSet.of(
        StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING
    );
    private final NativeImage.Format format;
    private final int width;
    private final int height;
    private final boolean useStbFree;
    private long pixels;
    private final long size;

    public NativeImage(int width, int height, boolean useCalloc) {
        this(NativeImage.Format.RGBA, width, height, useCalloc);
    }

    public NativeImage(NativeImage.Format format, int width, int height, boolean useCalloc) {
        if (width > 0 && height > 0) {
            this.format = format;
            this.width = width;
            this.height = height;
            this.size = (long)width * height * format.components();
            this.useStbFree = false;
            if (useCalloc) {
                this.pixels = MemoryUtil.nmemCalloc(1L, this.size);
            } else {
                this.pixels = MemoryUtil.nmemAlloc(this.size);
            }

            if (this.pixels == 0L) {
                throw new IllegalStateException("Unable to allocate texture of size " + width + "x" + height + " (" + format.components() + " channels)");
            }
        } else {
            throw new IllegalArgumentException("Invalid texture size: " + width + "x" + height);
        }
    }

    private NativeImage(NativeImage.Format format, int width, int height, boolean useStbFree, long pixels) {
        if (width > 0 && height > 0) {
            this.format = format;
            this.width = width;
            this.height = height;
            this.useStbFree = useStbFree;
            this.pixels = pixels;
            this.size = (long)width * height * format.components();
        } else {
            throw new IllegalArgumentException("Invalid texture size: " + width + "x" + height);
        }
    }

    @Override
    public String toString() {
        return "NativeImage[" + this.format + " " + this.width + "x" + this.height + "@" + this.pixels + (this.useStbFree ? "S" : "N") + "]";
    }

    private boolean isOutsideBounds(int x, int y) {
        return x < 0 || x >= this.width || y < 0 || y >= this.height;
    }

    public static NativeImage read(InputStream textureStream) throws IOException {
        return read(NativeImage.Format.RGBA, textureStream);
    }

    public static NativeImage read(@Nullable NativeImage.Format format, InputStream textureStream) throws IOException {
        ByteBuffer byteBuffer = null;

        try {
            byteBuffer = TextureUtil.readResource(textureStream);
            byteBuffer.rewind();
            return read(format, byteBuffer);
        } finally {
            MemoryUtil.memFree(byteBuffer);
            IOUtils.closeQuietly(textureStream);
        }
    }

    public static NativeImage read(ByteBuffer textureData) throws IOException {
        return read(NativeImage.Format.RGBA, textureData);
    }

    public static NativeImage read(byte[] bytes) throws IOException {
        try (MemoryStack memoryStack = MemoryStack.stackPush()) {
            ByteBuffer byteBuffer = memoryStack.malloc(bytes.length);
            byteBuffer.put(bytes);
            byteBuffer.rewind();
            return read(byteBuffer);
        }
    }

    public static NativeImage read(@Nullable NativeImage.Format format, ByteBuffer textureData) throws IOException {
        if (format != null && !format.supportedByStb()) {
            throw new UnsupportedOperationException("Don't know how to read format " + format);
        }

        if (MemoryUtil.memAddress(textureData) == 0L) {
            throw new IllegalArgumentException("Invalid buffer");
        }

        PngInfo.validateHeader(textureData);

        try (MemoryStack memoryStack = MemoryStack.stackPush()) {
            IntBuffer intBuffer = memoryStack.mallocInt(1);
            IntBuffer intBuffer2 = memoryStack.mallocInt(1);
            IntBuffer intBuffer3 = memoryStack.mallocInt(1);
            ByteBuffer byteBuffer = STBImage.stbi_load_from_memory(textureData, intBuffer, intBuffer2, intBuffer3, format == null ? 0 : format.components);
            if (byteBuffer == null) {
                throw new IOException("Could not load image: " + STBImage.stbi_failure_reason());
            } else {
                return new NativeImage(
                    format == null ? NativeImage.Format.getStbFormat(intBuffer3.get(0)) : format,
                    intBuffer.get(0),
                    intBuffer2.get(0),
                    true,
                    MemoryUtil.memAddress(byteBuffer)
                );
            }
        }
    }

    private static void setFilter(boolean linear, boolean mipmap) {
        RenderSystem.assertOnRenderThreadOrInit();
        if (linear) {
            GlStateManager._texParameter(3553, 10241, mipmap ? 9987 : 9729);
            GlStateManager._texParameter(3553, 10240, 9729);
        } else {
            GlStateManager._texParameter(3553, 10241, mipmap ? 9986 : 9728);
            GlStateManager._texParameter(3553, 10240, 9728);
        }
    }

    private void checkAllocated() {
        if (this.pixels == 0L) {
            throw new IllegalStateException("Image is not allocated.");
        }
    }

    @Override
    public void close() {
        if (this.pixels != 0L) {
            if (this.useStbFree) {
                STBImage.nstbi_image_free(this.pixels);
            } else {
                MemoryUtil.nmemFree(this.pixels);
            }
        }

        this.pixels = 0L;
    }

    public int getWidth() {
        return this.width;
    }

    public int getHeight() {
        return this.height;
    }

    public NativeImage.Format format() {
        return this.format;
    }

    public int getPixelRGBA(int x, int y) {
        if (this.format != NativeImage.Format.RGBA) {
            throw new IllegalArgumentException(String.format(Locale.ROOT, "getPixelRGBA only works on RGBA images; have %s", this.format));
        }

        if (this.isOutsideBounds(x, y)) {
            throw new IllegalArgumentException(String.format(Locale.ROOT, "(%s, %s) outside of image bounds (%s, %s)", x, y, this.width, this.height));
        }

        this.checkAllocated();
        long l = (x + (long)y * this.width) * 4L;
        return MemoryUtil.memGetInt(this.pixels + l);
    }

    public void setPixelRGBA(int x, int y, int abgrColor) {
        if (this.format != NativeImage.Format.RGBA) {
            throw new IllegalArgumentException(String.format(Locale.ROOT, "setPixelRGBA only works on RGBA images; have %s", this.format));
        }

        if (this.isOutsideBounds(x, y)) {
            throw new IllegalArgumentException(String.format(Locale.ROOT, "(%s, %s) outside of image bounds (%s, %s)", x, y, this.width, this.height));
        }

        this.checkAllocated();
        long l = (x + (long)y * this.width) * 4L;
        MemoryUtil.memPutInt(this.pixels + l, abgrColor);
    }

    public NativeImage mappedCopy(IntUnaryOperator function) {
        if (this.format != NativeImage.Format.RGBA) {
            throw new IllegalArgumentException(String.format(Locale.ROOT, "function application only works on RGBA images; have %s", this.format));
        }

        this.checkAllocated();
        NativeImage nativeImage = new NativeImage(this.width, this.height, false);
        int i = this.width * this.height;
        IntBuffer intBuffer = MemoryUtil.memIntBuffer(this.pixels, i);
        IntBuffer intBuffer2 = MemoryUtil.memIntBuffer(nativeImage.pixels, i);

        for (int j = 0; j < i; j++) {
            intBuffer2.put(j, function.applyAsInt(intBuffer.get(j)));
        }

        return nativeImage;
    }

    public void applyToAllPixels(IntUnaryOperator function) {
        if (this.format != NativeImage.Format.RGBA) {
            throw new IllegalArgumentException(String.format(Locale.ROOT, "function application only works on RGBA images; have %s", this.format));
        }

        this.checkAllocated();
        int i = this.width * this.height;
        IntBuffer intBuffer = MemoryUtil.memIntBuffer(this.pixels, i);

        for (int j = 0; j < i; j++) {
            intBuffer.put(j, function.applyAsInt(intBuffer.get(j)));
        }
    }

    public int[] getPixelsRGBA() {
        if (this.format != NativeImage.Format.RGBA) {
            throw new IllegalArgumentException(String.format(Locale.ROOT, "getPixelsRGBA only works on RGBA images; have %s", this.format));
        }

        this.checkAllocated();
        int[] is = new int[this.width * this.height];
        MemoryUtil.memIntBuffer(this.pixels, this.width * this.height).get(is);
        return is;
    }

    public void setPixelLuminance(int x, int y, byte luminance) {
        RenderSystem.assertOnRenderThread();
        if (!this.format.hasLuminance()) {
            throw new IllegalArgumentException(String.format(Locale.ROOT, "setPixelLuminance only works on image with luminance; have %s", this.format));
        }

        if (this.isOutsideBounds(x, y)) {
            throw new IllegalArgumentException(String.format(Locale.ROOT, "(%s, %s) outside of image bounds (%s, %s)", x, y, this.width, this.height));
        }

        this.checkAllocated();
        long l = (x + (long)y * this.width) * this.format.components() + this.format.luminanceOffset() / 8;
        MemoryUtil.memPutByte(this.pixels + l, luminance);
    }

    public byte getRedOrLuminance(int x, int y) {
        RenderSystem.assertOnRenderThread();
        if (!this.format.hasLuminanceOrRed()) {
            throw new IllegalArgumentException(String.format(Locale.ROOT, "no red or luminance in %s", this.format));
        }

        if (this.isOutsideBounds(x, y)) {
            throw new IllegalArgumentException(String.format(Locale.ROOT, "(%s, %s) outside of image bounds (%s, %s)", x, y, this.width, this.height));
        }

        int i = (x + y * this.width) * this.format.components() + this.format.luminanceOrRedOffset() / 8;
        return MemoryUtil.memGetByte(this.pixels + i);
    }

    public byte getGreenOrLuminance(int x, int y) {
        RenderSystem.assertOnRenderThread();
        if (!this.format.hasLuminanceOrGreen()) {
            throw new IllegalArgumentException(String.format(Locale.ROOT, "no green or luminance in %s", this.format));
        }

        if (this.isOutsideBounds(x, y)) {
            throw new IllegalArgumentException(String.format(Locale.ROOT, "(%s, %s) outside of image bounds (%s, %s)", x, y, this.width, this.height));
        }

        int i = (x + y * this.width) * this.format.components() + this.format.luminanceOrGreenOffset() / 8;
        return MemoryUtil.memGetByte(this.pixels + i);
    }

    public byte getBlueOrLuminance(int x, int y) {
        RenderSystem.assertOnRenderThread();
        if (!this.format.hasLuminanceOrBlue()) {
            throw new IllegalArgumentException(String.format(Locale.ROOT, "no blue or luminance in %s", this.format));
        }

        if (this.isOutsideBounds(x, y)) {
            throw new IllegalArgumentException(String.format(Locale.ROOT, "(%s, %s) outside of image bounds (%s, %s)", x, y, this.width, this.height));
        }

        int i = (x + y * this.width) * this.format.components() + this.format.luminanceOrBlueOffset() / 8;
        return MemoryUtil.memGetByte(this.pixels + i);
    }

    public byte getLuminanceOrAlpha(int x, int y) {
        if (!this.format.hasLuminanceOrAlpha()) {
            throw new IllegalArgumentException(String.format(Locale.ROOT, "no luminance or alpha in %s", this.format));
        }

        if (this.isOutsideBounds(x, y)) {
            throw new IllegalArgumentException(String.format(Locale.ROOT, "(%s, %s) outside of image bounds (%s, %s)", x, y, this.width, this.height));
        }

        int i = (x + y * this.width) * this.format.components() + this.format.luminanceOrAlphaOffset() / 8;
        return MemoryUtil.memGetByte(this.pixels + i);
    }

    public void blendPixel(int x, int y, int abgrColor) {
        if (this.format != NativeImage.Format.RGBA) {
            throw new UnsupportedOperationException("Can only call blendPixel with RGBA format");
        }

        int i = this.getPixelRGBA(x, y);
        float f = FastColor.ABGR32.alpha(abgrColor) / 255.0F;
        float g = FastColor.ABGR32.blue(abgrColor) / 255.0F;
        float h = FastColor.ABGR32.green(abgrColor) / 255.0F;
        float j = FastColor.ABGR32.red(abgrColor) / 255.0F;
        float k = FastColor.ABGR32.alpha(i) / 255.0F;
        float l = FastColor.ABGR32.blue(i) / 255.0F;
        float m = FastColor.ABGR32.green(i) / 255.0F;
        float n = FastColor.ABGR32.red(i) / 255.0F;
        float o = f;
        float p = 1.0F - f;
        float q = f * o + k * p;
        float r = g * o + l * p;
        float s = h * o + m * p;
        float t = j * o + n * p;
        if (q > 1.0F) {
            q = 1.0F;
        }

        if (r > 1.0F) {
            r = 1.0F;
        }

        if (s > 1.0F) {
            s = 1.0F;
        }

        if (t > 1.0F) {
            t = 1.0F;
        }

        int u = (int)(q * 255.0F);
        int v = (int)(r * 255.0F);
        int w = (int)(s * 255.0F);
        int z = (int)(t * 255.0F);
        this.setPixelRGBA(x, y, FastColor.ABGR32.color(u, v, w, z));
    }

    @Deprecated
    public int[] makePixelArray() {
        if (this.format != NativeImage.Format.RGBA) {
            throw new UnsupportedOperationException("can only call makePixelArray for RGBA images.");
        }

        this.checkAllocated();
        int[] is = new int[this.getWidth() * this.getHeight()];

        for (int i = 0; i < this.getHeight(); i++) {
            for (int j = 0; j < this.getWidth(); j++) {
                int k = this.getPixelRGBA(j, i);
                is[j + i * this.getWidth()] = FastColor.ARGB32.color(
                    FastColor.ABGR32.alpha(k), FastColor.ABGR32.red(k), FastColor.ABGR32.green(k), FastColor.ABGR32.blue(k)
                );
            }
        }

        return is;
    }

    public void upload(int level, int xOffset, int yOffset, boolean mipmap) {
        this.upload(level, xOffset, yOffset, 0, 0, this.width, this.height, false, mipmap);
    }

    public void upload(int level, int xOffset, int yOffset, int unpackSkipPixels, int unpackSkipRows, int width, int height, boolean mipmap, boolean autoClose) {
        this.upload(level, xOffset, yOffset, unpackSkipPixels, unpackSkipRows, width, height, false, false, mipmap, autoClose);
    }

    public void upload(
        int level,
        int xOffset,
        int yOffset,
        int unpackSkipPixels,
        int unpackSkipRows,
        int width,
        int height,
        boolean blur,
        boolean clamp,
        boolean mipmap,
        boolean autoClose
    ) {
        if (!RenderSystem.isOnRenderThreadOrInit()) {
            RenderSystem.recordRenderCall(
                () -> this._upload(level, xOffset, yOffset, unpackSkipPixels, unpackSkipRows, width, height, blur, clamp, mipmap, autoClose)
            );
        } else {
            this._upload(level, xOffset, yOffset, unpackSkipPixels, unpackSkipRows, width, height, blur, clamp, mipmap, autoClose);
        }
    }

    private void _upload(
        int level,
        int xOffset,
        int yOffset,
        int unpackSkipPixels,
        int unpackSkipRows,
        int width,
        int height,
        boolean blur,
        boolean clamp,
        boolean mipmap,
        boolean autoClose
    ) {
        try {
            RenderSystem.assertOnRenderThreadOrInit();
            this.checkAllocated();
            setFilter(blur, mipmap);
            if (width == this.getWidth()) {
                GlStateManager._pixelStore(3314, 0);
            } else {
                GlStateManager._pixelStore(3314, this.getWidth());
            }

            GlStateManager._pixelStore(3316, unpackSkipPixels);
            GlStateManager._pixelStore(3315, unpackSkipRows);
            this.format.setUnpackPixelStoreState();
            GlStateManager._texSubImage2D(3553, level, xOffset, yOffset, width, height, this.format.glFormat(), 5121, this.pixels);
            if (clamp) {
                GlStateManager._texParameter(3553, 10242, 33071);
                GlStateManager._texParameter(3553, 10243, 33071);
            }
        } finally {
            if (autoClose) {
                this.close();
            }
        }
    }

    public void downloadTexture(int level, boolean opaque) {
        RenderSystem.assertOnRenderThread();
        this.checkAllocated();
        this.format.setPackPixelStoreState();
        GlStateManager._getTexImage(3553, level, this.format.glFormat(), 5121, this.pixels);
        if (opaque && this.format.hasAlpha()) {
            for (int i = 0; i < this.getHeight(); i++) {
                for (int j = 0; j < this.getWidth(); j++) {
                    this.setPixelRGBA(j, i, this.getPixelRGBA(j, i) | 255 << this.format.alphaOffset());
                }
            }
        }
    }

    public void downloadDepthBuffer(float unused) {
        RenderSystem.assertOnRenderThread();
        if (this.format.components() != 1) {
            throw new IllegalStateException("Depth buffer must be stored in NativeImage with 1 component.");
        }

        this.checkAllocated();
        this.format.setPackPixelStoreState();
        GlStateManager._readPixels(0, 0, this.width, this.height, 6402, 5121, this.pixels);
    }

    public void drawPixels() {
        RenderSystem.assertOnRenderThread();
        this.format.setUnpackPixelStoreState();
        GlStateManager._glDrawPixels(this.width, this.height, this.format.glFormat(), 5121, this.pixels);
    }

    public void writeToFile(File file) throws IOException {
        this.writeToFile(file.toPath());
    }

    public boolean copyFromFont(FT_Face face, int index) {
        if (this.format.components() != 1) {
            throw new IllegalArgumentException("Can only write fonts into 1-component images.");
        } else if (FreeTypeUtil.checkError(FreeType.FT_Load_Glyph(face, index, 4), "Loading glyph")) {
            return false;
        } else {
            FT_GlyphSlot fT_GlyphSlot = Objects.requireNonNull(face.glyph(), "Glyph not initialized");
            FT_Bitmap fT_Bitmap = fT_GlyphSlot.bitmap();
            if (fT_Bitmap.pixel_mode() != 2) {
                throw new IllegalStateException("Rendered glyph was not 8-bit grayscale");
            } else if (fT_Bitmap.width() == this.getWidth() && fT_Bitmap.rows() == this.getHeight()) {
                int i = fT_Bitmap.width() * fT_Bitmap.rows();
                ByteBuffer byteBuffer = Objects.requireNonNull(fT_Bitmap.buffer(i), "Glyph has no bitmap");
                MemoryUtil.memCopy(MemoryUtil.memAddress(byteBuffer), this.pixels, i);
                return true;
            } else {
                throw new IllegalArgumentException(
                    String.format(
                        Locale.ROOT,
                        "Glyph bitmap of size %sx%s does not match image of size: %sx%s",
                        fT_Bitmap.width(),
                        fT_Bitmap.rows(),
                        this.getWidth(),
                        this.getHeight()
                    )
                );
            }
        }
    }

    public void writeToFile(Path path) throws IOException {
        if (!this.format.supportedByStb()) {
            throw new UnsupportedOperationException("Don't know how to write format " + this.format);
        }

        this.checkAllocated();

        try (WritableByteChannel writableByteChannel = Files.newByteChannel(path, OPEN_OPTIONS)) {
            if (!this.writeToChannel(writableByteChannel)) {
                throw new IOException("Could not write image to the PNG file \"" + path.toAbsolutePath() + "\": " + STBImage.stbi_failure_reason());
            }
        }
    }

    public byte[] asByteArray() throws IOException {
        try (
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            WritableByteChannel writableByteChannel = Channels.newChannel(byteArrayOutputStream);
        ) {
            if (!this.writeToChannel(writableByteChannel)) {
                throw new IOException("Could not write image to byte array: " + STBImage.stbi_failure_reason());
            } else {
                return byteArrayOutputStream.toByteArray();
            }
        }
    }

    private boolean writeToChannel(WritableByteChannel channel) throws IOException {
        NativeImage.WriteCallback writeCallback = new NativeImage.WriteCallback(channel);

        try {
            int i = Math.min(this.getHeight(), Integer.MAX_VALUE / this.getWidth() / this.format.components());
            if (i < this.getHeight()) {
                LOGGER.warn("Dropping image height from {} to {} to fit the size into 32-bit signed int", this.getHeight(), i);
            }

            if (STBImageWrite.nstbi_write_png_to_func(writeCallback.address(), 0L, this.getWidth(), i, this.format.components(), this.pixels, 0) == 0) {
                return false;
            }

            writeCallback.throwIfException();
            return true;
        } finally {
            writeCallback.free();
        }
    }

    public void copyFrom(NativeImage other) {
        if (other.format() != this.format) {
            throw new UnsupportedOperationException("Image formats don't match.");
        }

        int i = this.format.components();
        this.checkAllocated();
        other.checkAllocated();
        if (this.width == other.width) {
            MemoryUtil.memCopy(other.pixels, this.pixels, Math.min(this.size, other.size));
        } else {
            int j = Math.min(this.getWidth(), other.getWidth());
            int k = Math.min(this.getHeight(), other.getHeight());

            for (int l = 0; l < k; l++) {
                int m = l * other.getWidth() * i;
                int n = l * this.getWidth() * i;
                MemoryUtil.memCopy(other.pixels + m, this.pixels + n, j);
            }
        }
    }

    public void fillRect(int x, int y, int width, int height, int value) {
        for (int i = y; i < y + height; i++) {
            for (int j = x; j < x + width; j++) {
                this.setPixelRGBA(j, i, value);
            }
        }
    }

    public void copyRect(int xFrom, int yFrom, int xToDelta, int yToDelta, int width, int height, boolean mirrorX, boolean mirrorY) {
        this.copyRect(this, xFrom, yFrom, xFrom + xToDelta, yFrom + yToDelta, width, height, mirrorX, mirrorY);
    }

    public void copyRect(NativeImage source, int xFrom, int yFrom, int xTo, int yTo, int width, int height, boolean mirrorX, boolean mirrorY) {
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                int k = mirrorX ? width - 1 - j : j;
                int l = mirrorY ? height - 1 - i : i;
                int m = this.getPixelRGBA(xFrom + j, yFrom + i);
                source.setPixelRGBA(xTo + k, yTo + l, m);
            }
        }
    }

    public void flipY() {
        this.checkAllocated();
        int i = this.format.components();
        int j = this.getWidth() * i;
        long l = MemoryUtil.nmemAlloc(j);

        try {
            for (int k = 0; k < this.getHeight() / 2; k++) {
                int m = k * this.getWidth() * i;
                int n = (this.getHeight() - 1 - k) * this.getWidth() * i;
                MemoryUtil.memCopy(this.pixels + m, l, j);
                MemoryUtil.memCopy(this.pixels + n, this.pixels + m, j);
                MemoryUtil.memCopy(l, this.pixels + n, j);
            }
        } finally {
            MemoryUtil.nmemFree(l);
        }
    }

    public void resizeSubRectTo(int x, int y, int width, int height, NativeImage image) {
        this.checkAllocated();
        if (image.format() != this.format) {
            throw new UnsupportedOperationException("resizeSubRectTo only works for images of the same format.");
        }

        int i = this.format.components();
        STBImageResize.nstbir_resize_uint8(
            this.pixels + (x + y * this.getWidth()) * i, width, height, this.getWidth() * i, image.pixels, image.getWidth(), image.getHeight(), 0, i
        );
    }

    public void untrack() {
        DebugMemoryUntracker.untrack(this.pixels);
    }

    public enum Format {
        RGBA(4, 6408, true, true, true, false, true, 0, 8, 16, 255, 24, true),
        RGB(3, 6407, true, true, true, false, false, 0, 8, 16, 255, 255, true),
        LUMINANCE_ALPHA(2, 33319, false, false, false, true, true, 255, 255, 255, 0, 8, true),
        LUMINANCE(1, 6403, false, false, false, true, false, 0, 0, 0, 0, 255, true);

        final int components;
        private final int glFormat;
        private final boolean hasRed;
        private final boolean hasGreen;
        private final boolean hasBlue;
        private final boolean hasLuminance;
        private final boolean hasAlpha;
        private final int redOffset;
        private final int greenOffset;
        private final int blueOffset;
        private final int luminanceOffset;
        private final int alphaOffset;
        private final boolean supportedByStb;

        Format(
            final int components,
            final int glFormat,
            final boolean hasRed,
            final boolean hasGreen,
            final boolean hasBlue,
            final boolean hasLuminance,
            final boolean hasAlpha,
            final int redOffset,
            final int greenOffset,
            final int blueOffset,
            final int luminanceOffset,
            final int alphaOffset,
            final boolean supportedByStb
        ) {
            this.components = components;
            this.glFormat = glFormat;
            this.hasRed = hasRed;
            this.hasGreen = hasGreen;
            this.hasBlue = hasBlue;
            this.hasLuminance = hasLuminance;
            this.hasAlpha = hasAlpha;
            this.redOffset = redOffset;
            this.greenOffset = greenOffset;
            this.blueOffset = blueOffset;
            this.luminanceOffset = luminanceOffset;
            this.alphaOffset = alphaOffset;
            this.supportedByStb = supportedByStb;
        }

        public int components() {
            return this.components;
        }

        public void setPackPixelStoreState() {
            RenderSystem.assertOnRenderThread();
            GlStateManager._pixelStore(3333, this.components());
        }

        public void setUnpackPixelStoreState() {
            RenderSystem.assertOnRenderThreadOrInit();
            GlStateManager._pixelStore(3317, this.components());
        }

        public int glFormat() {
            return this.glFormat;
        }

        public boolean hasRed() {
            return this.hasRed;
        }

        public boolean hasGreen() {
            return this.hasGreen;
        }

        public boolean hasBlue() {
            return this.hasBlue;
        }

        public boolean hasLuminance() {
            return this.hasLuminance;
        }

        public boolean hasAlpha() {
            return this.hasAlpha;
        }

        public int redOffset() {
            return this.redOffset;
        }

        public int greenOffset() {
            return this.greenOffset;
        }

        public int blueOffset() {
            return this.blueOffset;
        }

        public int luminanceOffset() {
            return this.luminanceOffset;
        }

        public int alphaOffset() {
            return this.alphaOffset;
        }

        public boolean hasLuminanceOrRed() {
            return this.hasLuminance || this.hasRed;
        }

        public boolean hasLuminanceOrGreen() {
            return this.hasLuminance || this.hasGreen;
        }

        public boolean hasLuminanceOrBlue() {
            return this.hasLuminance || this.hasBlue;
        }

        public boolean hasLuminanceOrAlpha() {
            return this.hasLuminance || this.hasAlpha;
        }

        public int luminanceOrRedOffset() {
            return this.hasLuminance ? this.luminanceOffset : this.redOffset;
        }

        public int luminanceOrGreenOffset() {
            return this.hasLuminance ? this.luminanceOffset : this.greenOffset;
        }

        public int luminanceOrBlueOffset() {
            return this.hasLuminance ? this.luminanceOffset : this.blueOffset;
        }

        public int luminanceOrAlphaOffset() {
            return this.hasLuminance ? this.luminanceOffset : this.alphaOffset;
        }

        public boolean supportedByStb() {
            return this.supportedByStb;
        }

        static NativeImage.Format getStbFormat(int channels) {
            switch (channels) {
                case 1:
                    return LUMINANCE;
                case 2:
                    return LUMINANCE_ALPHA;
                case 3:
                    return RGB;
                case 4:
                default:
                    return RGBA;
            }
        }
    }

    public enum InternalGlFormat {
        RGBA(6408),
        RGB(6407),
        RG(33319),
        RED(6403);

        private final int glFormat;

        InternalGlFormat(final int glFormat) {
            this.glFormat = glFormat;
        }

        public int glFormat() {
            return this.glFormat;
        }
    }

    static class WriteCallback extends STBIWriteCallback {
        private final WritableByteChannel output;
        @Nullable
        private IOException exception;

        WriteCallback(WritableByteChannel output) {
            this.output = output;
        }

        @Override
        public void invoke(long context, long data, int size) {
            ByteBuffer byteBuffer = getData(data, size);

            try {
                this.output.write(byteBuffer);
            } catch (IOException iOException) {
                this.exception = iOException;
            }
        }

        public void throwIfException() throws IOException {
            if (this.exception != null) {
                throw this.exception;
            }
        }
    }
}
