package net.minecraft.client.gui.screens.advancements;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

public class AdvancementWidget {
    private static final ResourceLocation TITLE_BOX_SPRITE = ResourceLocation.withDefaultNamespace("advancements/title_box");
    private static final int HEIGHT = 26;
    private static final int BOX_X = 0;
    private static final int BOX_WIDTH = 200;
    private static final int FRAME_WIDTH = 26;
    private static final int ICON_X = 8;
    private static final int ICON_Y = 5;
    private static final int ICON_WIDTH = 26;
    private static final int TITLE_PADDING_LEFT = 3;
    private static final int TITLE_PADDING_RIGHT = 5;
    private static final int TITLE_X = 32;
    private static final int TITLE_Y = 9;
    private static final int TITLE_MAX_WIDTH = 163;
    private static final int[] TEST_SPLIT_OFFSETS = new int[]{0, 10, -10, 25, -25};
    private final AdvancementTab tab;
    private final AdvancementNode advancementNode;
    private final DisplayInfo display;
    private final FormattedCharSequence title;
    private final int width;
    private final List<FormattedCharSequence> description;
    private final Minecraft minecraft;
    @Nullable
    private AdvancementWidget parent;
    private final List<AdvancementWidget> children = Lists.newArrayList();
    @Nullable
    private AdvancementProgress progress;
    private final int x;
    private final int y;

    public AdvancementWidget(AdvancementTab tab, Minecraft minecraft, AdvancementNode advancementNode, DisplayInfo display) {
        this.tab = tab;
        this.advancementNode = advancementNode;
        this.display = display;
        this.minecraft = minecraft;
        this.title = Language.getInstance().getVisualOrder(minecraft.font.substrByWidth(display.getTitle(), 163));
        this.x = Mth.floor(display.getX() * 28.0F);
        this.y = Mth.floor(display.getY() * 27.0F);
        int i = this.getMaxProgressWidth();
        int j = 29 + minecraft.font.width(this.title) + i;
        this.description = Language.getInstance()
            .getVisualOrder(
                this.findOptimalLines(ComponentUtils.mergeStyles(display.getDescription().copy(), Style.EMPTY.withColor(display.getType().getChatColor())), j)
            );

        for (FormattedCharSequence formattedCharSequence : this.description) {
            j = Math.max(j, minecraft.font.width(formattedCharSequence));
        }

        this.width = j + 3 + 5;
    }

    private int getMaxProgressWidth() {
        int i = this.advancementNode.advancement().requirements().size();
        if (i <= 1) {
            return 0;
        }

        int j = 8;
        Component component = Component.translatable("advancements.progress", i, i);
        return this.minecraft.font.width(component) + 8;
    }

    private static float getMaxWidth(StringSplitter manager, List<FormattedText> text) {
        return (float)text.stream().mapToDouble(manager::stringWidth).max().orElse(0.0);
    }

    private List<FormattedText> findOptimalLines(Component component, int maxWidth) {
        StringSplitter stringSplitter = this.minecraft.font.getSplitter();
        List<FormattedText> list = null;
        float f = Float.MAX_VALUE;

        for (int i : TEST_SPLIT_OFFSETS) {
            List<FormattedText> list2 = stringSplitter.splitLines(component, maxWidth - i, Style.EMPTY);
            float g = Math.abs(getMaxWidth(stringSplitter, list2) - maxWidth);
            if (g <= 10.0F) {
                return list2;
            }

            if (g < f) {
                f = g;
                list = list2;
            }
        }

        return list;
    }

    @Nullable
    private AdvancementWidget getFirstVisibleParent(AdvancementNode advancement) {
        do {
            advancement = advancement.parent();
        } while (advancement != null && advancement.advancement().display().isEmpty());

        return advancement != null && !advancement.advancement().display().isEmpty() ? this.tab.getWidget(advancement.holder()) : null;
    }

    public void drawConnectivity(GuiGraphics guiGraphics, int x, int y, boolean dropShadow) {
        if (this.parent != null) {
            int i = x + this.parent.x + 13;
            int j = x + this.parent.x + 26 + 4;
            int k = y + this.parent.y + 13;
            int l = x + this.x + 13;
            int m = y + this.y + 13;
            int n = dropShadow ? -16777216 : -1;
            if (dropShadow) {
                guiGraphics.hLine(j, i, k - 1, n);
                guiGraphics.hLine(j + 1, i, k, n);
                guiGraphics.hLine(j, i, k + 1, n);
                guiGraphics.hLine(l, j - 1, m - 1, n);
                guiGraphics.hLine(l, j - 1, m, n);
                guiGraphics.hLine(l, j - 1, m + 1, n);
                guiGraphics.vLine(j - 1, m, k, n);
                guiGraphics.vLine(j + 1, m, k, n);
            } else {
                guiGraphics.hLine(j, i, k, n);
                guiGraphics.hLine(l, j, m, n);
                guiGraphics.vLine(j, m, k, n);
            }
        }

        for (AdvancementWidget advancementWidget : this.children) {
            advancementWidget.drawConnectivity(guiGraphics, x, y, dropShadow);
        }
    }

