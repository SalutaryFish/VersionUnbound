package net.minecraft.client.renderer.texture;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import com.mojang.realmsclient.gui.screens.AddRealmPopupScreen;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

public class TextureManager implements PreparableReloadListener, Tickable, AutoCloseable {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final ResourceLocation INTENTIONAL_MISSING_TEXTURE = ResourceLocation.withDefaultNamespace("");
    private final Map<ResourceLocation, AbstractTexture> byPath = Maps.newHashMap();
    private final Set<Tickable> tickableTextures = Sets.newHashSet();
    private final Map<String, Integer> prefixRegister = Maps.newHashMap();
    private final ResourceManager resourceManager;

    public TextureManager(ResourceManager resourceManager) {
        this.resourceManager = resourceManager;
    }

    public void bindForSetup(ResourceLocation path) {
        if (!RenderSystem.isOnRenderThread()) {
            RenderSystem.recordRenderCall(() -> this._bind(path));
        } else {
            this._bind(path);
        }
    }

    private void _bind(ResourceLocation path) {
        AbstractTexture abstractTexture = this.byPath.get(path);
        if (abstractTexture == null) {
            abstractTexture = new SimpleTexture(path);
            this.register(path, abstractTexture);
        }

        abstractTexture.bind();
    }

    public void register(ResourceLocation path, AbstractTexture texture) {
        texture = this.loadTexture(path, texture);
        AbstractTexture abstractTexture = this.byPath.put(path, texture);
        if (abstractTexture != texture) {
            if (abstractTexture != null && abstractTexture != MissingTextureAtlasSprite.getTexture()) {
                this.safeClose(path, abstractTexture);
            }

            if (texture instanceof Tickable) {
                this.tickableTextures.add((Tickable)texture);
            }
        }
    }

    private void safeClose(ResourceLocation path, AbstractTexture texture) {
        if (texture != MissingTextureAtlasSprite.getTexture()) {
            this.tickableTextures.remove(texture);

            try {
                texture.close();
            } catch (Exception exception) {
                LOGGER.warn("Failed to close texture {}", path, exception);
            }
        }

        texture.releaseId();
    }

    private AbstractTexture loadTexture(ResourceLocation path, AbstractTexture texture) {
        try {
            texture.load(this.resourceManager);
            return texture;
        } catch (IOException iOException) {
            if (path != INTENTIONAL_MISSING_TEXTURE) {
                LOGGER.warn("Failed to load texture: {}", path, iOException);
            }

            return MissingTextureAtlasSprite.getTexture();
        } catch (Throwable throwable) {
            CrashReport crashReport = CrashReport.forThrowable(throwable, "Registering texture");
            CrashReportCategory crashReportCategory = crashReport.addCategory("Resource location being registered");
            crashReportCategory.setDetail("Resource location", path);
            crashReportCategory.setDetail("Texture object class", () -> texture.getClass().getName());
            throw new ReportedException(crashReport);
        }
    }

    public AbstractTexture getTexture(ResourceLocation path) {
        AbstractTexture abstractTexture = this.byPath.get(path);
        if (abstractTexture == null) {
            abstractTexture = new SimpleTexture(path);
            this.register(path, abstractTexture);
        }

        return abstractTexture;
    }

    public AbstractTexture getTexture(ResourceLocation path, AbstractTexture defaultTexture) {
        return this.byPath.getOrDefault(path, defaultTexture);
    }

    public ResourceLocation register(String name, DynamicTexture texture) {
        Integer integer = this.prefixRegister.get(name);
        if (integer == null) {
            integer = 1;
        } else {
            integer = integer + 1;
        }

        this.prefixRegister.put(name, integer);
        ResourceLocation resourceLocation = ResourceLocation.withDefaultNamespace(String.format(Locale.ROOT, "dynamic/%s_%d", name, integer));
        this.register(resourceLocation, texture);
        return resourceLocation;
    }

    public CompletableFuture<Void> preload(ResourceLocation path, Executor backgroundExecutor) {
        if (!this.byPath.containsKey(path)) {
            PreloadedTexture preloadedTexture = new PreloadedTexture(this.resourceManager, path, backgroundExecutor);
            this.byPath.put(path, preloadedTexture);
            return preloadedTexture.getFuture().thenRunAsync(() -> this.register(path, preloadedTexture), TextureManager::execute);
        } else {
            return CompletableFuture.completedFuture(null);
        }
    }

    private static void execute(Runnable runnable) {
        Minecraft.getInstance().execute(() -> RenderSystem.recordRenderCall(runnable::run));
    }

    @Override
    public void tick() {
        for (Tickable tickable : this.tickableTextures) {
            tickable.tick();
        }
    }

    public void release(ResourceLocation path) {
        AbstractTexture abstractTexture = this.byPath.remove(path);
        if (abstractTexture != null) {
            this.safeClose(path, abstractTexture);
        }
    }

    @Override
    public void close() {
        this.byPath.forEach(this::safeClose);
        this.byPath.clear();
        this.tickableTextures.clear();
        this.prefixRegister.clear();
    }

    @Override
    public CompletableFuture<Void> reload(
        PreparableReloadListener.PreparationBarrier stage,
        ResourceManager resourceManager,
        ProfilerFiller preparationsProfiler,
        ProfilerFiller reloadProfiler,
        Executor backgroundExecutor,
        Executor gameExecutor
    ) {
        CompletableFuture<Void> completableFuture = new CompletableFuture<>();
        TitleScreen.preloadResources(this, backgroundExecutor).thenCompose(stage::wait).thenAcceptAsync(void_ -> {
            MissingTextureAtlasSprite.getTexture();
            AddRealmPopupScreen.updateCarouselImages(this.resourceManager);
            Iterator<Entry<ResourceLocation, AbstractTexture>> iterator = this.byPath.entrySet().iterator();

            while (iterator.hasNext()) {
                Entry<ResourceLocation, AbstractTexture> entry = iterator.next();
                ResourceLocation resourceLocation = entry.getKey();
                AbstractTexture abstractTexture = entry.getValue();
                if (abstractTexture == MissingTextureAtlasSprite.getTexture() && !resourceLocation.equals(MissingTextureAtlasSprite.getLocation())) {
                    iterator.remove();
                } else {
                    abstractTexture.reset(this, resourceManager, resourceLocation, gameExecutor);
                }
            }

            Minecraft.getInstance().tell(() -> completableFuture.complete(null));
        }, renderCall -> RenderSystem.recordRenderCall(renderCall::run));
        return completableFuture;
    }

    public void dumpAllSheets(Path path) {
        if (!RenderSystem.isOnRenderThread()) {
            RenderSystem.recordRenderCall(() -> this._dumpAllSheets(path));
        } else {
            this._dumpAllSheets(path);
        }
    }

    private void _dumpAllSheets(Path path) {
        try {
            Files.createDirectories(path);
        } catch (IOException iOException) {
            LOGGER.error("Failed to create directory {}", path, iOException);
            return;
        }

        this.byPath.forEach((pathx, texture) -> {
            if (texture instanceof Dumpable dumpable) {
                try {
                    dumpable.dumpContents(pathx, path);
                } catch (IOException iOException) {
                    LOGGER.error("Failed to dump texture {}", pathx, iOException);
                }
            }
        });
    }
}
