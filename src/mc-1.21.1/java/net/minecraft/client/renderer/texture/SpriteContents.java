package net.minecraft.client.renderer.texture;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.IntStream;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceMetadata;
import org.slf4j.Logger;

public class SpriteContents implements Stitcher.Entry, AutoCloseable {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final ResourceLocation name;
    final int width;
    final int height;
    private final NativeImage originalImage;
    NativeImage[] byMipLevel;
    @Nullable
    private final SpriteContents.AnimatedTexture animatedTexture;
    private final ResourceMetadata metadata;

    public SpriteContents(ResourceLocation name, FrameSize frameSize, NativeImage originalImage, ResourceMetadata metadata) {
        this.name = name;
        this.width = frameSize.width();
        this.height = frameSize.height();
        this.metadata = metadata;
        AnimationMetadataSection animationMetadataSection = metadata.getSection(AnimationMetadataSection.SERIALIZER).orElse(AnimationMetadataSection.EMPTY);
        this.animatedTexture = this.createAnimatedTexture(frameSize, originalImage.getWidth(), originalImage.getHeight(), animationMetadataSection);
        this.originalImage = originalImage;
        this.byMipLevel = new NativeImage[]{this.originalImage};
    }

    public void increaseMipLevel(int mipLevel) {
        try {
            this.byMipLevel = MipmapGenerator.generateMipLevels(this.byMipLevel, mipLevel);
        } catch (Throwable throwable) {
            CrashReport crashReport = CrashReport.forThrowable(throwable, "Generating mipmaps for frame");
            CrashReportCategory crashReportCategory = crashReport.addCategory("Sprite being mipmapped");
            crashReportCategory.setDetail("First frame", () -> {
                StringBuilder stringBuilder = new StringBuilder();
                if (stringBuilder.length() > 0) {
                    stringBuilder.append(", ");
                }

                stringBuilder.append(this.originalImage.getWidth()).append("x").append(this.originalImage.getHeight());
                return stringBuilder.toString();
            });
            CrashReportCategory crashReportCategory2 = crashReport.addCategory("Frame being iterated");
            crashReportCategory2.setDetail("Sprite name", this.name);
            crashReportCategory2.setDetail("Sprite size", () -> this.width + " x " + this.height);
            crashReportCategory2.setDetail("Sprite frames", () -> this.getFrameCount() + " frames");
            crashReportCategory2.setDetail("Mipmap levels", mipLevel);
            throw new ReportedException(crashReport);
        }
    }

    private int getFrameCount() {
        return this.animatedTexture != null ? this.animatedTexture.frames.size() : 1;
    }

    @Nullable
    private SpriteContents.AnimatedTexture createAnimatedTexture(FrameSize frameSize, int width, int height, AnimationMetadataSection metadata) {
        int i = width / frameSize.width();
        int j = height / frameSize.height();
        int k = i * j;
        List<SpriteContents.FrameInfo> list = new ArrayList<>();
        metadata.forEachFrame((index, time) -> list.add(new SpriteContents.FrameInfo(index, time)));
        if (list.isEmpty()) {
            for (int l = 0; l < k; l++) {
                list.add(new SpriteContents.FrameInfo(l, metadata.getDefaultFrameTime()));
            }
        } else {
            int l = 0;
            IntSet intSet = new IntOpenHashSet();

            for (Iterator<SpriteContents.FrameInfo> iterator = list.iterator(); iterator.hasNext(); l++) {
                SpriteContents.FrameInfo frameInfo = iterator.next();
                boolean bl = true;
                if (frameInfo.time <= 0) {
                    LOGGER.warn("Invalid frame duration on sprite {} frame {}: {}", this.name, l, frameInfo.time);
                    bl = false;
                }

                if (frameInfo.index < 0 || frameInfo.index >= k) {
                    LOGGER.warn("Invalid frame index on sprite {} frame {}: {}", this.name, l, frameInfo.index);
                    bl = false;
                }

                if (bl) {
                    intSet.add(frameInfo.index);
                } else {
                    iterator.remove();
                }
            }

            int[] is = IntStream.range(0, k).filter(index -> !intSet.contains(index)).toArray();
            if (is.length > 0) {
                LOGGER.warn("Unused frames in sprite {}: {}", this.name, Arrays.toString(is));
            }
        }

        return list.size() <= 1 ? null : new SpriteContents.AnimatedTexture(ImmutableList.copyOf(list), i, metadata.isInterpolatedFrames());
    }

