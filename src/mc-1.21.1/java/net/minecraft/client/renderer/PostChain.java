package net.minecraft.client.renderer;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.IOException;
import java.io.Reader;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.ChainedJsonException;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.util.GsonHelper;
import org.joml.Matrix4f;

public class PostChain implements AutoCloseable {
    private static final String MAIN_RENDER_TARGET = "minecraft:main";
    private final RenderTarget screenTarget;
    private final ResourceProvider resourceProvider;
    private final String name;
    private final List<PostPass> passes = Lists.newArrayList();
    private final Map<String, RenderTarget> customRenderTargets = Maps.newHashMap();
    private final List<RenderTarget> fullSizedTargets = Lists.newArrayList();
    private Matrix4f shaderOrthoMatrix;
    private int screenWidth;
    private int screenHeight;
    private float time;
    private float lastStamp;

    public PostChain(TextureManager textureManager, ResourceProvider resourceProvider, RenderTarget screenTarget, ResourceLocation resourceLocation) throws IOException, JsonSyntaxException {
        this.resourceProvider = resourceProvider;
        this.screenTarget = screenTarget;
        this.time = 0.0F;
        this.lastStamp = 0.0F;
        this.screenWidth = screenTarget.viewWidth;
        this.screenHeight = screenTarget.viewHeight;
        this.name = resourceLocation.toString();
        this.updateOrthoMatrix();
        this.load(textureManager, resourceLocation);
    }

    private void load(TextureManager textureManager, ResourceLocation resourceLocation) throws IOException, JsonSyntaxException {
        Resource resource = this.resourceProvider.getResourceOrThrow(resourceLocation);

        try (Reader reader = resource.openAsReader()) {
            JsonObject jsonObject = GsonHelper.parse(reader);
            if (GsonHelper.isArrayNode(jsonObject, "targets")) {
                JsonArray jsonArray = jsonObject.getAsJsonArray("targets");
                int i = 0;

                for (JsonElement jsonElement : jsonArray) {
                    try {
                        this.parseTargetNode(jsonElement);
                    } catch (Exception exception) {
                        ChainedJsonException chainedJsonException = ChainedJsonException.forException(exception);
                        chainedJsonException.prependJsonKey("targets[" + i + "]");
                        throw chainedJsonException;
                    }

                    i++;
                }
            }

            if (GsonHelper.isArrayNode(jsonObject, "passes")) {
                JsonArray jsonArray = jsonObject.getAsJsonArray("passes");
                int i = 0;

                for (JsonElement jsonElement : jsonArray) {
                    try {
                        this.parsePassNode(textureManager, jsonElement);
                    } catch (Exception exception) {
                        ChainedJsonException chainedJsonException = ChainedJsonException.forException(exception);
                        chainedJsonException.prependJsonKey("passes[" + i + "]");
                        throw chainedJsonException;
                    }

                    i++;
                }
            }
        } catch (Exception exception2) {
            ChainedJsonException chainedJsonException2 = ChainedJsonException.forException(exception2);
            chainedJsonException2.setFilenameAndFlush(resourceLocation.getPath() + " (" + resource.sourcePackId() + ")");
            throw chainedJsonException2;
        }
    }

    private void parseTargetNode(JsonElement json) throws ChainedJsonException {
        if (GsonHelper.isStringValue(json)) {
            this.addTempTarget(json.getAsString(), this.screenWidth, this.screenHeight);
        } else {
            JsonObject jsonObject = GsonHelper.convertToJsonObject(json, "target");
            String string = GsonHelper.getAsString(jsonObject, "name");
            int i = GsonHelper.getAsInt(jsonObject, "width", this.screenWidth);
            int j = GsonHelper.getAsInt(jsonObject, "height", this.screenHeight);
            if (this.customRenderTargets.containsKey(string)) {
                throw new ChainedJsonException(string + " is already defined");
            }

            this.addTempTarget(string, i, j);
        }
    }

