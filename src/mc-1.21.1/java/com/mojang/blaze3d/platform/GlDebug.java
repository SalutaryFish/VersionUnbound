package com.mojang.blaze3d.platform;

import com.google.common.collect.EvictingQueue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.Queue;
import javax.annotation.Nullable;
import org.lwjgl.opengl.ARBDebugOutput;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.opengl.GLDebugMessageARBCallback;
import org.lwjgl.opengl.GLDebugMessageCallback;
import org.lwjgl.opengl.KHRDebug;
import org.slf4j.Logger;

public class GlDebug {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int CIRCULAR_LOG_SIZE = 10;
    private static final Queue<GlDebug.LogEntry> MESSAGE_BUFFER = EvictingQueue.create(10);
    @Nullable
    private static volatile GlDebug.LogEntry lastEntry;
    private static final List<Integer> DEBUG_LEVELS = ImmutableList.of(37190, 37191, 37192, 33387);
    private static final List<Integer> DEBUG_LEVELS_ARB = ImmutableList.of(37190, 37191, 37192);
    private static boolean debugEnabled;

    private static String printUnknownToken(int token) {
        return "Unknown (0x" + Integer.toHexString(token).toUpperCase() + ")";
    }

    public static String sourceToString(int source) {
        switch (source) {
            case 33350:
                return "API";
            case 33351:
                return "WINDOW SYSTEM";
            case 33352:
                return "SHADER COMPILER";
            case 33353:
                return "THIRD PARTY";
            case 33354:
                return "APPLICATION";
            case 33355:
                return "OTHER";
            default:
                return printUnknownToken(source);
        }
    }

    public static String typeToString(int type) {
        switch (type) {
            case 33356:
                return "ERROR";
            case 33357:
                return "DEPRECATED BEHAVIOR";
            case 33358:
                return "UNDEFINED BEHAVIOR";
            case 33359:
                return "PORTABILITY";
            case 33360:
                return "PERFORMANCE";
            case 33361:
                return "OTHER";
            case 33384:
                return "MARKER";
            default:
                return printUnknownToken(type);
        }
    }

    public static String severityToString(int severity) {
        switch (severity) {
            case 33387:
                return "NOTIFICATION";
            case 37190:
                return "HIGH";
            case 37191:
                return "MEDIUM";
            case 37192:
                return "LOW";
            default:
                return printUnknownToken(severity);
        }
    }

    /**
     * @param source        The GLenum source represented as an ordinal integer.
     * @param type          The GLenum type represented as an ordinal integer.
     * @param id            The unbounded integer id of the message callback.
     * @param severity      The GLenum severity represented as an ordinal integer.
     * @param messageLength The {@link org.lwjgl.opengl.GLDebugMessageCallback} length
     *                      argument.
     * @param message       The {@link org.lwjgl.opengl.GLDebugMessageCallback}
     *                      message argument
     * @param userParam     A user supplied pointer that will be passed on each
     *                      invocation of callback.
     */
    private static void printDebugLog(int source, int type, int id, int severity, int messageLength, long message, long userParam) {
        String string = GLDebugMessageCallback.getMessage(messageLength, message);
        GlDebug.LogEntry logEntry;
        synchronized (MESSAGE_BUFFER) {
            logEntry = lastEntry;
            if (logEntry != null && logEntry.isSame(source, type, id, severity, string)) {
                logEntry.count++;
            } else {
                logEntry = new GlDebug.LogEntry(source, type, id, severity, string);
                MESSAGE_BUFFER.add(logEntry);
                lastEntry = logEntry;
            }
        }

        LOGGER.info("OpenGL debug message: {}", logEntry);
    }

    public static List<String> getLastOpenGlDebugMessages() {
        synchronized (MESSAGE_BUFFER) {
            List<String> list = Lists.newArrayListWithCapacity(MESSAGE_BUFFER.size());

            for (GlDebug.LogEntry logEntry : MESSAGE_BUFFER) {
                list.add(logEntry + " x " + logEntry.count);
            }

            return list;
        }
    }

    public static boolean isDebugEnabled() {
        return debugEnabled;
    }

    public static void enableDebugCallback(int debugVerbosity, boolean synchronous) {
        if (debugVerbosity > 0) {
            GLCapabilities gLCapabilities = GL.getCapabilities();
            if (gLCapabilities.GL_KHR_debug) {
                debugEnabled = true;
                GL11.glEnable(37600);
                if (synchronous) {
                    GL11.glEnable(33346);
                }

                for (int i = 0; i < DEBUG_LEVELS.size(); i++) {
                    boolean bl = i < debugVerbosity;
                    KHRDebug.glDebugMessageControl(4352, 4352, DEBUG_LEVELS.get(i), (int[])null, bl);
                }

                KHRDebug.glDebugMessageCallback(GLX.make(GLDebugMessageCallback.create(GlDebug::printDebugLog), DebugMemoryUntracker::untrack), 0L);
            } else if (gLCapabilities.GL_ARB_debug_output) {
                debugEnabled = true;
                if (synchronous) {
                    GL11.glEnable(33346);
                }

                for (int i = 0; i < DEBUG_LEVELS_ARB.size(); i++) {
                    boolean bl = i < debugVerbosity;
                    ARBDebugOutput.glDebugMessageControlARB(4352, 4352, DEBUG_LEVELS_ARB.get(i), (int[])null, bl);
                }

                ARBDebugOutput.glDebugMessageCallbackARB(GLX.make(GLDebugMessageARBCallback.create(GlDebug::printDebugLog), DebugMemoryUntracker::untrack), 0L);
            }
        }
    }

    static class LogEntry {
        private final int id;
        private final int source;
        private final int type;
        private final int severity;
        private final String message;
        int count = 1;

        LogEntry(int source, int type, int id, int severity, String message) {
            this.id = id;
            this.source = source;
            this.type = type;
            this.severity = severity;
            this.message = message;
        }

        boolean isSame(int source, int type, int id, int severity, String message) {
            return type == this.type && source == this.source && id == this.id && severity == this.severity && message.equals(this.message);
        }

        @Override
        public String toString() {
            return "id="
                + this.id
                + ", source="
                + GlDebug.sourceToString(this.source)
                + ", type="
                + GlDebug.typeToString(this.type)
                + ", severity="
                + GlDebug.severityToString(this.severity)
                + ", message='"
                + this.message
                + "'";
        }
    }
}
