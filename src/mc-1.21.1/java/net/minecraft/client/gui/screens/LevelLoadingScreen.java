package net.minecraft.client.gui.screens;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.Util;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.progress.StoringChunkProgressListener;
import net.minecraft.util.Mth;
import net.minecraft.world.level.chunk.status.ChunkStatus;

public class LevelLoadingScreen extends Screen {
    private static final long NARRATION_DELAY_MS = 2000L;
    private final StoringChunkProgressListener progressListener;
    private long lastNarration = -1L;
    private boolean done;
    private static final Object2IntMap<ChunkStatus> COLORS = Util.make(new Object2IntOpenHashMap<>(), map -> {
        map.defaultReturnValue(0);
        map.put(ChunkStatus.EMPTY, 5526612);
        map.put(ChunkStatus.STRUCTURE_STARTS, 10066329);
        map.put(ChunkStatus.STRUCTURE_REFERENCES, 6250897);
        map.put(ChunkStatus.BIOMES, 8434258);
        map.put(ChunkStatus.NOISE, 13750737);
        map.put(ChunkStatus.SURFACE, 7497737);
        map.put(ChunkStatus.CARVERS, 3159410);
        map.put(ChunkStatus.FEATURES, 2213376);
        map.put(ChunkStatus.INITIALIZE_LIGHT, 13421772);
        map.put(ChunkStatus.LIGHT, 16769184);
        map.put(ChunkStatus.SPAWN, 15884384);
        map.put(ChunkStatus.FULL, 16777215);
    });

    public LevelLoadingScreen(StoringChunkProgressListener progressListener) {
        super(GameNarrator.NO_TITLE);
        this.progressListener = progressListener;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    protected boolean shouldNarrateNavigation() {
        return false;
    }

    @Override
    public void removed() {
        this.done = true;
        this.triggerImmediateNarration(true);
    }

    @Override
    protected void updateNarratedWidget(NarrationElementOutput narrationElementOutput) {
        if (this.done) {
            narrationElementOutput.add(NarratedElementType.TITLE, Component.translatable("narrator.loading.done"));
        } else {
            narrationElementOutput.add(NarratedElementType.TITLE, this.getFormattedProgress());
        }
    }

    private Component getFormattedProgress() {
        return Component.translatable("loading.progress", Mth.clamp(this.progressListener.getProgress(), 0, 100));
    }

    /**
     * Renders the graphical user interface (GUI) element.
     *
     * @param guiGraphics the GuiGraphics object used for rendering.
     * @param mouseX      the x-coordinate of the mouse cursor.
     * @param mouseY      the y-coordinate of the mouse cursor.
     * @param partialTick the partial tick time.
     */
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        long l = Util.getMillis();
        if (l - this.lastNarration > 2000L) {
            this.lastNarration = l;
            this.triggerImmediateNarration(true);
        }

        int i = this.width / 2;
        int j = this.height / 2;
        renderChunks(guiGraphics, this.progressListener, i, j, 2, 0);
        int k = this.progressListener.getDiameter() + 9 + 2;
        guiGraphics.drawCenteredString(this.font, this.getFormattedProgress(), i, j - k, 16777215);
    }

    public static void renderChunks(GuiGraphics guiGraphics, StoringChunkProgressListener progressListener, int x, int y, int i, int j) {
        int k = i + j;
        int l = progressListener.getFullDiameter();
        int m = l * k - j;
        int n = progressListener.getDiameter();
        int o = n * k - j;
        int p = x - o / 2;
        int q = y - o / 2;
        int r = m / 2 + 1;
        int s = -16772609;
        guiGraphics.drawManaged(() -> {
            if (j != 0) {
                guiGraphics.fill(x - r, y - r, x - r + 1, y + r, -16772609);
                guiGraphics.fill(x + r - 1, y - r, x + r, y + r, -16772609);
                guiGraphics.fill(x - r, y - r, x + r, y - r + 1, -16772609);
                guiGraphics.fill(x - r, y + r - 1, x + r, y + r, -16772609);
            }

            for (int rx = 0; rx < n; rx++) {
                for (int sx = 0; sx < n; sx++) {
                    ChunkStatus chunkStatus = progressListener.getStatus(rx, sx);
                    int t = p + rx * k;
                    int u = q + sx * k;
                    guiGraphics.fill(t, u, t + i, u + i, COLORS.getInt(chunkStatus) | 0xFF000000);
                }
            }
        });
    }
}
