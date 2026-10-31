package net.minecraft.client.renderer;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.AbstractUniform;
import com.mojang.blaze3d.shaders.BlendMode;
import com.mojang.blaze3d.shaders.Effect;
import com.mojang.blaze3d.shaders.EffectProgram;
import com.mojang.blaze3d.shaders.Program;
import com.mojang.blaze3d.shaders.ProgramManager;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidClassException;
import java.io.Reader;
import java.util.List;
import java.util.Map;
import java.util.function.IntSupplier;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.ChainedJsonException;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.util.GsonHelper;
import org.slf4j.Logger;

public class EffectInstance implements Effect, AutoCloseable {
    private static final String EFFECT_SHADER_PATH = "shaders/program/";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final AbstractUniform DUMMY_UNIFORM = new AbstractUniform();
    private static final boolean ALWAYS_REAPPLY = true;
    private static EffectInstance lastAppliedEffect;
    private static int lastProgramId = -1;
    private final Map<String, IntSupplier> samplerMap = Maps.newHashMap();
    private final List<String> samplerNames = Lists.newArrayList();
    private final List<Integer> samplerLocations = Lists.newArrayList();
    private final List<Uniform> uniforms = Lists.newArrayList();
    private final List<Integer> uniformLocations = Lists.newArrayList();
    private final Map<String, Uniform> uniformMap = Maps.newHashMap();
    private final int programId;
    private final String name;
    private boolean dirty;
    private final BlendMode blend;
    private final List<Integer> attributes;
    private final List<String> attributeNames;
    private final EffectProgram vertexProgram;
    private final EffectProgram fragmentProgram;

    public EffectInstance(ResourceProvider resourceProvider, String name) throws IOException {
        ResourceLocation resourceLocation = ResourceLocation.withDefaultNamespace("shaders/program/" + name + ".json");
        this.name = name;
        Resource resource = resourceProvider.getResourceOrThrow(resourceLocation);

        try (Reader reader = resource.openAsReader()) {
            JsonObject jsonObject = GsonHelper.parse(reader);
            String string = GsonHelper.getAsString(jsonObject, "vertex");
            String string2 = GsonHelper.getAsString(jsonObject, "fragment");
            JsonArray jsonArray = GsonHelper.getAsJsonArray(jsonObject, "samplers", null);
            if (jsonArray != null) {
                int i = 0;

                for (JsonElement jsonElement : jsonArray) {
                    try {
                        this.parseSamplerNode(jsonElement);
                    } catch (Exception exception) {
                        ChainedJsonException chainedJsonException = ChainedJsonException.forException(exception);
                        chainedJsonException.prependJsonKey("samplers[" + i + "]");
                        throw chainedJsonException;
                    }

                    i++;
                }
            }

            JsonArray jsonArray2 = GsonHelper.getAsJsonArray(jsonObject, "attributes", null);
            if (jsonArray2 != null) {
                int j = 0;
                this.attributes = Lists.newArrayListWithCapacity(jsonArray2.size());
                this.attributeNames = Lists.newArrayListWithCapacity(jsonArray2.size());

                for (JsonElement jsonElement2 : jsonArray2) {
                    try {
                        this.attributeNames.add(GsonHelper.convertToString(jsonElement2, "attribute"));
                    } catch (Exception exception2) {
                        ChainedJsonException chainedJsonException2 = ChainedJsonException.forException(exception2);
                        chainedJsonException2.prependJsonKey("attributes[" + j + "]");
                        throw chainedJsonException2;
                    }

                    j++;
                }
            } else {
                this.attributes = null;
                this.attributeNames = null;
            }

            JsonArray jsonArray3 = GsonHelper.getAsJsonArray(jsonObject, "uniforms", null);
            if (jsonArray3 != null) {
                int k = 0;

                for (JsonElement jsonElement3 : jsonArray3) {
                    try {
                        this.parseUniformNode(jsonElement3);
                    } catch (Exception exception3) {
                        ChainedJsonException chainedJsonException3 = ChainedJsonException.forException(exception3);
                        chainedJsonException3.prependJsonKey("uniforms[" + k + "]");
                        throw chainedJsonException3;
                    }

                    k++;
                }
            }

            this.blend = parseBlendNode(GsonHelper.getAsJsonObject(jsonObject, "blend", null));
            this.vertexProgram = getOrCreate(resourceProvider, Program.Type.VERTEX, string);
            this.fragmentProgram = getOrCreate(resourceProvider, Program.Type.FRAGMENT, string2);
            this.programId = ProgramManager.createProgram();
            ProgramManager.linkShader(this);
            this.updateLocations();
            if (this.attributeNames != null) {
                for (String string3 : this.attributeNames) {
                    int l = Uniform.glGetAttribLocation(this.programId, string3);
                    this.attributes.add(l);
                }
            }
        } catch (Exception exception4) {
            ChainedJsonException chainedJsonException4 = ChainedJsonException.forException(exception4);
            chainedJsonException4.setFilenameAndFlush(resourceLocation.getPath() + " (" + resource.sourcePackId() + ")");
            throw chainedJsonException4;
        }

        this.markDirty();
    }