    private void parsePassNode(TextureManager textureManager, JsonElement json) throws IOException {
        JsonObject jsonObject = GsonHelper.convertToJsonObject(json, "pass");
        String string = GsonHelper.getAsString(jsonObject, "name");
        String string2 = GsonHelper.getAsString(jsonObject, "intarget");
        String string3 = GsonHelper.getAsString(jsonObject, "outtarget");
        RenderTarget renderTarget = this.getRenderTarget(string2);
        RenderTarget renderTarget2 = this.getRenderTarget(string3);
        boolean bl = GsonHelper.getAsBoolean(jsonObject, "use_linear_filter", false);
        if (renderTarget == null) {
            throw new ChainedJsonException("Input target '" + string2 + "' does not exist");
        }

        if (renderTarget2 == null) {
            throw new ChainedJsonException("Output target '" + string3 + "' does not exist");
        }

        PostPass postPass = this.addPass(string, renderTarget, renderTarget2, bl);
        JsonArray jsonArray = GsonHelper.getAsJsonArray(jsonObject, "auxtargets", null);
        if (jsonArray != null) {
            int i = 0;

            for (JsonElement jsonElement : jsonArray) {
                try {
                    JsonObject jsonObject2 = GsonHelper.convertToJsonObject(jsonElement, "auxtarget");
                    String string4 = GsonHelper.getAsString(jsonObject2, "name");
                    String string5 = GsonHelper.getAsString(jsonObject2, "id");
                    boolean bl2;
                    String string6;
                    if (string5.endsWith(":depth")) {
                        bl2 = true;
                        string6 = string5.substring(0, string5.lastIndexOf(58));
                    } else {
                        bl2 = false;
                        string6 = string5;
                    }

                    RenderTarget renderTarget3 = this.getRenderTarget(string6);
                    if (renderTarget3 == null) {
                        if (bl2) {
                            throw new ChainedJsonException("Render target '" + string6 + "' can't be used as depth buffer");
                        }

                        ResourceLocation resourceLocation = ResourceLocation.withDefaultNamespace("textures/effect/" + string6 + ".png");
                        this.resourceProvider
                            .getResource(resourceLocation)
                            .orElseThrow(() -> new ChainedJsonException("Render target or texture '" + string6 + "' does not exist"));
                        RenderSystem.setShaderTexture(0, resourceLocation);
                        textureManager.bindForSetup(resourceLocation);
                        AbstractTexture abstractTexture = textureManager.getTexture(resourceLocation);
                        int j = GsonHelper.getAsInt(jsonObject2, "width");
                        int k = GsonHelper.getAsInt(jsonObject2, "height");
                        boolean bl3 = GsonHelper.getAsBoolean(jsonObject2, "bilinear");
                        if (bl3) {
                            RenderSystem.texParameter(3553, 10241, 9729);
                            RenderSystem.texParameter(3553, 10240, 9729);
                        } else {
                            RenderSystem.texParameter(3553, 10241, 9728);
                            RenderSystem.texParameter(3553, 10240, 9728);
                        }

                        postPass.addAuxAsset(string4, abstractTexture::getId, j, k);
                    } else if (bl2) {
                        postPass.addAuxAsset(string4, renderTarget3::getDepthTextureId, renderTarget3.width, renderTarget3.height);
                    } else {
                        postPass.addAuxAsset(string4, renderTarget3::getColorTextureId, renderTarget3.width, renderTarget3.height);
                    }
                } catch (Exception exception) {
                    ChainedJsonException chainedJsonException = ChainedJsonException.forException(exception);
                    chainedJsonException.prependJsonKey("auxtargets[" + i + "]");
                    throw chainedJsonException;
                }

                i++;
            }
        }

        JsonArray jsonArray2 = GsonHelper.getAsJsonArray(jsonObject, "uniforms", null);
        if (jsonArray2 != null) {
            int l = 0;

            for (JsonElement jsonElement2 : jsonArray2) {
                try {
                    this.parseUniformNode(jsonElement2);
                } catch (Exception exception2) {
                    ChainedJsonException chainedJsonException2 = ChainedJsonException.forException(exception2);
                    chainedJsonException2.prependJsonKey("uniforms[" + l + "]");
                    throw chainedJsonException2;
                }

                l++;
            }
        }
    }