    void upload(int x, int y, int frameX, int frameY, NativeImage[] atlasData) {
        for (int i = 0; i < this.byMipLevel.length; i++) {
            atlasData[i].upload(i, x >> i, y >> i, frameX >> i, frameY >> i, this.width >> i, this.height >> i, this.byMipLevel.length > 1, false);
        }
    }

    @Override
    public int width() {
        return this.width;
    }

    @Override
    public int height() {
        return this.height;
    }

    @Override
    public ResourceLocation name() {
        return this.name;
    }

    public IntStream getUniqueFrames() {
        return this.animatedTexture != null ? this.animatedTexture.getUniqueFrames() : IntStream.of(1);
    }

    @Nullable
    public SpriteTicker createTicker() {
        return this.animatedTexture != null ? this.animatedTexture.createTicker() : null;
    }

    public ResourceMetadata metadata() {
        return this.metadata;
    }

    @Override
    public void close() {
        for (NativeImage nativeImage : this.byMipLevel) {
            nativeImage.close();
        }
    }

    @Override
    public String toString() {
        return "SpriteContents{name=" + this.name + ", frameCount=" + this.getFrameCount() + ", height=" + this.height + ", width=" + this.width + "}";
    }

    public boolean isTransparent(int frame, int x, int y) {
        int i = x;
        int j = y;
        if (this.animatedTexture != null) {
            i += this.animatedTexture.getFrameX(frame) * this.width;
            j += this.animatedTexture.getFrameY(frame) * this.height;
        }

        return (this.originalImage.getPixelRGBA(i, j) >> 24 & 0xFF) == 0;
    }

    public void uploadFirstFrame(int x, int y) {
        if (this.animatedTexture != null) {
            this.animatedTexture.uploadFirstFrame(x, y);
        } else {
            this.upload(x, y, 0, 0, this.byMipLevel);
        }
    }

    class AnimatedTexture {
        final List<SpriteContents.FrameInfo> frames;
        private final int frameRowSize;
        private final boolean interpolateFrames;

        AnimatedTexture(final List<SpriteContents.FrameInfo> frames, final int frameRowSize, final boolean interpolateFrames) {
            this.frames = frames;
            this.frameRowSize = frameRowSize;
            this.interpolateFrames = interpolateFrames;
        }

        int getFrameX(int frameIndex) {
            return frameIndex % this.frameRowSize;
        }

        int getFrameY(int frameIndex) {
            return frameIndex / this.frameRowSize;
        }

        void uploadFrame(int x, int y, int frameIndex) {
            int i = this.getFrameX(frameIndex) * SpriteContents.this.width;
            int j = this.getFrameY(frameIndex) * SpriteContents.this.height;
            SpriteContents.this.upload(x, y, i, j, SpriteContents.this.byMipLevel);
        }

        public SpriteTicker createTicker() {
            return SpriteContents.this.new Ticker(this, this.interpolateFrames ? SpriteContents.this.new InterpolationData() : null);
        }

        public void uploadFirstFrame(int x, int y) {
            this.uploadFrame(x, y, this.frames.get(0).index);
        }

        public IntStream getUniqueFrames() {
            return this.frames.stream().mapToInt(frame -> frame.index).distinct();
        }
    }

    static class FrameInfo {
        final int index;
        final int time;

        FrameInfo(int index, int time) {
            this.index = index;
            this.time = time;
        }
    }

    final class InterpolationData implements AutoCloseable {
        private final NativeImage[] activeFrame = new NativeImage[SpriteContents.this.byMipLevel.length];