    public static EffectProgram getOrCreate(ResourceProvider resourceProvider, Program.Type type, String name) throws IOException {
        Program program = type.getPrograms().get(name);
        if (program != null && !(program instanceof EffectProgram)) {
            throw new InvalidClassException("Program is not of type EffectProgram");
        }

        EffectProgram effectProgram;
        if (program == null) {
            ResourceLocation resourceLocation = ResourceLocation.withDefaultNamespace("shaders/program/" + name + type.getExtension());
            Resource resource = resourceProvider.getResourceOrThrow(resourceLocation);

            try (InputStream inputStream = resource.open()) {
                effectProgram = EffectProgram.compileShader(type, name, inputStream, resource.sourcePackId());
            }
        } else {
            effectProgram = (EffectProgram)program;
        }

        return effectProgram;
    }

    public static BlendMode parseBlendNode(@Nullable JsonObject json) {
        if (json == null) {
            return new BlendMode();
        }

        int i = 32774;
        int j = 1;
        int k = 0;
        int l = 1;
        int m = 0;
        boolean bl = true;
        boolean bl2 = false;
        if (GsonHelper.isStringValue(json, "func")) {
            i = BlendMode.stringToBlendFunc(json.get("func").getAsString());
            if (i != 32774) {
                bl = false;
            }
        }

        if (GsonHelper.isStringValue(json, "srcrgb")) {
            j = BlendMode.stringToBlendFactor(json.get("srcrgb").getAsString());
            if (j != 1) {
                bl = false;
            }
        }

        if (GsonHelper.isStringValue(json, "dstrgb")) {
            k = BlendMode.stringToBlendFactor(json.get("dstrgb").getAsString());
            if (k != 0) {
                bl = false;
            }
        }

        if (GsonHelper.isStringValue(json, "srcalpha")) {
            l = BlendMode.stringToBlendFactor(json.get("srcalpha").getAsString());
            if (l != 1) {
                bl = false;
            }

            bl2 = true;
        }

        if (GsonHelper.isStringValue(json, "dstalpha")) {
            m = BlendMode.stringToBlendFactor(json.get("dstalpha").getAsString());
            if (m != 0) {
                bl = false;
            }

            bl2 = true;
        }

        if (bl) {
            return new BlendMode();
        } else {
            return bl2 ? new BlendMode(j, k, l, m, i) : new BlendMode(j, k, i);
        }
    }

    @Override
    public void close() {
        for (Uniform uniform : this.uniforms) {
            uniform.close();
        }

        ProgramManager.releaseProgram(this);
    }

    public void clear() {
        RenderSystem.assertOnRenderThread();
        ProgramManager.glUseProgram(0);
        lastProgramId = -1;
        lastAppliedEffect = null;

        for (int i = 0; i < this.samplerLocations.size(); i++) {
            if (this.samplerMap.get(this.samplerNames.get(i)) != null) {
                GlStateManager._activeTexture(33984 + i);
                GlStateManager._bindTexture(0);
            }
        }
    }

    public void apply() {
        this.dirty = false;
        lastAppliedEffect = this;
        this.blend.apply();
        if (this.programId != lastProgramId) {
            ProgramManager.glUseProgram(this.programId);
            lastProgramId = this.programId;
        }

        for (int i = 0; i < this.samplerLocations.size(); i++) {
            String string = this.samplerNames.get(i);
            IntSupplier intSupplier = this.samplerMap.get(string);
            if (intSupplier != null) {
                RenderSystem.activeTexture(33984 + i);
                int j = intSupplier.getAsInt();
                if (j != -1) {
                    RenderSystem.bindTexture(j);
                    Uniform.uploadInteger(this.samplerLocations.get(i), i);
                }
            }
        }

        for (Uniform uniform : this.uniforms) {
            uniform.upload();
        }
    }

