package net.minecraft.client.renderer;

import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;

public class ViewArea {
    protected final LevelRenderer levelRenderer;
    protected final Level level;
    protected int sectionGridSizeY;
    protected int sectionGridSizeX;
    protected int sectionGridSizeZ;
    private int viewDistance;
    public SectionRenderDispatcher.RenderSection[] sections;

    public ViewArea(SectionRenderDispatcher sectionRenderDispatcher, Level level, int viewDistance, LevelRenderer levelRenderer) {
        this.levelRenderer = levelRenderer;
        this.level = level;
        this.setViewDistance(viewDistance);
        this.createSections(sectionRenderDispatcher);
    }

    protected void createSections(SectionRenderDispatcher sectionRenderDispatcher) {
        if (!Minecraft.getInstance().isSameThread()) {
            throw new IllegalStateException("createSections called from wrong thread: " + Thread.currentThread().getName());
        }

        int i = this.sectionGridSizeX * this.sectionGridSizeY * this.sectionGridSizeZ;
        this.sections = new SectionRenderDispatcher.RenderSection[i];

        for (int j = 0; j < this.sectionGridSizeX; j++) {
            for (int k = 0; k < this.sectionGridSizeY; k++) {
                for (int l = 0; l < this.sectionGridSizeZ; l++) {
                    int m = this.getSectionIndex(j, k, l);
                    this.sections[m] = sectionRenderDispatcher.new RenderSection(m, j * 16, this.level.getMinBuildHeight() + k * 16, l * 16);
                }
            }
        }
    }

    public void releaseAllBuffers() {
        for (SectionRenderDispatcher.RenderSection renderSection : this.sections) {
            renderSection.releaseBuffers();
        }
    }

    private int getSectionIndex(int x, int y, int z) {
        return (z * this.sectionGridSizeY + y) * this.sectionGridSizeX + x;
    }

    protected void setViewDistance(int renderDistanceChunks) {
        int i = renderDistanceChunks * 2 + 1;
        this.sectionGridSizeX = i;
        this.sectionGridSizeY = this.level.getSectionsCount();
        this.sectionGridSizeZ = i;
        this.viewDistance = renderDistanceChunks;
    }

    public int getViewDistance() {
        return this.viewDistance;
    }

    public LevelHeightAccessor getLevelHeightAccessor() {
        return this.level;
    }

    public void repositionCamera(double viewEntityX, double viewEntityZ) {
        int i = Mth.ceil(viewEntityX);
        int j = Mth.ceil(viewEntityZ);

        for (int k = 0; k < this.sectionGridSizeX; k++) {
            int l = this.sectionGridSizeX * 16;
            int m = i - 8 - l / 2;
            int n = m + Math.floorMod(k * 16 - m, l);

            for (int o = 0; o < this.sectionGridSizeZ; o++) {
                int p = this.sectionGridSizeZ * 16;
                int q = j - 8 - p / 2;
                int r = q + Math.floorMod(o * 16 - q, p);

                for (int s = 0; s < this.sectionGridSizeY; s++) {
                    int t = this.level.getMinBuildHeight() + s * 16;
                    SectionRenderDispatcher.RenderSection renderSection = this.sections[this.getSectionIndex(k, s, o)];
                    BlockPos blockPos = renderSection.getOrigin();
                    if (n != blockPos.getX() || t != blockPos.getY() || r != blockPos.getZ()) {
                        renderSection.setOrigin(n, t, r);
                    }
                }
            }
        }
    }

    public void setDirty(int sectionX, int sectionY, int sectionZ, boolean reRenderOnMainThread) {
        int i = Math.floorMod(sectionX, this.sectionGridSizeX);
        int j = Math.floorMod(sectionY - this.level.getMinSection(), this.sectionGridSizeY);
        int k = Math.floorMod(sectionZ, this.sectionGridSizeZ);
        SectionRenderDispatcher.RenderSection renderSection = this.sections[this.getSectionIndex(i, j, k)];
        renderSection.setDirty(reRenderOnMainThread);
    }

    @Nullable
    protected SectionRenderDispatcher.RenderSection getRenderSectionAt(BlockPos pos) {
        int i = Mth.floorDiv(pos.getY() - this.level.getMinBuildHeight(), 16);
        if (i >= 0 && i < this.sectionGridSizeY) {
            int j = Mth.positiveModulo(Mth.floorDiv(pos.getX(), 16), this.sectionGridSizeX);
            int k = Mth.positiveModulo(Mth.floorDiv(pos.getZ(), 16), this.sectionGridSizeZ);
            return this.sections[this.getSectionIndex(j, i, k)];
        } else {
            return null;
        }
    }
}
