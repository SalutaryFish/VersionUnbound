package com.mojang.blaze3d.platform;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.mojang.blaze3d.DontObfuscate;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import net.minecraft.client.renderer.GameRenderer;
import org.lwjgl.Version;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWErrorCallbackI;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;

@DontObfuscate
public class GLX {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static String cpuInfo;

    public static String getOpenGLVersionString() {
        RenderSystem.assertOnRenderThread();
        return GLFW.glfwGetCurrentContext() == 0L
            ? "NO CONTEXT"
            : GlStateManager._getString(7937) + " GL version " + GlStateManager._getString(7938) + ", " + GlStateManager._getString(7936);
    }

    public static int _getRefreshRate(Window window) {
        RenderSystem.assertOnRenderThread();
        long l = GLFW.glfwGetWindowMonitor(window.getWindow());
        if (l == 0L) {
            l = GLFW.glfwGetPrimaryMonitor();
        }

        GLFWVidMode gLFWVidMode = l == 0L ? null : GLFW.glfwGetVideoMode(l);
        return gLFWVidMode == null ? 0 : gLFWVidMode.refreshRate();
    }

    public static String _getLWJGLVersion() {
        return Version.getVersion();
    }

    public static LongSupplier _initGlfw() {
        Window.checkGlfwError((error, description) -> {
            throw new IllegalStateException(String.format(Locale.ROOT, "GLFW error before init: [0x%X]%s", error, description));
        });
        List<String> list = Lists.newArrayList();
        GLFWErrorCallback gLFWErrorCallback = GLFW.glfwSetErrorCallback((error, description) -> {
            String stringx = description == 0L ? "" : MemoryUtil.memUTF8(description);
            list.add(String.format(Locale.ROOT, "GLFW error during init: [0x%X]%s", error, stringx));
        });
        if (!GLFW.glfwInit()) {
            throw new IllegalStateException("Failed to initialize GLFW, errors: " + Joiner.on(",").join(list));
        }

        LongSupplier longSupplier = () -> (long)(GLFW.glfwGetTime() * 1.0E9);

        for (String string : list) {
            LOGGER.error("GLFW error collected during initialization: {}", string);
        }

        RenderSystem.setErrorCallback(gLFWErrorCallback);
        return longSupplier;
    }

    public static void _setGlfwErrorCallback(GLFWErrorCallbackI errorCallback) {
        GLFWErrorCallback gLFWErrorCallback = GLFW.glfwSetErrorCallback(errorCallback);
        if (gLFWErrorCallback != null) {
            gLFWErrorCallback.free();
        }
    }

    public static boolean _shouldClose(Window window) {
        return GLFW.glfwWindowShouldClose(window.getWindow());
    }

    public static void _init(int debugVerbosity, boolean synchronous) {
        try {
            CentralProcessor centralProcessor = new SystemInfo().getHardware().getProcessor();
            cpuInfo = String.format(Locale.ROOT, "%dx %s", centralProcessor.getLogicalProcessorCount(), centralProcessor.getProcessorIdentifier().getName())
                .replaceAll("\\s+", " ");
        } catch (Throwable var3) {
        }

        GlDebug.enableDebugCallback(debugVerbosity, synchronous);
    }

    public static String _getCpuInfo() {
        return cpuInfo == null ? "<unknown>" : cpuInfo;
    }

    public static void _renderCrosshair(int lineLength, boolean renderX, boolean renderY, boolean renderZ) {
        if (renderX || renderY || renderZ) {
            RenderSystem.assertOnRenderThread();
            GlStateManager._depthMask(false);
            GlStateManager._disableCull();
            RenderSystem.setShader(GameRenderer::getRendertypeLinesShader);
            Tesselator tesselator = RenderSystem.renderThreadTesselator();
            BufferBuilder bufferBuilder = tesselator.begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL);
            RenderSystem.lineWidth(4.0F);
            if (renderX) {
                bufferBuilder.addVertex(0.0F, 0.0F, 0.0F).setColor(-16777216).setNormal(1.0F, 0.0F, 0.0F);
                bufferBuilder.addVertex(lineLength, 0.0F, 0.0F).setColor(-16777216).setNormal(1.0F, 0.0F, 0.0F);
            }

            if (renderY) {
                bufferBuilder.addVertex(0.0F, 0.0F, 0.0F).setColor(-16777216).setNormal(0.0F, 1.0F, 0.0F);
                bufferBuilder.addVertex(0.0F, lineLength, 0.0F).setColor(-16777216).setNormal(0.0F, 1.0F, 0.0F);
            }

            if (renderZ) {
                bufferBuilder.addVertex(0.0F, 0.0F, 0.0F).setColor(-16777216).setNormal(0.0F, 0.0F, 1.0F);
                bufferBuilder.addVertex(0.0F, 0.0F, lineLength).setColor(-16777216).setNormal(0.0F, 0.0F, 1.0F);
            }

            BufferUploader.drawWithShader(bufferBuilder.buildOrThrow());
            RenderSystem.lineWidth(2.0F);
            bufferBuilder = tesselator.begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL);
            if (renderX) {
                bufferBuilder.addVertex(0.0F, 0.0F, 0.0F).setColor(-65536).setNormal(1.0F, 0.0F, 0.0F);
                bufferBuilder.addVertex(lineLength, 0.0F, 0.0F).setColor(-65536).setNormal(1.0F, 0.0F, 0.0F);
            }

            if (renderY) {
                bufferBuilder.addVertex(0.0F, 0.0F, 0.0F).setColor(-16711936).setNormal(0.0F, 1.0F, 0.0F);
                bufferBuilder.addVertex(0.0F, lineLength, 0.0F).setColor(-16711936).setNormal(0.0F, 1.0F, 0.0F);
            }

            if (renderZ) {
                bufferBuilder.addVertex(0.0F, 0.0F, 0.0F).setColor(-8421377).setNormal(0.0F, 0.0F, 1.0F);
                bufferBuilder.addVertex(0.0F, 0.0F, lineLength).setColor(-8421377).setNormal(0.0F, 0.0F, 1.0F);
            }

            BufferUploader.drawWithShader(bufferBuilder.buildOrThrow());
            RenderSystem.lineWidth(1.0F);
            GlStateManager._enableCull();
            GlStateManager._depthMask(true);
        }
    }

    public static <T> T make(Supplier<T> supplier) {
        return supplier.get();
    }

    public static <T> T make(T value, Consumer<T> consumer) {
        consumer.accept(value);
        return value;
    }
}
