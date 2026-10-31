package com.mojang.blaze3d.platform;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.main.SilentInitException;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.IoSupplier;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.Callbacks;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.glfw.GLFWImage.Buffer;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import org.slf4j.Logger;

public final class Window implements AutoCloseable {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final int BASE_WIDTH = 320;
    public static final int BASE_HEIGHT = 240;
    private final GLFWErrorCallback defaultErrorCallback = GLFWErrorCallback.create(this::defaultErrorCallback);
    private final WindowEventHandler eventHandler;
    private final ScreenManager screenManager;
    private final long window;
    private int windowedX;
    private int windowedY;
    private int windowedWidth;
    private int windowedHeight;
    private Optional<VideoMode> preferredFullscreenVideoMode;
    private boolean fullscreen;
    private boolean actuallyFullscreen;
    private int x;
    private int y;
    private int width;
    private int height;
    private int framebufferWidth;
    private int framebufferHeight;
    private int guiScaledWidth;
    private int guiScaledHeight;
    private double guiScale;
    private String errorSection = "";
    private boolean dirty;
    private int framerateLimit;
    private boolean vsync;

    public Window(
        WindowEventHandler eventHandler, ScreenManager screenManager, DisplayData displayData, @Nullable String preferredFullscreenVideoMode, String title
    ) {
        this.screenManager = screenManager;
        this.setBootErrorCallback();
        this.setErrorSection("Pre startup");
        this.eventHandler = eventHandler;
        Optional<VideoMode> optional = VideoMode.read(preferredFullscreenVideoMode);
        if (optional.isPresent()) {
            this.preferredFullscreenVideoMode = optional;
        } else if (displayData.fullscreenWidth.isPresent() && displayData.fullscreenHeight.isPresent()) {
            this.preferredFullscreenVideoMode = Optional.of(
                new VideoMode(displayData.fullscreenWidth.getAsInt(), displayData.fullscreenHeight.getAsInt(), 8, 8, 8, 60)
            );
        } else {
            this.preferredFullscreenVideoMode = Optional.empty();
        }

        this.actuallyFullscreen = this.fullscreen = displayData.isFullscreen;
        Monitor monitor = screenManager.getMonitor(GLFW.glfwGetPrimaryMonitor());
        this.windowedWidth = this.width = displayData.width > 0 ? displayData.width : 1;
        this.windowedHeight = this.height = displayData.height > 0 ? displayData.height : 1;
        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(139265, 196609);
        GLFW.glfwWindowHint(139275, 221185);
        GLFW.glfwWindowHint(139266, 3);
        GLFW.glfwWindowHint(139267, 2);
        GLFW.glfwWindowHint(139272, 204801);
        GLFW.glfwWindowHint(139270, 1);
        this.window = GLFW.glfwCreateWindow(this.width, this.height, title, this.fullscreen && monitor != null ? monitor.getMonitor() : 0L, 0L);
        if (monitor != null) {
            VideoMode videoMode = monitor.getPreferredVidMode(this.fullscreen ? this.preferredFullscreenVideoMode : Optional.empty());
            this.windowedX = this.x = monitor.getX() + videoMode.getWidth() / 2 - this.width / 2;
            this.windowedY = this.y = monitor.getY() + videoMode.getHeight() / 2 - this.height / 2;
        } else {
            int[] is = new int[1];
            int[] js = new int[1];
            GLFW.glfwGetWindowPos(this.window, is, js);
            this.windowedX = this.x = is[0];
            this.windowedY = this.y = js[0];
        }

        GLFW.glfwMakeContextCurrent(this.window);
        GL.createCapabilities();
        int i = RenderSystem.maxSupportedTextureSize();
        GLFW.glfwSetWindowSizeLimits(this.window, -1, -1, i, i);
        this.setMode();
        this.refreshFramebufferSize();
        GLFW.glfwSetFramebufferSizeCallback(this.window, this::onFramebufferResize);
        GLFW.glfwSetWindowPosCallback(this.window, this::onMove);
        GLFW.glfwSetWindowSizeCallback(this.window, this::onResize);
        GLFW.glfwSetWindowFocusCallback(this.window, this::onFocus);
        GLFW.glfwSetCursorEnterCallback(this.window, this::onEnter);
    }

    public static String getPlatform() {
        int i = GLFW.glfwGetPlatform();

        return switch (i) {
            case 0 -> "<error>";
            case 393217 -> "win32";
            case 393218 -> "cocoa";
            case 393219 -> "wayland";
            case 393220 -> "x11";
            case 393221 -> "null";
            default -> String.format(Locale.ROOT, "unknown (%08X)", i);
        };
    }

    public int getRefreshRate() {
        RenderSystem.assertOnRenderThread();
        return GLX._getRefreshRate(this);
    }

    public boolean shouldClose() {
        return GLX._shouldClose(this);
    }

    public static void checkGlfwError(BiConsumer<Integer, String> errorConsumer) {
        try (MemoryStack memoryStack = MemoryStack.stackPush()) {
            PointerBuffer pointerBuffer = memoryStack.mallocPointer(1);
            int i = GLFW.glfwGetError(pointerBuffer);
            if (i != 0) {
                long l = pointerBuffer.get();
                String string = l == 0L ? "" : MemoryUtil.memUTF8(l);
                errorConsumer.accept(i, string);
            }
        }
    }

