package com.mojang.realmsclient.util;

import com.google.common.collect.Maps;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;

public class RealmsTextureManager {
    private static final Map<String, RealmsTextureManager.RealmsTexture> TEXTURES = Maps.newHashMap();
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation TEMPLATE_ICON_LOCATION = ResourceLocation.withDefaultNamespace("textures/gui/presets/isles.png");

    public static ResourceLocation worldTemplate(String key, @Nullable String image) {
        return image == null ? TEMPLATE_ICON_LOCATION : getTexture(key, image);
    }

    private static ResourceLocation getTexture(String key, String image) {
        RealmsTextureManager.RealmsTexture realmsTexture = TEXTURES.get(key);
        if (realmsTexture != null && realmsTexture.image().equals(image)) {
            return realmsTexture.textureId;
        } else {
            NativeImage nativeImage = loadImage(image);
            if (nativeImage == null) {
                ResourceLocation resourceLocation = MissingTextureAtlasSprite.getLocation();
                TEXTURES.put(key, new RealmsTextureManager.RealmsTexture(image, resourceLocation));
                return resourceLocation;
            } else {
                ResourceLocation resourceLocation = ResourceLocation.fromNamespaceAndPath("realms", "dynamic/" + key);
                Minecraft.getInstance().getTextureManager().register(resourceLocation, new DynamicTexture(nativeImage));
                TEXTURES.put(key, new RealmsTextureManager.RealmsTexture(image, resourceLocation));
                return resourceLocation;
            }
        }
    }

    @Nullable
    private static NativeImage loadImage(String base64Image) {
        byte[] bs = Base64.getDecoder().decode(base64Image);
        ByteBuffer byteBuffer = MemoryUtil.memAlloc(bs.length);

        try {
            return NativeImage.read(byteBuffer.put(bs).flip());
        } catch (IOException iOException) {
            LOGGER.warn("Failed to load world image: {}", base64Image, iOException);
        } finally {
            MemoryUtil.memFree(byteBuffer);
        }

        return null;
    }

    public record RealmsTexture(String image, ResourceLocation textureId) {
    }
}
