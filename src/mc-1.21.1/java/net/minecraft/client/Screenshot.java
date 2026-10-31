package net.minecraft.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;

public class Screenshot {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String SCREENSHOT_DIR = "screenshots";
    private int rowHeight;
    private final DataOutputStream outputStream;
    private final byte[] bytes;
    private final int width;
    private final int height;
    private File file;

    /**
     * Saves a screenshot in the game directory with a time-stamped filename.
     */
    public static void grab(File gameDirectory, RenderTarget buffer, Consumer<Component> messageConsumer) {
        grab(gameDirectory, null, buffer, messageConsumer);
    }

    /**
     * Saves a screenshot in the game directory with the given file name (or null to generate a time-stamped name).
     */
    public static void grab(File gameDirectory, @Nullable String screenshotName, RenderTarget buffer, Consumer<Component> messageConsumer) {
        if (!RenderSystem.isOnRenderThread()) {
            RenderSystem.recordRenderCall(() -> _grab(gameDirectory, screenshotName, buffer, messageConsumer));
        } else {
            _grab(gameDirectory, screenshotName, buffer, messageConsumer);
        }
    }

    private static void _grab(File gameDirectory, @Nullable String screenshotName, RenderTarget buffer, Consumer<Component> messageConsumer) {
        NativeImage nativeImage = takeScreenshot(buffer);
        File file = new File(gameDirectory, "screenshots");
        file.mkdir();
        File file2;
        if (screenshotName == null) {
            file2 = getFile(file);
        } else {
            file2 = new File(file, screenshotName);
        }

        Util.ioPool()
            .execute(
                () -> {
                    try {
                        nativeImage.writeToFile(file2);
                        Component component = Component.literal(file2.getName())
                            .withStyle(ChatFormatting.UNDERLINE)
                            .withStyle(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, file2.getAbsolutePath())));
                        messageConsumer.accept(Component.translatable("screenshot.success", component));
                    } catch (Exception exception) {
                        LOGGER.warn("Couldn't save screenshot", exception);
                        messageConsumer.accept(Component.translatable("screenshot.failure", exception.getMessage()));
                    } finally {
                        nativeImage.close();
                    }
                }
            );
    }

    public static NativeImage takeScreenshot(RenderTarget framebuffer) {
        int i = framebuffer.width;
        int j = framebuffer.height;
        NativeImage nativeImage = new NativeImage(i, j, false);
        RenderSystem.bindTexture(framebuffer.getColorTextureId());
        nativeImage.downloadTexture(0, true);
        nativeImage.flipY();
        return nativeImage;
    }

    /**
     * Creates a unique PNG file in the given directory named by a timestamp. Handles cases where the timestamp alone is not enough to create a uniquely named file, though it still might suffer from an unlikely race condition where the filename was unique when this method was called, but another process or thread created a file at the same path immediately after this method returned.
     */
    private static File getFile(File gameDirectory) {
        String string = Util.getFilenameFormattedDateTime();
        int i = 1;

        while (true) {
            File file = new File(gameDirectory, string + (i == 1 ? "" : "_" + i) + ".png");
            if (!file.exists()) {
                return file;
            }

            i++;
        }
    }

    public Screenshot(File gameDirectory, int width, int height, int rowHeight) throws IOException {
        this.width = width;
        this.height = height;
        this.rowHeight = rowHeight;
        File file = new File(gameDirectory, "screenshots");
        file.mkdir();
        String string = "huge_" + Util.getFilenameFormattedDateTime();
        int i = 1;

        while ((this.file = new File(file, string + (i == 1 ? "" : "_" + i) + ".tga")).exists()) {
            i++;
        }

        byte[] bs = new byte[18];
        bs[2] = 2;
        bs[12] = (byte)(width % 256);
        bs[13] = (byte)(width / 256);
        bs[14] = (byte)(height % 256);
        bs[15] = (byte)(height / 256);
        bs[16] = 24;
        this.bytes = new byte[width * rowHeight * 3];
        this.outputStream = new DataOutputStream(new FileOutputStream(this.file));
        this.outputStream.write(bs);
    }

    public void addRegion(ByteBuffer buffer, int width, int height, int rowWidth, int rowHeight) {
        int i = rowWidth;
        int j = rowHeight;
        if (i > this.width - width) {
            i = this.width - width;
        }

        if (j > this.height - height) {
            j = this.height - height;
        }

        this.rowHeight = j;

        for (int k = 0; k < j; k++) {
            buffer.position((rowHeight - j) * rowWidth * 3 + k * rowWidth * 3);
            int l = (width + k * this.width) * 3;
            buffer.get(this.bytes, l, i * 3);
        }
    }

    public void saveRow() throws IOException {
        this.outputStream.write(this.bytes, 0, this.width * 3 * this.rowHeight);
    }

    public File close() throws IOException {
        this.outputStream.close();
        return this.file;
    }
}