    @Override
    public void markDirty() {
        this.dirty = true;
    }

    @Nullable
    public Uniform getUniform(String name) {
        RenderSystem.assertOnRenderThread();
        return this.uniformMap.get(name);
    }

    public AbstractUniform safeGetUniform(String name) {
        Uniform uniform = this.getUniform(name);
        return uniform == null ? DUMMY_UNIFORM : uniform;
    }

    private void updateLocations() {
        RenderSystem.assertOnRenderThread();
        IntList intList = new IntArrayList();

        for (int i = 0; i < this.samplerNames.size(); i++) {
            String string = this.samplerNames.get(i);
            int j = Uniform.glGetUniformLocation(this.programId, string);
            if (j == -1) {
                LOGGER.warn("Shader {} could not find sampler named {} in the specified shader program.", this.name, string);
                this.samplerMap.remove(string);
                intList.add(i);
            } else {
                this.samplerLocations.add(j);
            }
        }

        for (int i = intList.size() - 1; i >= 0; i--) {
            this.samplerNames.remove(intList.getInt(i));
        }

        for (Uniform uniform : this.uniforms) {
            String string2 = uniform.getName();
            int k = Uniform.glGetUniformLocation(this.programId, string2);
            if (k == -1) {
                LOGGER.warn("Shader {} could not find uniform named {} in the specified shader program.", this.name, string2);
            } else {
                this.uniformLocations.add(k);
                uniform.setLocation(k);
                this.uniformMap.put(string2, uniform);
            }
        }
    }

    private void parseSamplerNode(JsonElement json) {
        JsonObject jsonObject = GsonHelper.convertToJsonObject(json, "sampler");
        String string = GsonHelper.getAsString(jsonObject, "name");
        if (!GsonHelper.isStringValue(jsonObject, "file")) {
            this.samplerMap.put(string, null);
            this.samplerNames.add(string);
        } else {
            this.samplerNames.add(string);
        }
    }

    public void setSampler(String name, IntSupplier textureId) {
        if (this.samplerMap.containsKey(name)) {
            this.samplerMap.remove(name);
        }

        this.samplerMap.put(name, textureId);
        this.markDirty();
    }

    private void parseUniformNode(JsonElement json) throws ChainedJsonException {
        JsonObject jsonObject = GsonHelper.convertToJsonObject(json, "uniform");
        String string = GsonHelper.getAsString(jsonObject, "name");
        int i = Uniform.getTypeFromString(GsonHelper.getAsString(jsonObject, "type"));
        int j = GsonHelper.getAsInt(jsonObject, "count");
        float[] fs = new float[Math.max(j, 16)];
        JsonArray jsonArray = GsonHelper.getAsJsonArray(jsonObject, "values");
        if (jsonArray.size() != j && jsonArray.size() > 1) {
            throw new ChainedJsonException("Invalid amount of values specified (expected " + j + ", found " + jsonArray.size() + ")");
        }

        int k = 0;

        for (JsonElement jsonElement : jsonArray) {
            try {
                fs[k] = GsonHelper.convertToFloat(jsonElement, "value");
            } catch (Exception exception) {
                ChainedJsonException chainedJsonException = ChainedJsonException.forException(exception);
                chainedJsonException.prependJsonKey("values[" + k + "]");
                throw chainedJsonException;
            }

            k++;
        }

        if (j > 1 && jsonArray.size() == 1) {
            while (k < j) {
                fs[k] = fs[0];
                k++;
            }
        }

        int l = j > 1 && j <= 4 && i < 8 ? j - 1 : 0;
        Uniform uniform = new Uniform(string, i + l, j, this);
        if (i <= 3) {
            uniform.setSafe((int)fs[0], (int)fs[1], (int)fs[2], (int)fs[3]);
        } else if (i <= 7) {
            uniform.setSafe(fs[0], fs[1], fs[2], fs[3]);
        } else {
            uniform.set(fs);
        }

        this.uniforms.add(uniform);
    }

    @Override
    public Program getVertexProgram() {
        return this.vertexProgram;
    }

    @Override
    public Program getFragmentProgram() {
        return this.fragmentProgram;
    }

    @Override
    public void attachToProgram() {
        this.fragmentProgram.attachToEffect(this);
        this.vertexProgram.attachToEffect(this);
    }

    public String getName() {
        return this.name;
    }

    @Override
    public int getId() {
        return this.programId;
    }
}
