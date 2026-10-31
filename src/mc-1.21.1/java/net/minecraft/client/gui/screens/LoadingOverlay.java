package net.minecraft.client.gui.screens;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.client.resources.metadata.texture.TextureMetadataSection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.VanillaPackResources;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;

public class LoadingOverlay extends Overlay {
    static final ResourceLocation MOJANG_STUDIOS_LOGO_LOCATION = ResourceLocation.withDefaultNamespace("textures/gui/title/mojangstudios.png");
    private static final int LOGO_BACKGROUND_COLOR = FastColor.ARGB32.color(255, 239, 50, 61);
    private static final int LOGO_BACKGROUND_COLOR_DARK = FastColor.ARGB32.color(255, 0, 0, 0);
    private static final IntSupplier BRAND_BACKGROUND = () -> Minecraft.getInstance().options.darkMojangStudiosBackground().get()
        ? LOGO_BACKGROUND_COLOR_DARK
        : LOGO_BACKGROUND_COLOR;
    private static final int LOGO_SCALE = 240;
    private static final float LOGO_QUARTER_FLOAT = 60.0F;
    private static final int LOGO_QUARTER = 60;
    private static final int LOGO_HALF = 120;
    private static final float LOGO_OVERLAP = 0.0625F;
    private static final float SMOOTHING = 0.95F;
    public static final long FADE_OUT_TIME = 1000L;
    public static final long FADE_IN_TIME = 500L;
    private final Minecraft minecraft;
    private final ReloadInstance reload;
    private final Consumer<Optional<Throwable>> onFinish;
    private final boolean fadeIn;
    private float currentProgress;
    private long fadeOutStart = -1L;
    private long fadeInStart = -1L;

    public LoadingOverlay(Minecraft minecraft, ReloadInstance reload, Consumer<Optional<Throwable>> onFinish, boolean fadeIn) {
        this.minecraft = minecraft;
        this.reload = reload;
        this.onFinish = onFinish;
        this.fadeIn = fadeIn;
    }

    public static void registerTextures(Minecraft minecraft) {
        minecraft.getTextureManager().register(MOJANG_STUDIOS_LOGO_LOCATION, new LoadingOverlay.LogoTexture());
    }

    private static int replaceAlpha(int color, int alpha) {
        return color & 16777215 | alpha << 24;
    }

    /**
     * Renders the graphical user interface (GUI) element.
     *
     * @param guiGraphics the GuiGraphics object used for rendering.
     * @param mouseX      the x-coordinate of the mouse cursor.
     * @param mouseY      the y-coordinate of the mouse cursor.
     * @param partialTick the partial tick time.
     */
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int i = guiGraphics.guiWidth();
        int j = guiGraphics.guiHeight();
        long l = Util.getMillis();
        if (this.fadeIn && this.fadeInStart == -1L) {
            this.fadeInStart = l;
        }

        float f = this.fadeOutStart > -1L ? (float)(l - this.fadeOutStart) / 1000.0F : -1.0F;
        float g = this.fadeInStart > -1L ? (float)(l - this.fadeInStart) / 500.0F : -1.0F;
        float h;
        if (f >= 1.0F) {
            if (this.minecraft.screen != null) {
                this.minecraft.screen.render(guiGraphics, 0, 0, partialTick);
            }

            int k = Mth.ceil((1.0F - Mth.clamp(f - 1.0F, 0.0F, 1.0F)) * 255.0F);
            guiGraphics.fill(RenderType.guiOverlay(), 0, 0, i, j, replaceAlpha(BRAND_BACKGROUND.getAsInt(), k));
            h = 1.0F - Mth.clamp(f - 1.0F, 0.0F, 1.0F);
        } else if (this.fadeIn) {
            if (this.minecraft.screen != null && g < 1.0F) {
                this.minecraft.screen.render(guiGraphics, mouseX, mouseY, partialTick);
            }

            int k = Mth.ceil(Mth.clamp(g, 0.15, 1.0) * 255.0);
            guiGraphics.fill(RenderType.guiOverlay(), 0, 0, i, j, replaceAlpha(BRAND_BACKGROUND.getAsInt(), k));
            h = Mth.clamp(g, 0.0F, 1.0F);
        } else {
            int k = BRAND_BACKGROUND.getAsInt();
            float m = (k >> 16 & 0xFF) / 255.0F;
            float n = (k >> 8 & 0xFF) / 255.0F;
            float o = (k & 0xFF) / 255.0F;
            GlStateManager._clearColor(m, n, o, 1.0F);
            GlStateManager._clear(16384, Minecraft.ON_OSX);
            h = 1.0F;
        }

