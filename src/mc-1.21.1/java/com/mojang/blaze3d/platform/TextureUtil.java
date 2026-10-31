package com.mojang.blaze3d.platform;

import com.mojang.blaze3d.DontObfuscate;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Path;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntUnaryOperator;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;

@DontObfuscate
public class TextureUtil {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final int MIN_MIPMAP_LEVEL = 0;
    private static final int DEFAULT_IMAGE_BUFFER_SIZE = 8192;

    public static int generateTextureId() {
        RenderSystem.assertOnRenderThreadOrInit();
        if (SharedConstants.IS_RUNNING_IN_IDE) {
            int[] is = new int[ThreadLocalRandom.current().nextInt(15) + 1];
            GlStateManager._genTextures(is);
            int i = GlStateManager._genTexture();
            GlStateManager._deleteTextures(is);
            return i;
        } else {
            return GlStateManager._genTexture();
        }
    }

    public static void releaseTextureId(int textureId) {
        RenderSystem.assertOnRenderThreadOrInit();
        GlStateManager._deleteTexture(textureId);
    }

    public static void prepareImage(int textureId, int width, int height) {
        prepareImage(NativeImage.InternalGlFormat.RGBA, textureId, 0, width, height);
    }

    public static void prepareImage(NativeImage.InternalGlFormat pixelFormat, int textureId, int width, int height) {
        prepareImage(pixelFormat, textureId, 0, width, height);
    }

    public static void prepareImage(int textureId, int mipmapLevel, int width, int height) {
        prepareImage(NativeImage.InternalGlFormat.RGBA, textureId, mipmapLevel, width, height);
    }

    public static void prepareImage(NativeImage.InternalGlFormat pixelFormat, int textureId, int mipmapLevel, int width, int height) {
        RenderSystem.assertOnRenderThreadOrInit();
        bind(textureId);
        if (mipmapLevel >= 0) {
            GlStateManager._texParameter(3553, 33085, mipmapLevel);
            GlStateManager._texParameter(3553, 33082, 0);
            GlStateManager._texParameter(3553, 33083, mipmapLevel);
            GlStateManager._texParameter(3553, 34049, 0.0F);
        }

        for (int i = 0; i <= mipmapLevel; i++) {
            GlStateManager._texImage2D(3553, i, pixelFormat.glFormat(), width >> i, height >> i, 0, 6408, 5121, null);
        }
    }

    private static void bind(int textureId) {
        RenderSystem.assertOnRenderThreadOrInit();
        GlStateManager._bindTexture(textureId);
    }

    public static ByteBuffer readResource(InputStream inputStream) throws IOException {
        ReadableByteChannel readableByteChannel = Channels.newChannel(inputStream);
        return readableByteChannel instanceof SeekableByteChannel seekableByteChannel
            ? readResource(readableByteChannel, (int)seekableByteChannel.size() + 1)
            : readResource(readableByteChannel, 8192);
    }

    private static ByteBuffer readResource(ReadableByteChannel channel, int size) throws IOException {
        ByteBuffer byteBuffer = MemoryUtil.memAlloc(size);

        try {
            while (channel.read(byteBuffer) != -1) {
                if (!byteBuffer.hasRemaining()) {
                    byteBuffer = MemoryUtil.memRealloc(byteBuffer, byteBuffer.capacity() * 2);
                }
            }

            return byteBuffer;
        } catch (IOException iOException) {
            MemoryUtil.memFree(byteBuffer);
            throw iOException;
        }
    }

    public static void writeAsPNG(Path outputDir, String textureName, int textureId, int amount, int width, int height) {
        writeAsPNG(outputDir, textureName, textureId, amount, width, height, null);
    }

    public static void writeAsPNG(Path outputDir, String textureName, int textureId, int amount, int width, int height, @Nullable IntUnaryOperator function) {
        RenderSystem.assertOnRenderThread();
        bind(textureId);

        for (int i = 0; i <= amount; i++) {
            int j = width >> i;
            int k = height >> i;

            try (NativeImage nativeImage = new NativeImage(j, k, false)) {
                nativeImage.downloadTexture(i, false);
                if (function != null) {
                    nativeImage.applyToAllPixels(function);
                }

                Path path = outputDir.resolve(textureName + "_" + i + ".png");
                nativeImage.writeToFile(path);
                LOGGER.debug("Exported png to: {}", path.toAbsolutePath());
            } catch (IOException iOException) {
                LOGGER.debug("Unable to write: ", iOException);
            }
        }
    }

    public static Path getDebugTexturePath(Path basePath) {
        return basePath.resolve("screenshots").resolve("debug");
    }

    public static Path getDebugTexturePath() {
        return getDebugTexturePath(Path.of("."));
    }
}