    public void setIcon(PackResources packResources, IconSet iconSet) throws IOException {
        int i = GLFW.glfwGetPlatform();
        switch (i) {
            case 393217:
            case 393220:
                List<IoSupplier<InputStream>> list = iconSet.getStandardIcons(packResources);
                List<ByteBuffer> list2 = new ArrayList<>(list.size());

                try (MemoryStack memoryStack = MemoryStack.stackPush()) {
                    Buffer buffer = GLFWImage.malloc(list.size(), memoryStack);

                    for (int j = 0; j < list.size(); j++) {
                        try (NativeImage nativeImage = NativeImage.read(list.get(j).get())) {
                            ByteBuffer byteBuffer = MemoryUtil.memAlloc(nativeImage.getWidth() * nativeImage.getHeight() * 4);
                            list2.add(byteBuffer);
                            byteBuffer.asIntBuffer().put(nativeImage.getPixelsRGBA());
                            buffer.position(j);
                            buffer.width(nativeImage.getWidth());
                            buffer.height(nativeImage.getHeight());
                            buffer.pixels(byteBuffer);
                        }
                    }

                    GLFW.glfwSetWindowIcon(this.window, buffer.position(0));
                    break;
                } finally {
                    list2.forEach(MemoryUtil::memFree);
                }
            case 393218:
                MacosUtil.loadIcon(iconSet.getMacIcon(packResources));
            case 393219:
            case 393221:
                break;
            default:
                LOGGER.warn("Not setting icon for unrecognized platform: {}", i);
        }
    }

    public void setErrorSection(String errorSection) {
        this.errorSection = errorSection;
    }

    private void setBootErrorCallback() {
        GLFW.glfwSetErrorCallback(Window::bootCrash);
    }

    private static void bootCrash(int error, long description) {
        String string = "GLFW error " + error + ": " + MemoryUtil.memUTF8(description);
        TinyFileDialogs.tinyfd_messageBox(
            "Minecraft", string + ".\n\nPlease make sure you have up-to-date drivers (see aka.ms/mcdriver for instructions).", "ok", "error", false
        );
        throw new Window.WindowInitFailed(string);
    }

    public void defaultErrorCallback(int error, long description) {
        RenderSystem.assertOnRenderThread();
        String string = MemoryUtil.memUTF8(description);
        LOGGER.error("########## GL ERROR ##########");
        LOGGER.error("@ {}", this.errorSection);
        LOGGER.error("{}: {}", error, string);
    }

    public void setDefaultErrorCallback() {
        GLFWErrorCallback gLFWErrorCallback = GLFW.glfwSetErrorCallback(this.defaultErrorCallback);
        if (gLFWErrorCallback != null) {
            gLFWErrorCallback.free();
        }
    }

    public void updateVsync(boolean vsync) {
        RenderSystem.assertOnRenderThreadOrInit();
        this.vsync = vsync;
        GLFW.glfwSwapInterval(vsync ? 1 : 0);
    }

    @Override
    public void close() {
        RenderSystem.assertOnRenderThread();
        Callbacks.glfwFreeCallbacks(this.window);
        this.defaultErrorCallback.close();
        GLFW.glfwDestroyWindow(this.window);
        GLFW.glfwTerminate();
    }

    private void onMove(long window, int x, int y) {
        this.x = x;
        this.y = y;
    }

    private void onFramebufferResize(long window, int framebufferWidth, int framebufferHeight) {
        if (window == this.window) {
            int i = this.getWidth();
            int j = this.getHeight();
            if (framebufferWidth != 0 && framebufferHeight != 0) {
                this.framebufferWidth = framebufferWidth;
                this.framebufferHeight = framebufferHeight;
                if (this.getWidth() != i || this.getHeight() != j) {
                    this.eventHandler.resizeDisplay();
                }
            }
        }
    }

    private void refreshFramebufferSize() {
        int[] is = new int[1];
        int[] js = new int[1];
        GLFW.glfwGetFramebufferSize(this.window, is, js);
        this.framebufferWidth = is[0] > 0 ? is[0] : 1;
        this.framebufferHeight = js[0] > 0 ? js[0] : 1;
    }

    private void onResize(long window, int width, int height) {
        this.width = width;
        this.height = height;
    }

    private void onFocus(long window, boolean hasFocus) {
        if (window == this.window) {
            this.eventHandler.setWindowActive(hasFocus);
        }
    }

    /**
     * @param cursorEntered {@code true} if the cursor entered the window, {@code
     *                      false} if the cursor left
     */
    private void onEnter(long window, boolean cursorEntered) {
        if (cursorEntered) {
            this.eventHandler.cursorEntered();
        }
    }

    public void setFramerateLimit(int limit) {
        this.framerateLimit = limit;
    }

    public int getFramerateLimit() {
        return this.framerateLimit;
    }