        int k = (int)(guiGraphics.guiWidth() * 0.5);
        int p = (int)(guiGraphics.guiHeight() * 0.5);
        double d = Math.min(guiGraphics.guiWidth() * 0.75, guiGraphics.guiHeight()) * 0.25;
        int q = (int)(d * 0.5);
        double e = d * 4.0;
        int r = (int)(e * 0.5);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(770, 1);
        guiGraphics.setColor(1.0F, 1.0F, 1.0F, h);
        guiGraphics.blit(MOJANG_STUDIOS_LOGO_LOCATION, k - r, p - q, r, (int)d, -0.0625F, 0.0F, 120, 60, 120, 120);
        guiGraphics.blit(MOJANG_STUDIOS_LOGO_LOCATION, k, p - q, r, (int)d, 0.0625F, 60.0F, 120, 60, 120, 120);
        guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        int s = (int)(guiGraphics.guiHeight() * 0.8325);
        float t = this.reload.getActualProgress();
        this.currentProgress = Mth.clamp(this.currentProgress * 0.95F + t * 0.050000012F, 0.0F, 1.0F);
        if (f < 1.0F) {
            this.drawProgressBar(guiGraphics, i / 2 - r, s - 5, i / 2 + r, s + 5, 1.0F - Mth.clamp(f, 0.0F, 1.0F));
        }

        if (f >= 2.0F) {
            this.minecraft.setOverlay(null);
        }

        if (this.fadeOutStart == -1L && this.reload.isDone() && (!this.fadeIn || g >= 2.0F)) {
            try {
                this.reload.checkExceptions();
                this.onFinish.accept(Optional.empty());
            } catch (Throwable throwable) {
                this.onFinish.accept(Optional.of(throwable));
            }

            this.fadeOutStart = Util.getMillis();
            if (this.minecraft.screen != null) {
                this.minecraft.screen.init(this.minecraft, guiGraphics.guiWidth(), guiGraphics.guiHeight());
            }
        }
    }

    private void drawProgressBar(GuiGraphics guiGraphics, int minX, int minY, int maxX, int maxY, float partialTick) {
        int i = Mth.ceil((maxX - minX - 2) * this.currentProgress);
        int j = Math.round(partialTick * 255.0F);
        int k = FastColor.ARGB32.color(j, 255, 255, 255);
        guiGraphics.fill(minX + 2, minY + 2, minX + i, maxY - 2, k);
        guiGraphics.fill(minX + 1, minY, maxX - 1, minY + 1, k);
        guiGraphics.fill(minX + 1, maxY, maxX - 1, maxY - 1, k);
        guiGraphics.fill(minX, minY, minX + 1, maxY, k);
        guiGraphics.fill(maxX, minY, maxX - 1, maxY, k);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    static class LogoTexture extends SimpleTexture {
        public LogoTexture() {
            super(LoadingOverlay.MOJANG_STUDIOS_LOGO_LOCATION);
        }

        @Override
        protected SimpleTexture.TextureImage getTextureImage(ResourceManager resourceManager) {
            VanillaPackResources vanillaPackResources = Minecraft.getInstance().getVanillaPackResources();
            IoSupplier<InputStream> ioSupplier = vanillaPackResources.getResource(PackType.CLIENT_RESOURCES, LoadingOverlay.MOJANG_STUDIOS_LOGO_LOCATION);
            if (ioSupplier == null) {
                return new SimpleTexture.TextureImage(new FileNotFoundException(LoadingOverlay.MOJANG_STUDIOS_LOGO_LOCATION.toString()));
            }

            try (InputStream inputStream = ioSupplier.get()) {
                return new SimpleTexture.TextureImage(new TextureMetadataSection(true, true), NativeImage.read(inputStream));
            } catch (IOException iOException) {
                return new SimpleTexture.TextureImage(iOException);
            }
        }
    }
}
