package com.mojang.blaze3d.platform;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import javax.annotation.Nullable;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWMonitorCallback;
import org.slf4j.Logger;

public class ScreenManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Long2ObjectMap<Monitor> monitors = new Long2ObjectOpenHashMap<>();
    private final MonitorCreator monitorCreator;

    public ScreenManager(MonitorCreator monitorCreator) {
        this.monitorCreator = monitorCreator;
        GLFW.glfwSetMonitorCallback(this::onMonitorChange);
        PointerBuffer pointerBuffer = GLFW.glfwGetMonitors();
        if (pointerBuffer != null) {
            for (int i = 0; i < pointerBuffer.limit(); i++) {
                long l = pointerBuffer.get(i);
                this.monitors.put(l, monitorCreator.createMonitor(l));
            }
        }
    }

    private void onMonitorChange(long monitorID, int opCode) {
        RenderSystem.assertOnRenderThread();
        if (opCode == 262145) {
            this.monitors.put(monitorID, this.monitorCreator.createMonitor(monitorID));
            LOGGER.debug("Monitor {} connected. Current monitors: {}", monitorID, this.monitors);
        } else if (opCode == 262146) {
            this.monitors.remove(monitorID);
            LOGGER.debug("Monitor {} disconnected. Current monitors: {}", monitorID, this.monitors);
        }
    }

    @Nullable
    public Monitor getMonitor(long monitorID) {
        return this.monitors.get(monitorID);
    }

    @Nullable
    public Monitor findBestMonitor(Window window) {
        long l = GLFW.glfwGetWindowMonitor(window.getWindow());
        if (l != 0L) {
            return this.getMonitor(l);
        }

        int i = window.getX();
        int j = i + window.getScreenWidth();
        int k = window.getY();
        int m = k + window.getScreenHeight();
        int n = -1;
        Monitor monitor = null;
        long o = GLFW.glfwGetPrimaryMonitor();
        LOGGER.debug("Selecting monitor - primary: {}, current monitors: {}", o, this.monitors);

        for (Monitor monitor2 : this.monitors.values()) {
            int p = monitor2.getX();
            int q = p + monitor2.getCurrentMode().getWidth();
            int r = monitor2.getY();
            int s = r + monitor2.getCurrentMode().getHeight();
            int t = clamp(i, p, q);
            int u = clamp(j, p, q);
            int v = clamp(k, r, s);
            int w = clamp(m, r, s);
            int x = Math.max(0, u - t);
            int y = Math.max(0, w - v);
            int z = x * y;
            if (z > n) {
                monitor = monitor2;
                n = z;
            } else if (z == n && o == monitor2.getMonitor()) {
                LOGGER.debug("Primary monitor {} is preferred to monitor {}", monitor2, monitor);
                monitor = monitor2;
            }
        }

        LOGGER.debug("Selected monitor: {}", monitor);
        return monitor;
    }

    public static int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        } else {
            return value > max ? max : value;
        }
    }

    public void shutdown() {
        RenderSystem.assertOnRenderThread();
        GLFWMonitorCallback gLFWMonitorCallback = GLFW.glfwSetMonitorCallback(null);
        if (gLFWMonitorCallback != null) {
            gLFWMonitorCallback.free();
        }
    }
}