    public void updateDisplay() {
        RenderSystem.flipFrame(this.window);
        if (this.fullscreen != this.actuallyFullscreen) {
            this.actuallyFullscreen = this.fullscreen;
            this.updateFullscreen(this.vsync);
        }
    }

    public Optional<VideoMode> getPreferredFullscreenVideoMode() {
        return this.preferredFullscreenVideoMode;
    }

    public void setPreferredFullscreenVideoMode(Optional<VideoMode> preferredFullscreenVideoMode) {
        boolean bl = !preferredFullscreenVideoMode.equals(this.preferredFullscreenVideoMode);
        this.preferredFullscreenVideoMode = preferredFullscreenVideoMode;
        if (bl) {
            this.dirty = true;
        }
    }

    public void changeFullscreenVideoMode() {
        if (this.fullscreen && this.dirty) {
            this.dirty = false;
            this.setMode();
            this.eventHandler.resizeDisplay();
        }
    }

    private void setMode() {
        boolean bl = GLFW.glfwGetWindowMonitor(this.window) != 0L;
        if (this.fullscreen) {
            Monitor monitor = this.screenManager.findBestMonitor(this);
            if (monitor == null) {
                LOGGER.warn("Failed to find suitable monitor for fullscreen mode");
                this.fullscreen = false;
            } else {
                if (Minecraft.ON_OSX) {
                    MacosUtil.exitNativeFullscreen(this.window);
                }

                VideoMode videoMode = monitor.getPreferredVidMode(this.preferredFullscreenVideoMode);
                if (!bl) {
                    this.windowedX = this.x;
                    this.windowedY = this.y;
                    this.windowedWidth = this.width;
                    this.windowedHeight = this.height;
                }

                this.x = 0;
                this.y = 0;
                this.width = videoMode.getWidth();
                this.height = videoMode.getHeight();
                GLFW.glfwSetWindowMonitor(this.window, monitor.getMonitor(), this.x, this.y, this.width, this.height, videoMode.getRefreshRate());
                if (Minecraft.ON_OSX) {
                    MacosUtil.clearResizableBit(this.window);
                }
            }
        } else {
            this.x = this.windowedX;
            this.y = this.windowedY;
            this.width = this.windowedWidth;
            this.height = this.windowedHeight;
            GLFW.glfwSetWindowMonitor(this.window, 0L, this.x, this.y, this.width, this.height, -1);
        }
    }

    public void toggleFullScreen() {
        this.fullscreen = !this.fullscreen;
    }

    public void setWindowed(int windowedWidth, int windowedHeight) {
        this.windowedWidth = windowedWidth;
        this.windowedHeight = windowedHeight;
        this.fullscreen = false;
        this.setMode();
    }

    private void updateFullscreen(boolean vsyncEnabled) {
        RenderSystem.assertOnRenderThread();

        try {
            this.setMode();
            this.eventHandler.resizeDisplay();
            this.updateVsync(vsyncEnabled);
            this.updateDisplay();
        } catch (Exception exception) {
            LOGGER.error("Couldn't toggle fullscreen", exception);
        }
    }

    public int calculateScale(int guiScale, boolean forceUnicode) {
        int i = 1;

        while (
            i != guiScale
                && i < this.framebufferWidth
                && i < this.framebufferHeight
                && this.framebufferWidth / (i + 1) >= 320
                && this.framebufferHeight / (i + 1) >= 240
        ) {
            i++;
        }

        if (forceUnicode && i % 2 != 0) {
            i++;
        }

        return i;
    }

    public void setGuiScale(double scaleFactor) {
        this.guiScale = scaleFactor;
        int i = (int)(this.framebufferWidth / scaleFactor);
        this.guiScaledWidth = this.framebufferWidth / scaleFactor > i ? i + 1 : i;
        int j = (int)(this.framebufferHeight / scaleFactor);
        this.guiScaledHeight = this.framebufferHeight / scaleFactor > j ? j + 1 : j;
    }

    public void setTitle(String title) {
        GLFW.glfwSetWindowTitle(this.window, title);
    }

    public long getWindow() {
        return this.window;
    }

    public boolean isFullscreen() {
        return this.fullscreen;
    }

    public int getWidth() {
        return this.framebufferWidth;
    }

    public int getHeight() {
        return this.framebufferHeight;
    }

    public void setWidth(int framebufferWidth) {
        this.framebufferWidth = framebufferWidth;
    }

    public void setHeight(int framebufferHeight) {
        this.framebufferHeight = framebufferHeight;
    }

    public int getScreenWidth() {
        return this.width;
    }

    public int getScreenHeight() {
        return this.height;
    }

    public int getGuiScaledWidth() {
        return this.guiScaledWidth;
    }

    public int getGuiScaledHeight() {
        return this.guiScaledHeight;
    }

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }

    public double getGuiScale() {
        return this.guiScale;
    }

    @Nullable
    public Monitor findBestMonitor() {
        return this.screenManager.findBestMonitor(this);
    }

    public void updateRawMouseInput(boolean enableRawMouseMotion) {
        InputConstants.updateRawMouseInput(this.window, enableRawMouseMotion);
    }

    public static class WindowInitFailed extends SilentInitException {
        WindowInitFailed(String string) {
            super(string);
        }
    }
}
