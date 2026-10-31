package net.minecraft.client.renderer;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.preprocessor.GlslPreprocessor;
import com.mojang.blaze3d.shaders.AbstractUniform;
import com.mojang.blaze3d.shaders.Program;
import com.mojang.blaze3d.shaders.ProgramManager;
import com.mojang.blaze3d.shaders.Shader;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.FileUtil;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.ChainedJsonException;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.util.GsonHelper;
import org.apache.commons.io.IOUtils;
import org.joml.Matrix4f;
import org.slf4j.Logger;

public class ShaderInstance implements Shader, AutoCloseable {
    public static final String SHADER_PATH = "shaders";
    private static final String SHADER_CORE_PATH = "shaders/core/";
    private static final String SHADER_INCLUDE_PATH = "shaders/include/";
    static final Logger LOGGER = LogUtils.getLogger();
    private static final AbstractUniform DUMMY_UNIFORM = new AbstractUniform();
    private static final boolean ALWAYS_REAPPLY = true;
    private static ShaderInstance lastAppliedShader;
    private static int lastProgramId = -1;
    private final Map<String, Object> samplerMap = Maps.newHashMap();
    private final List<String> samplerNames = Lists.newArrayList();
    private final List<Integer> samplerLocations = Lists.newArrayList();
    private final List<Uniform> uniforms = Lists.newArrayList();
    private final List<Integer> uniformLocations = Lists.newArrayList();
    private final Map<String, Uniform> uniformMap = Maps.newHashMap();
    private final int programId;
    private final String name;
    private boolean dirty;
    private final Program vertexProgram;
    private final Program fragmentProgram;
    private final VertexFormat vertexFormat;
    @Nullable
    public final Uniform MODEL_VIEW_MATRIX;
    @Nullable
    public final Uniform PROJECTION_MATRIX;
    @Nullable
    public final Uniform TEXTURE_MATRIX;
    @Nullable
    public final Uniform SCREEN_SIZE;
    @Nullable
    public final Uniform COLOR_MODULATOR;
    @Nullable
    public final Uniform LIGHT0_DIRECTION;
    @Nullable
    public final Uniform LIGHT1_DIRECTION;
    @Nullable
    public final Uniform GLINT_ALPHA;
    @Nullable
    public final Uniform FOG_START;
    @Nullable
    public final Uniform FOG_END;
    @Nullable
    public final Uniform FOG_COLOR;
    @Nullable
    public final Uniform FOG_SHAPE;
    @Nullable
    public final Uniform LINE_WIDTH;
    @Nullable
    public final Uniform GAME_TIME;
    @Nullable
    public final Uniform CHUNK_OFFSET;

    public ShaderInstance(ResourceProvider resourceProvider, String name, VertexFormat vertexFormat) throws IOException {
        this.name = name;
        this.vertexFormat = vertexFormat;
        ResourceLocation resourceLocation = ResourceLocation.withDefaultNamespace("shaders/core/" + name + ".json");

        try (Reader reader = resourceProvider.openAsReader(resourceLocation)) {
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

            JsonArray jsonArray2 = GsonHelper.getAsJsonArray(jsonObject, "uniforms", null);
            if (jsonArray2 != null) {
                int j = 0;

                for (JsonElement jsonElement2 : jsonArray2) {
                    try {
                        this.parseUniformNode(jsonElement2);
                    } catch (Exception exception2) {
                        ChainedJsonException chainedJsonException2 = ChainedJsonException.forException(exception2);
                        chainedJsonException2.prependJsonKey("uniforms[" + j + "]");
                        throw chainedJsonException2;
                    }

                    j++;
                }
            }

            this.vertexProgram = getOrCreate(resourceProvider, Program.Type.VERTEX, string);
            this.fragmentProgram = getOrCreate(resourceProvider, Program.Type.FRAGMENT, string2);
            this.programId = ProgramManager.createProgram();
            int j = 0;

            for (String string3 : vertexFormat.getElementAttributeNames()) {
                Uniform.glBindAttribLocation(this.programId, j, string3);
                j++;
            }

            ProgramManager.linkShader(this);
            this.updateLocations();
        } catch (Exception exception3) {
            ChainedJsonException chainedJsonException3 = ChainedJsonException.forException(exception3);
            chainedJsonException3.setFilenameAndFlush(resourceLocation.getPath());
            throw chainedJsonException3;
        }

        this.markDirty();
        this.MODEL_VIEW_MATRIX = this.getUniform("ModelViewMat");
        this.PROJECTION_MATRIX = this.getUniform("ProjMat");
        this.TEXTURE_MATRIX = this.getUniform("TextureMat");
        this.SCREEN_SIZE = this.getUniform("ScreenSize");
        this.COLOR_MODULATOR = this.getUniform("ColorModulator");
        this.LIGHT0_DIRECTION = this.getUniform("Light0_Direction");
        this.LIGHT1_DIRECTION = this.getUniform("Light1_Direction");
        this.GLINT_ALPHA = this.getUniform("GlintAlpha");
        this.FOG_START = this.getUniform("FogStart");
        this.FOG_END = this.getUniform("FogEnd");
        this.FOG_COLOR = this.getUniform("FogColor");
        this.FOG_SHAPE = this.getUniform("FogShape");
        this.LINE_WIDTH = this.getUniform("LineWidth");
        this.GAME_TIME = this.getUniform("GameTime");
        this.CHUNK_OFFSET = this.getUniform("ChunkOffset");
    }

