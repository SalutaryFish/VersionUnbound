package net.minecraft.client.gui.components;

import java.util.OptionalInt;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.SingleKeyCache;

public class MultiLineTextWidget extends AbstractStringWidget {
    private OptionalInt maxWidth = OptionalInt.empty();
    private OptionalInt maxRows = OptionalInt.empty();
    private final SingleKeyCache<MultiLineTextWidget.CacheKey, MultiLineLabel> cache;
    private boolean centered = false;

    public MultiLineTextWidget(Component message, Font font) {
        this(0, 0, message, font);
    }

    public MultiLineTextWidget(int x, int y, Component message, Font font) {
        super(x, y, 0, 0, message, font);
        this.cache = Util.singleKeyCache(
            cacheKey -> cacheKey.maxRows.isPresent()
                ? MultiLineLabel.create(font, cacheKey.maxWidth, cacheKey.maxRows.getAsInt(), cacheKey.message)
                : MultiLineLabel.create(font, cacheKey.message, cacheKey.maxWidth)
        );
        this.active = false;
    }

    public MultiLineTextWidget setColor(int color) {
        super.setColor(color);
        return this;
    }

    public MultiLineTextWidget setMaxWidth(int maxWidth) {
        this.maxWidth = OptionalInt.of(maxWidth);
        return this;
    }

    public MultiLineTextWidget setMaxRows(int maxRows) {
        this.maxRows = OptionalInt.of(maxRows);
        return this;
    }

    public MultiLineTextWidget setCentered(boolean centered) {
        this.centered = centered;
        return this;
    }

    @Override
    public int getWidth() {
        return this.cache.getValue(this.getFreshCacheKey()).getWidth();
    }

    @Override
    public int getHeight() {
        return this.cache.getValue(this.getFreshCacheKey()).getLineCount() * 9;
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        MultiLineLabel multiLineLabel = this.cache.getValue(this.getFreshCacheKey());
        int i = this.getX();
        int j = this.getY();
        int k = 9;
        int l = this.getColor();
        if (this.centered) {
            multiLineLabel.renderCentered(guiGraphics, i + this.getWidth() / 2, j, k, l);
        } else {
            multiLineLabel.renderLeftAligned(guiGraphics, i, j, k, l);
        }
    }

    private MultiLineTextWidget.CacheKey getFreshCacheKey() {
        return new MultiLineTextWidget.CacheKey(this.getMessage(), this.maxWidth.orElse(Integer.MAX_VALUE), this.maxRows);
    }

    record CacheKey(Component message, int maxWidth, OptionalInt maxRows) {
    }
}
