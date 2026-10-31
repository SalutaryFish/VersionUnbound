package com.mojang.blaze3d.platform;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.glfw.GLFWVidMode.Buffer;

public final class VideoMode {
    private final int width;
    private final int height;
    private final int redBits;
    private final int greenBits;
    private final int blueBits;
    private final int refreshRate;
    private static final Pattern PATTERN = Pattern.compile("(\\d+)x(\\d+)(?:@(\\d+)(?::(\\d+))?)?");

    public VideoMode(int width, int height, int redBits, int greenBits, int blueBits, int refreshRate) {
        this.width = width;
        this.height = height;
        this.redBits = redBits;
        this.greenBits = greenBits;
        this.blueBits = blueBits;
        this.refreshRate = refreshRate;
    }

    public VideoMode(Buffer bufferVideoMode) {
        this.width = bufferVideoMode.width();
        this.height = bufferVideoMode.height();
        this.redBits = bufferVideoMode.redBits();
        this.greenBits = bufferVideoMode.greenBits();
        this.blueBits = bufferVideoMode.blueBits();
        this.refreshRate = bufferVideoMode.refreshRate();
    }

    public VideoMode(GLFWVidMode glfwVideoMode) {
        this.width = glfwVideoMode.width();
        this.height = glfwVideoMode.height();
        this.redBits = glfwVideoMode.redBits();
        this.greenBits = glfwVideoMode.greenBits();
        this.blueBits = glfwVideoMode.blueBits();
        this.refreshRate = glfwVideoMode.refreshRate();
    }

    public int getWidth() {
        return this.width;
    }

    public int getHeight() {
        return this.height;
    }

    public int getRedBits() {
        return this.redBits;
    }

    public int getGreenBits() {
        return this.greenBits;
    }

    public int getBlueBits() {
        return this.blueBits;
    }

    public int getRefreshRate() {
        return this.refreshRate;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        } else if (other != null && this.getClass() == other.getClass()) {
            VideoMode videoMode = (VideoMode)other;
            return this.width == videoMode.width
                && this.height == videoMode.height
                && this.redBits == videoMode.redBits
                && this.greenBits == videoMode.greenBits
                && this.blueBits == videoMode.blueBits
                && this.refreshRate == videoMode.refreshRate;
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.width, this.height, this.redBits, this.greenBits, this.blueBits, this.refreshRate);
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "%sx%s@%s (%sbit)", this.width, this.height, this.refreshRate, this.redBits + this.greenBits + this.blueBits);
    }

    public static Optional<VideoMode> read(@Nullable String videoMode) {
        if (videoMode == null) {
            return Optional.empty();
        }

        try {
            Matcher matcher = PATTERN.matcher(videoMode);
            if (matcher.matches()) {
                int i = Integer.parseInt(matcher.group(1));
                int j = Integer.parseInt(matcher.group(2));
                String string = matcher.group(3);
                int k;
                if (string == null) {
                    k = 60;
                } else {
                    k = Integer.parseInt(string);
                }

                String string2 = matcher.group(4);
                int l;
                if (string2 == null) {
                    l = 24;
                } else {
                    l = Integer.parseInt(string2);
                }

                int m = l / 3;
                return Optional.of(new VideoMode(i, j, m, m, m, k));
            }
        } catch (Exception var9) {
        }

        return Optional.empty();
    }

    public String write() {
        return String.format(Locale.ROOT, "%sx%s@%s:%s", this.width, this.height, this.refreshRate, this.redBits + this.greenBits + this.blueBits);
    }
}
