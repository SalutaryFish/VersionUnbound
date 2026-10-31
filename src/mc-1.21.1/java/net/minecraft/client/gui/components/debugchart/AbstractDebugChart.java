package net.minecraft.client.gui.components.debugchart;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.util.debugchart.SampleStorage;

public abstract class AbstractDebugChart {
    protected static final int COLOR_GREY = 14737632;
    protected static final int CHART_HEIGHT = 60;
    protected static final int LINE_WIDTH = 1;
    protected final Font font;
    protected final SampleStorage sampleStorage;

    protected AbstractDebugChart(Font font, SampleStorage sampleStorage) {
        this.font = font;
        this.sampleStorage = sampleStorage;
    }

    public int getWidth(int maxWidth) {
        return Math.min(this.sampleStorage.capacity() + 2, maxWidth);
    }

    public void drawChart(GuiGraphics guiGraphics, int x, int width) {
        int i = guiGraphics.guiHeight();
        guiGraphics.fill(RenderType.guiOverlay(), x, i - 60, x + width, i, -1873784752);
        long l = 0L;
        long m = 2147483647L;
        long n = -2147483648L;
        int j = Math.max(0, this.sampleStorage.capacity() - (width - 2));
        int k = this.sampleStorage.size() - j;

        for (int o = 0; o < k; o++) {
            int p = x + o + 1;
            int q = j + o;
            long r = this.getValueForAggregation(q);
            m = Math.min(m, r);
            n = Math.max(n, r);
            l += r;
            this.drawDimensions(guiGraphics, i, p, q);
        }

        guiGraphics.hLine(RenderType.guiOverlay(), x, x + width - 1, i - 60, -1);
        guiGraphics.hLine(RenderType.guiOverlay(), x, x + width - 1, i - 1, -1);
        guiGraphics.vLine(RenderType.guiOverlay(), x, i - 60, i, -1);
        guiGraphics.vLine(RenderType.guiOverlay(), x + width - 1, i - 60, i, -1);
        if (k > 0) {
            String string = this.toDisplayString(m) + " min";
            String string2 = this.toDisplayString((double)l / k) + " avg";
            String string3 = this.toDisplayString(n) + " max";
            guiGraphics.drawString(this.font, string, x + 2, i - 60 - 9, 14737632);
            guiGraphics.drawCenteredString(this.font, string2, x + width / 2, i - 60 - 9, 14737632);
            guiGraphics.drawString(this.font, string3, x + width - this.font.width(string3) - 2, i - 60 - 9, 14737632);
        }

        this.renderAdditionalLinesAndLabels(guiGraphics, x, width, i);
    }

    protected void drawDimensions(GuiGraphics guiGraphics, int height, int x, int index) {
        this.drawMainDimension(guiGraphics, height, x, index);
        this.drawAdditionalDimensions(guiGraphics, height, x, index);
    }

    protected void drawMainDimension(GuiGraphics guiGraphics, int height, int x, int index) {
        long l = this.sampleStorage.get(index);
        int i = this.getSampleHeight(l);
        int j = this.getSampleColor(l);
        guiGraphics.fill(RenderType.guiOverlay(), x, height - i, x + 1, height, j);
    }

    protected void drawAdditionalDimensions(GuiGraphics guiGraphics, int height, int x, int index) {
    }

    protected long getValueForAggregation(int index) {
        return this.sampleStorage.get(index);
    }

    protected void renderAdditionalLinesAndLabels(GuiGraphics guiGraphics, int x, int width, int height) {
    }

    protected void drawStringWithShade(GuiGraphics guiGraphics, String text, int x, int y) {
        guiGraphics.fill(RenderType.guiOverlay(), x, y, x + this.font.width(text) + 1, y + 9, -1873784752);
        guiGraphics.drawString(this.font, text, x + 1, y + 1, 14737632, false);
    }

    protected abstract String toDisplayString(double var1);

    protected abstract int getSampleHeight(double var1);

    protected abstract int getSampleColor(long var1);

    protected int getSampleColor(double value, double minPosition, int minColor, double midPosition, int midColor, double maxPosition, int guiGraphics) {
        value = Mth.clamp(value, minPosition, maxPosition);
        return value < midPosition
            ? FastColor.ARGB32.lerp((float)((value - minPosition) / (midPosition - minPosition)), minColor, midColor)
            : FastColor.ARGB32.lerp((float)((value - midPosition) / (maxPosition - midPosition)), midColor, guiGraphics);
    }
}