    public void draw(GuiGraphics guiGraphics, int x, int y) {
        if (!this.display.isHidden() || this.progress != null && this.progress.isDone()) {
            float f = this.progress == null ? 0.0F : this.progress.getPercent();
            AdvancementWidgetType advancementWidgetType;
            if (f >= 1.0F) {
                advancementWidgetType = AdvancementWidgetType.OBTAINED;
            } else {
                advancementWidgetType = AdvancementWidgetType.UNOBTAINED;
            }

            guiGraphics.blitSprite(advancementWidgetType.frameSprite(this.display.getType()), x + this.x + 3, y + this.y, 26, 26);
            guiGraphics.renderFakeItem(this.display.getIcon(), x + this.x + 8, y + this.y + 5);
        }

        for (AdvancementWidget advancementWidget : this.children) {
            advancementWidget.draw(guiGraphics, x, y);
        }
    }

    public int getWidth() {
        return this.width;
    }

    public void setProgress(AdvancementProgress progress) {
        this.progress = progress;
    }

    public void addChild(AdvancementWidget advancementWidget) {
        this.children.add(advancementWidget);
    }

    public void drawHover(GuiGraphics guiGraphics, int x, int y, float fade, int width, int height) {
        boolean bl = width + x + this.x + this.width + 26 >= this.tab.getScreen().width;
        Component component = this.progress == null ? null : this.progress.getProgressText();
        int i = component == null ? 0 : this.minecraft.font.width(component);
        boolean bl2 = 113 - y - this.y - 26 <= 6 + this.description.size() * 9;
        float f = this.progress == null ? 0.0F : this.progress.getPercent();
        int j = Mth.floor(f * this.width);
        AdvancementWidgetType advancementWidgetType;
        AdvancementWidgetType advancementWidgetType2;
        AdvancementWidgetType advancementWidgetType3;
        if (f >= 1.0F) {
            j = this.width / 2;
            advancementWidgetType = AdvancementWidgetType.OBTAINED;
            advancementWidgetType2 = AdvancementWidgetType.OBTAINED;
            advancementWidgetType3 = AdvancementWidgetType.OBTAINED;
        } else if (j < 2) {
            j = this.width / 2;
            advancementWidgetType = AdvancementWidgetType.UNOBTAINED;
            advancementWidgetType2 = AdvancementWidgetType.UNOBTAINED;
            advancementWidgetType3 = AdvancementWidgetType.UNOBTAINED;
        } else if (j > this.width - 2) {
            j = this.width / 2;
            advancementWidgetType = AdvancementWidgetType.OBTAINED;
            advancementWidgetType2 = AdvancementWidgetType.OBTAINED;
            advancementWidgetType3 = AdvancementWidgetType.UNOBTAINED;
        } else {
            advancementWidgetType = AdvancementWidgetType.OBTAINED;
            advancementWidgetType2 = AdvancementWidgetType.UNOBTAINED;
            advancementWidgetType3 = AdvancementWidgetType.UNOBTAINED;
        }

        int k = this.width - j;
        RenderSystem.enableBlend();
        int l = y + this.y;
        int m;
        if (bl) {
            m = x + this.x - this.width + 26 + 6;
        } else {
            m = x + this.x;
        }

        int n = 32 + this.description.size() * 9;
        if (!this.description.isEmpty()) {
            if (bl2) {
                guiGraphics.blitSprite(TITLE_BOX_SPRITE, m, l + 26 - n, this.width, n);
            } else {
                guiGraphics.blitSprite(TITLE_BOX_SPRITE, m, l, this.width, n);
            }
        }

        guiGraphics.blitSprite(advancementWidgetType.boxSprite(), 200, 26, 0, 0, m, l, j, 26);
        guiGraphics.blitSprite(advancementWidgetType2.boxSprite(), 200, 26, 200 - k, 0, m + j, l, k, 26);
        guiGraphics.blitSprite(advancementWidgetType3.frameSprite(this.display.getType()), x + this.x + 3, y + this.y, 26, 26);
        if (bl) {
            guiGraphics.drawString(this.minecraft.font, this.title, m + 5, y + this.y + 9, -1);
            if (component != null) {
                guiGraphics.drawString(this.minecraft.font, component, x + this.x - i, y + this.y + 9, -1);
            }
        } else {
            guiGraphics.drawString(this.minecraft.font, this.title, x + this.x + 32, y + this.y + 9, -1);
            if (component != null) {
                guiGraphics.drawString(this.minecraft.font, component, x + this.x + this.width - i - 5, y + this.y + 9, -1);
            }
        }

        if (bl2) {
            for (int o = 0; o < this.description.size(); o++) {
                guiGraphics.drawString(this.minecraft.font, this.description.get(o), m + 5, l + 26 - n + 7 + o * 9, -5592406, false);
            }
        } else {
            for (int o = 0; o < this.description.size(); o++) {
                guiGraphics.drawString(this.minecraft.font, this.description.get(o), m + 5, y + this.y + 9 + 17 + o * 9, -5592406, false);
            }
        }

        guiGraphics.renderFakeItem(this.display.getIcon(), x + this.x + 8, y + this.y + 5);
    }

    public boolean isMouseOver(int x, int y, int mouseX, int mouseY) {
        if (!this.display.isHidden() || this.progress != null && this.progress.isDone()) {
            int i = x + this.x;
            int j = i + 26;
            int k = y + this.y;
            int l = k + 26;
            return mouseX >= i && mouseX <= j && mouseY >= k && mouseY <= l;
        } else {
            return false;
        }
    }

    public void attachToParent() {
        if (this.parent == null && this.advancementNode.parent() != null) {
            this.parent = this.getFirstVisibleParent(this.advancementNode);
            if (this.parent != null) {
                this.parent.addChild(this);
            }
        }
    }

    public int getY() {
        return this.y;
    }

    public int getX() {
        return this.x;
    }
}
