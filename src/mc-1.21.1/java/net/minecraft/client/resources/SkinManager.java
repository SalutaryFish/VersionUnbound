package net.minecraft.client.resources;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.hash.Hashing;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.SignatureState;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.minecraft.MinecraftProfileTextures;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.minecraft.MinecraftProfileTexture.Type;
import com.mojang.authlib.properties.Property;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.Optionull;
import net.minecraft.Util;
import net.minecraft.client.renderer.texture.HttpTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

public class SkinManager {
    static final Logger LOGGER = LogUtils.getLogger();
    private final MinecraftSessionService sessionService;
    private final LoadingCache<SkinManager.CacheKey, CompletableFuture<PlayerSkin>> skinCache;
    private final SkinManager.TextureCache skinTextures;
    private final SkinManager.TextureCache capeTextures;
    private final SkinManager.TextureCache elytraTextures;

    public SkinManager(TextureManager textureManager, Path root, MinecraftSessionService sessionService, Executor executor) {
        this.sessionService = sessionService;
        this.skinTextures = new SkinManager.TextureCache(textureManager, root, Type.SKIN);
        this.capeTextures = new SkinManager.TextureCache(textureManager, root, Type.CAPE);
        this.elytraTextures = new SkinManager.TextureCache(textureManager, root, Type.ELYTRA);
        this.skinCache = CacheBuilder.newBuilder()
            .expireAfterAccess(Duration.ofSeconds(15L))
            .build(
                new CacheLoader<SkinManager.CacheKey, CompletableFuture<PlayerSkin>>() {
                    public CompletableFuture<PlayerSkin> load(SkinManager.CacheKey cacheKey) {
                        return CompletableFuture.<MinecraftProfileTextures>supplyAsync(() -> {
                                Property property = cacheKey.packedTextures();
                                if (property == null) {
                                    return MinecraftProfileTextures.EMPTY;
                                }

                                MinecraftProfileTextures minecraftProfileTextures = sessionService.unpackTextures(property);
                                if (minecraftProfileTextures.signatureState() == SignatureState.INVALID) {
                                    SkinManager.LOGGER.warn("Profile contained invalid signature for textures property (profile id: {})", cacheKey.profileId());
                                }

                                return minecraftProfileTextures;
                            }, Util.backgroundExecutor())
                            .thenComposeAsync(profileTextures -> SkinManager.this.registerTextures(cacheKey.profileId(), profileTextures), executor);
                    }
                }
            );
    }

    public Supplier<PlayerSkin> lookupInsecure(GameProfile profile) {
        CompletableFuture<PlayerSkin> completableFuture = this.getOrLoad(profile);
        PlayerSkin playerSkin = DefaultPlayerSkin.get(profile);
        return () -> completableFuture.getNow(playerSkin);
    }

    public PlayerSkin getInsecureSkin(GameProfile profile) {
        PlayerSkin playerSkin = this.getOrLoad(profile).getNow(null);
        return playerSkin != null ? playerSkin : DefaultPlayerSkin.get(profile);
    }

    public CompletableFuture<PlayerSkin> getOrLoad(GameProfile profile) {
        Property property = this.sessionService.getPackedTextures(profile);
        return this.skinCache.getUnchecked(new SkinManager.CacheKey(profile.getId(), property));
    }

    CompletableFuture<PlayerSkin> registerTextures(UUID uuid, MinecraftProfileTextures textures) {
        MinecraftProfileTexture minecraftProfileTexture = textures.skin();
        CompletableFuture<ResourceLocation> completableFuture;
        PlayerSkin.Model model;
        if (minecraftProfileTexture != null) {
            completableFuture = this.skinTextures.getOrLoad(minecraftProfileTexture);
            model = PlayerSkin.Model.byName(minecraftProfileTexture.getMetadata("model"));
        } else {
            PlayerSkin playerSkin = DefaultPlayerSkin.get(uuid);
            completableFuture = CompletableFuture.completedFuture(playerSkin.texture());
            model = playerSkin.model();
        }

        String string = Optionull.map(minecraftProfileTexture, MinecraftProfileTexture::getUrl);
        MinecraftProfileTexture minecraftProfileTexture2 = textures.cape();
        CompletableFuture<ResourceLocation> completableFuture2 = minecraftProfileTexture2 != null
            ? this.capeTextures.getOrLoad(minecraftProfileTexture2)
            : CompletableFuture.completedFuture(null);
        MinecraftProfileTexture minecraftProfileTexture3 = textures.elytra();
        CompletableFuture<ResourceLocation> completableFuture3 = minecraftProfileTexture3 != null
            ? this.elytraTextures.getOrLoad(minecraftProfileTexture3)
            : CompletableFuture.completedFuture(null);
        return CompletableFuture.allOf(completableFuture, completableFuture2, completableFuture3)
            .thenApply(
                void_ -> new PlayerSkin(
                    completableFuture.join(),
                    string,
                    completableFuture2.join(),
                    completableFuture3.join(),
                    model,
                    textures.signatureState() == SignatureState.SIGNED
                )
            );
    }

    record CacheKey(UUID profileId, @Nullable Property packedTextures) {
    }

    static class TextureCache {
        private final TextureManager textureManager;
        private final Path root;
        private final Type type;
        private final Map<String, CompletableFuture<ResourceLocation>> textures = new Object2ObjectOpenHashMap<>();

        TextureCache(TextureManager textureManager, Path root, Type type) {
            this.textureManager = textureManager;
            this.root = root;
            this.type = type;
        }

        public CompletableFuture<ResourceLocation> getOrLoad(MinecraftProfileTexture texture) {
            String string = texture.getHash();
            CompletableFuture<ResourceLocation> completableFuture = this.textures.get(string);
            if (completableFuture == null) {
                completableFuture = this.registerTexture(texture);
                this.textures.put(string, completableFuture);
            }

            return completableFuture;
        }

        private CompletableFuture<ResourceLocation> registerTexture(MinecraftProfileTexture texture) {
            String string = Hashing.sha1().hashUnencodedChars(texture.getHash()).toString();
            ResourceLocation resourceLocation = this.getTextureLocation(string);
            Path path = this.root.resolve(string.length() > 2 ? string.substring(0, 2) : "xx").resolve(string);
            CompletableFuture<ResourceLocation> completableFuture = new CompletableFuture<>();
            HttpTexture httpTexture = new HttpTexture(
                path.toFile(),
                texture.getUrl(),
                DefaultPlayerSkin.getDefaultTexture(),
                this.type == Type.SKIN,
                () -> completableFuture.complete(resourceLocation)
            );
            this.textureManager.register(resourceLocation, httpTexture);
            return completableFuture;
        }

        private ResourceLocation getTextureLocation(String name) {
            String string = switch (this.type) {
                case SKIN -> "skins";
                case CAPE -> "capes";
                case ELYTRA -> "elytra";
            };
            return ResourceLocation.withDefaultNamespace(string + "/" + name);
        }
    }
}
