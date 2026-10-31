package net.minecraft.client.resources.metadata.animation;

import com.google.common.collect.Lists;
import java.util.List;

public class AnimationMetadataSection {
    public static final AnimationMetadataSectionSerializer SERIALIZER = new AnimationMetadataSectionSerializer();
    public static final String SECTION_NAME = "animation";
    public static final int DEFAULT_FRAME_TIME = 1;
    public static final int UNKNOWN_SIZE = -1;
    public static final AnimationMetadataSection EMPTY = new AnimationMetadataSection(Lists.newArrayList(), -1, -1, 1, false) {
        @Override
        public FrameSize calculateFrameSize(int i, int j) {
            return new FrameSize(i, j);
        }
    };
    private final List<AnimationFrame> frames;
    private final int frameWidth;
    private final int frameHeight;
    private final int defaultFrameTime;
    private final boolean interpolatedFrames;

    public AnimationMetadataSection(List<AnimationFrame> frames, int frameWidth, int frameHeight, int defaultFrameTime, boolean interpolatedFrames) {
        this.frames = frames;
        this.frameWidth = frameWidth;
        this.frameHeight = frameHeight;
        this.defaultFrameTime = defaultFrameTime;
        this.interpolatedFrames = interpolatedFrames;
    }

    public FrameSize calculateFrameSize(int width, int height) {
        if (this.frameWidth != -1) {
            return this.frameHeight != -1 ? new FrameSize(this.frameWidth, this.frameHeight) : new FrameSize(this.frameWidth, height);
        }

        if (this.frameHeight != -1) {
            return new FrameSize(width, this.frameHeight);
        }

        int i = Math.min(width, height);
        return new FrameSize(i, i);
    }

    public int getDefaultFrameTime() {
        return this.defaultFrameTime;
    }

    public boolean isInterpolatedFrames() {
        return this.interpolatedFrames;
    }

    public void forEachFrame(AnimationMetadataSection.FrameOutput output) {
        for (AnimationFrame animationFrame : this.frames) {
            output.accept(animationFrame.getIndex(), animationFrame.getTime(this.defaultFrameTime));
        }
    }

    @FunctionalInterface
    public interface FrameOutput {
        void accept(int var1, int var2);
    }
}
