package net.minecraft.client.gui.components.debugchart;

import java.util.Locale;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.TimeUtil;
import net.minecraft.util.debugchart.SampleStorage;
import net.minecraft.util.debugchart.TpsDebugDimensions;

public class TpsDebugChart extends AbstractDebugChart {
    private static final int RED = -65536;
    private static final int YELLOW = -256;
    private static final int GREEN = -16711936;
    private static final int TICK_METHOD_COLOR = -6745839;
    private static final int TASK_COLOR = -4548257;
    private static final int OTHER_COLOR = -10547572;
    private final Supplier<Float> msptSupplier;

    public TpsDebugChart(Font font, SampleStorage sampleStorage, Supplier<Float> msptSupplier) {
        super(font, sampleStorage);
        this.msptSupplier = msptSupplier;
    }

    @Override
    protected void renderAdditionalLinesAndLabels(GuiGraphics guiGraphics, int x, int width, int height) {
        float f = (float)TimeUtil.MILLISECONDS_PER_SECOND / this.msptSupplier.get();
        this.drawStringWithShade(guiGraphics, String.format("%.1f TPS", f), x + 1, height - 60 + 1);
    }

    @Override
    protected void drawAdditionalDimensions(GuiGraphics guiGraphics, int height, int x, int index) {
        long l = this.sampleStorage.get(index, TpsDebugDimensions.TICK_SERVER_METHOD.ordinal());
        int i = this.getSampleHeight(l);
        guiGraphics.fill(RenderType.guiOverlay(), x, height - i, x + 1, height, -6745839);
        long m = this.sampleStorage.get(index, TpsDebugDimensions.SCHEDULED_TASKS.ordinal());
        int j = this.getSampleHeight(m);
        guiGraphics.fill(RenderType.guiOverlay(), x, height - i - j, x + 1, height - i, -4548257);
        long n = this.sampleStorage.get(index) - this.sampleStorage.get(index, TpsDebugDimensions.IDLE.ordinal()) - l - m;
        int k = this.getSampleHeight(n);
        guiGraphics.fill(RenderType.guiOverlay(), x, height - k - j - i, x + 1, height - j - i, -10547572);
    }

    @Override
    protected long getValueForAggregation(int index) {
        return this.sampleStorage.get(index) - this.sampleStorage.get(index, TpsDebugDimensions.IDLE.ordinal());
    }

    @Override
    protected String toDisplayString(double value) {
        return String.format(Locale.ROOT, "%d ms", (int)Math.round(toMilliseconds(value)));
    }

    @Override
    protected int getSampleHeight(double value) {
        return (int)Math.round(toMilliseconds(value) * 60.0 / this.msptSupplier.get().floatValue());
    }

    @Override
    protected int getSampleColor(long value) {
        float f = this.msptSupplier.get();
        return this.getSampleColor(toMilliseconds(value), f, -16711936, f * 1.125, -256, f * 1.25, -65536);
    }

    private static double toMilliseconds(double value) {
        return value / 1000000.0;
    }
}