    private void parseUniformNode(JsonElement json) throws ChainedJsonException {
        JsonObject jsonObject = GsonHelper.convertToJsonObject(json, "uniform");
        String string = GsonHelper.getAsString(jsonObject, "name");
        Uniform uniform = this.passes.get(this.passes.size() - 1).getEffect().getUniform(string);
        if (uniform == null) {
            throw new ChainedJsonException("Uniform '" + string + "' does not exist");
        }

        float[] fs = new float[4];
        int i = 0;

        for (JsonElement jsonElement : GsonHelper.getAsJsonArray(jsonObject, "values")) {
            try {
                fs[i] = GsonHelper.convertToFloat(jsonElement, "value");
            } catch (Exception exception) {
                ChainedJsonException chainedJsonException = ChainedJsonException.forException(exception);
                chainedJsonException.prependJsonKey("values[" + i + "]");
                throw chainedJsonException;
            }

            i++;
        }

        switch (i) {
            case 0:
            default:
                break;
            case 1:
                uniform.set(fs[0]);
                break;
            case 2:
                uniform.set(fs[0], fs[1]);
                break;
            case 3:
                uniform.set(fs[0], fs[1], fs[2]);
                break;
            case 4:
                uniform.set(fs[0], fs[1], fs[2], fs[3]);
        }
    }

    public RenderTarget getTempTarget(String attributeName) {
        return this.customRenderTargets.get(attributeName);
    }

    public void addTempTarget(String name, int width, int height) {
        RenderTarget renderTarget = new TextureTarget(width, height, true, Minecraft.ON_OSX);
        renderTarget.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        this.customRenderTargets.put(name, renderTarget);
        if (width == this.screenWidth && height == this.screenHeight) {
            this.fullSizedTargets.add(renderTarget);
        }
    }

    @Override
    public void close() {
        for (RenderTarget renderTarget : this.customRenderTargets.values()) {
            renderTarget.destroyBuffers();
        }

        for (PostPass postPass : this.passes) {
            postPass.close();
        }

        this.passes.clear();
    }

    public PostPass addPass(String name, RenderTarget inTarget, RenderTarget outTarget, boolean useLinearFilter) throws IOException {
        PostPass postPass = new PostPass(this.resourceProvider, name, inTarget, outTarget, useLinearFilter);
        this.passes.add(this.passes.size(), postPass);
        return postPass;
    }

    private void updateOrthoMatrix() {
        this.shaderOrthoMatrix = new Matrix4f().setOrtho(0.0F, this.screenTarget.width, 0.0F, this.screenTarget.height, 0.1F, 1000.0F);
    }

    public void resize(int width, int height) {
        this.screenWidth = this.screenTarget.width;
        this.screenHeight = this.screenTarget.height;
        this.updateOrthoMatrix();

        for (PostPass postPass : this.passes) {
            postPass.setOrthoMatrix(this.shaderOrthoMatrix);
        }

        for (RenderTarget renderTarget : this.fullSizedTargets) {
            renderTarget.resize(width, height, Minecraft.ON_OSX);
        }
    }

    private void setFilterMode(int filterMode) {
        this.screenTarget.setFilterMode(filterMode);

        for (RenderTarget renderTarget : this.customRenderTargets.values()) {
            renderTarget.setFilterMode(filterMode);
        }
    }

    public void process(float partialTicks) {
        this.time += partialTicks;

        while (this.time > 20.0F) {
            this.time -= 20.0F;
        }

        int i = 9728;

        for (PostPass postPass : this.passes) {
            int j = postPass.getFilterMode();
            if (i != j) {
                this.setFilterMode(j);
                i = j;
            }

            postPass.process(this.time / 20.0F);
        }

        this.setFilterMode(9728);
    }

    public void setUniform(String name, float backgroundBlurriness) {
        for (PostPass postPass : this.passes) {
            postPass.getEffect().safeGetUniform(name).set(backgroundBlurriness);
        }
    }

    public final String getName() {
        return this.name;
    }

    @Nullable
    private RenderTarget getRenderTarget(@Nullable String target) {
        if (target == null) {
            return null;
        } else {
            return target.equals("minecraft:main") ? this.screenTarget : this.customRenderTargets.get(target);
        }
    }
}
