package com.mojang.blaze3d.platform;

import com.google.common.collect.Lists;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.glfw.GLFWVidMode.Buffer;

public final class Monitor {
    private final long monitor;
    private final List<VideoMode> videoModes;
    private VideoMode currentMode;
    private int x;
    private int y;

    public Monitor(long monitor) {
        this.monitor = monitor;
        this.videoModes = Lists.newArrayList();
        this.refreshVideoModes();
    }

    public void refreshVideoModes() {
        this.videoModes.clear();
        Buffer buffer = GLFW.glfwGetVideoModes(this.monitor);

        for (int i = buffer.limit() - 1; i >= 0; i--) {
            buffer.position(i);
            VideoMode videoMode = new VideoMode(buffer);
            if (videoMode.getRedBits() >= 8 && videoMode.getGreenBits() >= 8 && videoMode.getBlueBits() >= 8) {
                this.videoModes.add(videoMode);
            }
        }

        int[] is = new int[1];
        int[] js = new int[1];
        GLFW.glfwGetMonitorPos(this.monitor, is, js);
        this.x = is[0];
        this.y = js[0];
        GLFWVidMode gLFWVidMode = GLFW.glfwGetVideoMode(this.monitor);
        this.currentMode = new VideoMode(gLFWVidMode);
    }

    public VideoMode getPreferredVidMode(Optional<VideoMode> videoMode) {
        if (videoMode.isPresent()) {
            VideoMode videoMode2 = videoMode.get();

            for (VideoMode videoMode3 : this.videoModes) {
                if (videoMode3.equals(videoMode2)) {
                    return videoMode3;
                }
            }
        }

        return this.getCurrentMode();
    }

    public int getVideoModeIndex(VideoMode videoMode) {
        return this.videoModes.indexOf(videoMode);
    }

    public VideoMode getCurrentMode() {
        return this.currentMode;
    }

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }

    public VideoMode getMode(int index) {
        return this.videoModes.get(index);
    }

    public int getModeCount() {
        return this.videoModes.size();
    }

    public long getMonitor() {
        return this.monitor;
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "Monitor[%s %sx%s %s]", this.monitor, this.x, this.y, this.currentMode);
    }
}