    private static Program getOrCreate(ResourceProvider resourceProvider, Program.Type programType, String name) throws IOException {
        Program program = programType.getPrograms().get(name);
        Program program2;
        if (program == null) {
            String string = "shaders/core/" + name + programType.getExtension();
            Resource resource = resourceProvider.getResourceOrThrow(ResourceLocation.withDefaultNamespace(string));

            try (InputStream inputStream = resource.open()) {
                final String string2 = FileUtil.getFullResourcePath(string);
                program2 = Program.compileShader(programType, name, inputStream, resource.sourcePackId(), new GlslPreprocessor() {
                    private final Set<String> importedPaths = Sets.newHashSet();

                    @Override
                    public String applyImport(boolean bl, String stringx) {
                        stringx = FileUtil.normalizeResourcePath((bl ? string2 : "shaders/include/") + stringx);
                        if (!this.importedPaths.add(stringx)) {
                            return null;
                        }

                        ResourceLocation resourceLocation = ResourceLocation.parse(stringx);

                        try (Reader reader = resourceProvider.openAsReader(resourceLocation)) {
                            return IOUtils.toString(reader);
                        } catch (IOException iOException) {
                            ShaderInstance.LOGGER.error("Could not open GLSL import {}: {}", stringx, iOException.getMessage());
                            return "#error " + iOException.getMessage();
                        }
                    }
                });
            }
        } else {
            program2 = program;
        }

        return program2;
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
        lastAppliedShader = null;
        int i = GlStateManager._getActiveTexture();

        for (int j = 0; j < this.samplerLocations.size(); j++) {
            if (this.samplerMap.get(this.samplerNames.get(j)) != null) {
                GlStateManager._activeTexture(33984 + j);
                GlStateManager._bindTexture(0);
            }
        }

        GlStateManager._activeTexture(i);
    }

    public void apply() {
        RenderSystem.assertOnRenderThread();
        this.dirty = false;
        lastAppliedShader = this;
        if (this.programId != lastProgramId) {
            ProgramManager.glUseProgram(this.programId);
            lastProgramId = this.programId;
        }

        int i = GlStateManager._getActiveTexture();

        for (int j = 0; j < this.samplerLocations.size(); j++) {
            String string = this.samplerNames.get(j);
            if (this.samplerMap.get(string) != null) {
                int k = Uniform.glGetUniformLocation(this.programId, string);
                Uniform.uploadInteger(k, j);
                RenderSystem.activeTexture(33984 + j);
                Object object = this.samplerMap.get(string);
                int l = -1;
                if (object instanceof RenderTarget) {
                    l = ((RenderTarget)object).getColorTextureId();
                } else if (object instanceof AbstractTexture) {
                    l = ((AbstractTexture)object).getId();
                } else if (object instanceof Integer) {
                    l = (Integer)object;
                }

                if (l != -1) {
                    RenderSystem.bindTexture(l);
                }
            }
        }

        GlStateManager._activeTexture(i);

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
            int k = intList.getInt(i);
            this.samplerNames.remove(k);
        }

        for (Uniform uniform : this.uniforms) {
            String string2 = uniform.getName();
            int l = Uniform.glGetUniformLocation(this.programId, string2);
            if (l == -1) {
                LOGGER.warn("Shader {} could not find uniform named {} in the specified shader program.", this.name, string2);
            } else {
                this.uniformLocations.add(l);
                uniform.setLocation(l);
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

    public void setSampler(String name, Object textureId) {
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
            uniform.set(Arrays.copyOfRange(fs, 0, j));
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
        this.fragmentProgram.attachToShader(this);
        this.vertexProgram.attachToShader(this);
    }

    public VertexFormat getVertexFormat() {
        return this.vertexFormat;
    }

    public String getName() {
        return this.name;
    }

    @Override
    public int getId() {
        return this.programId;
    }

    public void setDefaultUniforms(VertexFormat.Mode mode, Matrix4f frustumMatrix, Matrix4f projectionMatrix, Window window) {
        for (int i = 0; i < 12; i++) {
            int j = RenderSystem.getShaderTexture(i);
            this.setSampler("Sampler" + i, j);
        }

        if (this.MODEL_VIEW_MATRIX != null) {
            this.MODEL_VIEW_MATRIX.set(frustumMatrix);
        }

        if (this.PROJECTION_MATRIX != null) {
            this.PROJECTION_MATRIX.set(projectionMatrix);
        }

        if (this.COLOR_MODULATOR != null) {
            this.COLOR_MODULATOR.set(RenderSystem.getShaderColor());
        }

        if (this.GLINT_ALPHA != null) {
            this.GLINT_ALPHA.set(RenderSystem.getShaderGlintAlpha());
        }

        if (this.FOG_START != null) {
            this.FOG_START.set(RenderSystem.getShaderFogStart());
        }

        if (this.FOG_END != null) {
            this.FOG_END.set(RenderSystem.getShaderFogEnd());
        }

        if (this.FOG_COLOR != null) {
            this.FOG_COLOR.set(RenderSystem.getShaderFogColor());
        }

        if (this.FOG_SHAPE != null) {
            this.FOG_SHAPE.set(RenderSystem.getShaderFogShape().getIndex());
        }

        if (this.TEXTURE_MATRIX != null) {
            this.TEXTURE_MATRIX.set(RenderSystem.getTextureMatrix());
        }

        if (this.GAME_TIME != null) {
            this.GAME_TIME.set(RenderSystem.getShaderGameTime());
        }

        if (this.SCREEN_SIZE != null) {
            this.SCREEN_SIZE.set((float)window.getWidth(), (float)window.getHeight());
        }

        if (this.LINE_WIDTH != null && (mode == VertexFormat.Mode.LINES || mode == VertexFormat.Mode.LINE_STRIP)) {
            this.LINE_WIDTH.set(RenderSystem.getShaderLineWidth());
        }

        RenderSystem.setupShaderLights(this);
    }
}
