package com.mojang.blaze3d.platform;

import com.google.common.base.Charsets;
import java.nio.ByteBuffer;
import net.minecraft.util.StringDecomposer;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWErrorCallbackI;
import org.lwjgl.system.MemoryUtil;

public class ClipboardManager {
    public static final int FORMAT_UNAVAILABLE = 65545;
    private final ByteBuffer clipboardScratchBuffer = BufferUtils.createByteBuffer(8192);

    public String getClipboard(long window, GLFWErrorCallbackI errorCallback) {
        GLFWErrorCallback gLFWErrorCallback = GLFW.glfwSetErrorCallback(errorCallback);
        String string = GLFW.glfwGetClipboardString(window);
        string = string != null ? StringDecomposer.filterBrokenSurrogates(string) : "";
        GLFWErrorCallback gLFWErrorCallback2 = GLFW.glfwSetErrorCallback(gLFWErrorCallback);
        if (gLFWErrorCallback2 != null) {
            gLFWErrorCallback2.free();
        }

        return string;
    }

    private static void pushClipboard(long window, ByteBuffer buffer, byte[] clipboardContent) {
        buffer.clear();
        buffer.put(clipboardContent);
        buffer.put((byte)0);
        buffer.flip();
        GLFW.glfwSetClipboardString(window, buffer);
    }

    public void setClipboard(long window, String clipboardContent) {
        byte[] bs = clipboardContent.getBytes(Charsets.UTF_8);
        int i = bs.length + 1;
        if (i < this.clipboardScratchBuffer.capacity()) {
            pushClipboard(window, this.clipboardScratchBuffer, bs);
        } else {
            ByteBuffer byteBuffer = MemoryUtil.memAlloc(i);

            try {
                pushClipboard(window, byteBuffer, bs);
            } finally {
                MemoryUtil.memFree(byteBuffer);
            }
        }
    }
}
