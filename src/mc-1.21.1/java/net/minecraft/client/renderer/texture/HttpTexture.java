package net.minecraft.client.renderer.texture;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;

public class HttpTexture extends SimpleTexture {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SKIN_WIDTH = 64;
    private static final int SKIN_HEIGHT = 64;
    private static final int LEGACY_SKIN_HEIGHT = 32;
    @Nullable
    private final File file;
    private final String urlString;
    private final boolean processLegacySkin;
    @Nullable
    private final Runnable onDownloaded;
    @Nullable
    private CompletableFuture<?> future;
    private boolean uploaded;

    public HttpTexture(@Nullable File file, String urlString, ResourceLocation location, boolean processLegacySkin, @Nullable Runnable onDownloaded) {
        super(location);
        this.file = file;
        this.urlString = urlString;
        this.processLegacySkin = processLegacySkin;
        this.onDownloaded = onDownloaded;
    }

    private void loadCallback(NativeImage image) {
        if (this.onDownloaded != null) {
            this.onDownloaded.run();
        }

        Minecraft.getInstance().execute(() -> {
            this.uploaded = true;
            if (!RenderSystem.isOnRenderThread()) {
                RenderSystem.recordRenderCall(() -> this.upload(image));
            } else {
                this.upload(image);
            }
        });
    }

    private void upload(NativeImage image) {
        TextureUtil.prepareImage(this.getId(), image.getWidth(), image.getHeight());
        image.upload(0, 0, 0, true);
    }

    @Override
    public void load(ResourceManager resourceManager) throws IOException {
        Minecraft.getInstance().execute(() -> {
            if (!this.uploaded) {
                try {
                    super.load(resourceManager);
                } catch (IOException iOException) {
                    LOGGER.warn("Failed to load texture: {}", this.location, iOException);
                }

                this.uploaded = true;
            }
        });
        if (this.future == null) {
            NativeImage nativeImage;
            if (this.file != null && this.file.isFile()) {
                LOGGER.debug("Loading http texture from local cache ({})", this.file);
                FileInputStream fileInputStream = new FileInputStream(this.file);
                nativeImage = this.load(fileInputStream);
            } else {
                nativeImage = null;
            }

            if (nativeImage != null) {
                this.loadCallback(nativeImage);
            } else {
                this.future = CompletableFuture.runAsync(() -> {
                    HttpURLConnection httpURLConnection = null;
                    LOGGER.debug("Downloading http texture from {} to {}", this.urlString, this.file);

                    try {
                        httpURLConnection = (HttpURLConnection)new URL(this.urlString).openConnection(Minecraft.getInstance().getProxy());
                        httpURLConnection.setDoInput(true);
                        httpURLConnection.setDoOutput(false);
                        httpURLConnection.connect();
                        if (httpURLConnection.getResponseCode() / 100 == 2) {
                            InputStream inputStream;
                            if (this.file != null) {
                                FileUtils.copyInputStreamToFile(httpURLConnection.getInputStream(), this.file);
                                inputStream = new FileInputStream(this.file);
                            } else {
                                inputStream = httpURLConnection.getInputStream();
                            }

                            Minecraft.getInstance().execute(() -> {
                                NativeImage nativeImagex = this.load(inputStream);
                                if (nativeImagex != null) {
                                    this.loadCallback(nativeImagex);
                                }
                            });
                            return;
                        }
                    } catch (Exception exception) {
                        LOGGER.error("Couldn't download http texture", exception);
                        return;
                    } finally {
                        if (httpURLConnection != null) {
                            httpURLConnection.disconnect();
                        }
                    }
                }, Util.backgroundExecutor());
            }
        }
    }

    @Nullable
    private NativeImage load(InputStream stream) {
        NativeImage nativeImage = null;

        try {
            nativeImage = NativeImage.read(stream);
            if (this.processLegacySkin) {
                nativeImage = this.processLegacySkin(nativeImage);
            }
        } catch (Exception exception) {
            LOGGER.warn("Error while loading the skin texture", exception);
        }

        return nativeImage;
    }

    @Nullable
    private NativeImage processLegacySkin(NativeImage image) {
        int i = image.getHeight();
        int j = image.getWidth();
        if (j == 64 && (i == 32 || i == 64)) {
            boolean bl = i == 32;
            if (bl) {
                NativeImage nativeImage = new NativeImage(64, 64, true);
                nativeImage.copyFrom(image);
                image.close();
                image = nativeImage;
                image.fillRect(0, 32, 64, 32, 0);
                image.copyRect(4, 16, 16, 32, 4, 4, true, false);
                image.copyRect(8, 16, 16, 32, 4, 4, true, false);
                image.copyRect(0, 20, 24, 32, 4, 12, true, false);
                image.copyRect(4, 20, 16, 32, 4, 12, true, false);
                image.copyRect(8, 20, 8, 32, 4, 12, true, false);
                image.copyRect(12, 20, 16, 32, 4, 12, true, false);
                image.copyRect(44, 16, -8, 32, 4, 4, true, false);
                image.copyRect(48, 16, -8, 32, 4, 4, true, false);
                image.copyRect(40, 20, 0, 32, 4, 12, true, false);
                image.copyRect(44, 20, -8, 32, 4, 12, true, false);
                image.copyRect(48, 20, -16, 32, 4, 12, true, false);
                image.copyRect(52, 20, -8, 32, 4, 12, true, false);
            }

            setNoAlpha(image, 0, 0, 32, 16);
            if (bl) {
                doNotchTransparencyHack(image, 32, 0, 64, 32);
            }

            setNoAlpha(image, 0, 16, 64, 32);
            setNoAlpha(image, 16, 48, 48, 64);
            return image;
        } else {
            image.close();
            LOGGER.warn("Discarding incorrectly sized ({}x{}) skin texture from {}", j, i, this.urlString);
            return null;
        }
    }

    private static void doNotchTransparencyHack(NativeImage image, int x, int y, int width, int height) {
        for (int i = x; i < width; i++) {
            for (int j = y; j < height; j++) {
                int k = image.getPixelRGBA(i, j);
                if ((k >> 24 & 0xFF) < 128) {
                    return;
                }
            }
        }

        for (int i = x; i < width; i++) {
            for (int j = y; j < height; j++) {
                image.setPixelRGBA(i, j, image.getPixelRGBA(i, j) & 16777215);
            }
        }
    }

    private static void setNoAlpha(NativeImage image, int x, int y, int width, int height) {
        for (int i = x; i < width; i++) {
            for (int j = y; j < height; j++) {
                image.setPixelRGBA(i, j, image.getPixelRGBA(i, j) | 0xFF000000);
            }
        }
    }
}