        InterpolationData() {
            for (int i = 0; i < this.activeFrame.length; i++) {
                int j = SpriteContents.this.width >> i;
                int k = SpriteContents.this.height >> i;
                this.activeFrame[i] = new NativeImage(j, k, false);
            }
        }

        void uploadInterpolatedFrame(int x, int y, SpriteContents.Ticker ticker) {
            SpriteContents.AnimatedTexture animatedTexture = ticker.animationInfo;
            List<SpriteContents.FrameInfo> list = animatedTexture.frames;
            SpriteContents.FrameInfo frameInfo = list.get(ticker.frame);
            double d = 1.0 - (double)ticker.subFrame / frameInfo.time;
            int i = frameInfo.index;
            int j = list.get((ticker.frame + 1) % list.size()).index;
            if (i != j) {
                for (int k = 0; k < this.activeFrame.length; k++) {
                    int l = SpriteContents.this.width >> k;
                    int m = SpriteContents.this.height >> k;

                    for (int n = 0; n < m; n++) {
                        for (int o = 0; o < l; o++) {
                            int p = this.getPixel(animatedTexture, i, k, o, n);
                            int q = this.getPixel(animatedTexture, j, k, o, n);
                            int r = this.mix(d, p >> 16 & 0xFF, q >> 16 & 0xFF);
                            int s = this.mix(d, p >> 8 & 0xFF, q >> 8 & 0xFF);
                            int t = this.mix(d, p & 0xFF, q & 0xFF);
                            this.activeFrame[k].setPixelRGBA(o, n, p & 0xFF000000 | r << 16 | s << 8 | t);
                        }
                    }
                }

                SpriteContents.this.upload(x, y, 0, 0, this.activeFrame);
            }
        }

        private int getPixel(SpriteContents.AnimatedTexture animatedTexture, int frameIndex, int mipLevel, int x, int y) {
            return SpriteContents.this.byMipLevel[mipLevel]
                .getPixelRGBA(
                    x + (animatedTexture.getFrameX(frameIndex) * SpriteContents.this.width >> mipLevel),
                    y + (animatedTexture.getFrameY(frameIndex) * SpriteContents.this.height >> mipLevel)
                );
        }

        private int mix(double delta, int color1, int color2) {
            return (int)(delta * color1 + (1.0 - delta) * color2);
        }

        @Override
        public void close() {
            for (NativeImage nativeImage : this.activeFrame) {
                nativeImage.close();
            }
        }
    }

    class Ticker implements SpriteTicker {
        int frame;
        int subFrame;
        final SpriteContents.AnimatedTexture animationInfo;
        @Nullable
        private final SpriteContents.InterpolationData interpolationData;

        Ticker(final SpriteContents.AnimatedTexture animationInfo, @Nullable final SpriteContents.InterpolationData interpolationData) {
            this.animationInfo = animationInfo;
            this.interpolationData = interpolationData;
        }

        @Override
        public void tickAndUpload(int x, int y) {
            this.subFrame++;
            SpriteContents.FrameInfo frameInfo = this.animationInfo.frames.get(this.frame);
            if (this.subFrame >= frameInfo.time) {
                int i = frameInfo.index;
                this.frame = (this.frame + 1) % this.animationInfo.frames.size();
                this.subFrame = 0;
                int j = this.animationInfo.frames.get(this.frame).index;
                if (i != j) {
                    this.animationInfo.uploadFrame(x, y, j);
                }
            } else if (this.interpolationData != null) {
                if (!RenderSystem.isOnRenderThread()) {
                    RenderSystem.recordRenderCall(() -> this.interpolationData.uploadInterpolatedFrame(x, y, this));
                } else {
                    this.interpolationData.uploadInterpolatedFrame(x, y, this);
                }
            }
        }

        @Override
        public void close() {
            if (this.interpolationData != null) {
                this.interpolationData.close();
            }
        }
    }
}
