package net.minecraft.client.gui.components;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public abstract class AbstractScrollWidget extends AbstractWidget implements Renderable, GuiEventListener {
    private static final WidgetSprites BACKGROUND_SPRITES = new WidgetSprites(
        ResourceLocation.withDefaultNamespace("widget/text_field"), ResourceLocation.withDefaultNamespace("widget/text_field_highlighted")
    );
    private static final ResourceLocation SCROLLER_SPRITE = ResourceLocation.withDefaultNamespace("widget/scroller");
    private static final int INNER_PADDING = 4;
    private static final int SCROLL_BAR_WIDTH = 8;
    private double scrollAmount;
    private boolean scrolling;

    public AbstractScrollWidget(int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.visible) {
            return false;
        } else {
            boolean bl = this.withinContentAreaPoint(mouseX, mouseY);
            boolean bl2 = this.scrollbarVisible()
                && mouseX >= this.getX() + this.width
                && mouseX <= this.getX() + this.width + 8
                && mouseY >= this.getY()
                && mouseY < this.getY() + this.height;
            if (bl2 && button == 0) {
                this.scrolling = true;
                return true;
            } else {
                return bl || bl2;
            }
        }
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            this.scrolling = false;
        }

        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.visible && this.isFocused() && this.scrolling) {
            if (mouseY < this.getY()) {
                this.setScrollAmount(0.0);
            } else if (mouseY > this.getY() + this.height) {
                this.setScrollAmount(this.getMaxScrollAmount());
            } else {
                int i = this.getScrollBarHeight();
                double d = Math.max(1, this.getMaxScrollAmount() / (this.height - i));
                this.setScrollAmount(this.scrollAmount + dragY * d);
            }

            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!this.visible) {
            return false;
        }

        this.setScrollAmount(this.scrollAmount - scrollY * this.scrollRate());
        return true;
    }

    /**
     * Called when a keyboard key is pressed within the GUI element.
     * <p>
     * @return {@code true} if the event is consumed, {@code false} otherwise.
     *
     * @param keyCode   the key code of the pressed key.
     * @param scanCode  the scan code of the pressed key.
     * @param modifiers the keyboard modifiers.
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean bl = keyCode == 265;
        boolean bl2 = keyCode == 264;
        if (bl || bl2) {
            double d = this.scrollAmount;
            this.setScrollAmount(this.scrollAmount + (bl ? -1 : 1) * this.scrollRate());
            if (d != this.scrollAmount) {
                return true;
            }
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (this.visible) {
            this.renderBackground(guiGraphics);
            guiGraphics.enableScissor(this.getX() + 1, this.getY() + 1, this.getX() + this.width - 1, this.getY() + this.height - 1);
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0.0, -this.scrollAmount, 0.0);
            this.renderContents(guiGraphics, mouseX, mouseY, partialTick);
            guiGraphics.pose().popPose();
            guiGraphics.disableScissor();
            this.renderDecorations(guiGraphics);
        }
    }

    private int getScrollBarHeight() {
        return Mth.clamp((int)((float)(this.height * this.height) / this.getContentHeight()), 32, this.height);
    }

    protected void renderDecorations(GuiGraphics guiGraphics) {
        if (this.scrollbarVisible()) {
            this.renderScrollBar(guiGraphics);
        }
    }

    protected int innerPadding() {
        return 4;
    }

    protected int totalInnerPadding() {
        return this.innerPadding() * 2;
    }

    protected double scrollAmount() {
        return this.scrollAmount;
    }

    protected void setScrollAmount(double scrollAmount) {
        this.scrollAmount = Mth.clamp(scrollAmount, 0.0, this.getMaxScrollAmount());
    }

    protected int getMaxScrollAmount() {
        return Math.max(0, this.getContentHeight() - (this.height - 4));
    }

    private int getContentHeight() {
        return this.getInnerHeight() + 4;
    }

    protected void renderBackground(GuiGraphics guiGraphics) {
        this.renderBorder(guiGraphics, this.getX(), this.getY(), this.getWidth(), this.getHeight());
    }

    protected void renderBorder(GuiGraphics guiGraphics, int x, int y, int width, int height) {
        ResourceLocation resourceLocation = BACKGROUND_SPRITES.get(this.isActive(), this.isFocused());
        guiGraphics.blitSprite(resourceLocation, x, y, width, height);
    }

    private void renderScrollBar(GuiGraphics guiGraphics) {
        int i = this.getScrollBarHeight();
        int j = this.getX() + this.width;
        int k = Math.max(this.getY(), (int)this.scrollAmount * (this.height - i) / this.getMaxScrollAmount() + this.getY());
        RenderSystem.enableBlend();
        guiGraphics.blitSprite(SCROLLER_SPRITE, j, k, 8, i);
        RenderSystem.disableBlend();
    }

    protected boolean withinContentAreaTopBottom(int top, int bottom) {
        return bottom - this.scrollAmount >= this.getY() && top - this.scrollAmount <= this.getY() + this.height;
    }

    protected boolean withinContentAreaPoint(double x, double y) {
        return x >= this.getX() && x < this.getX() + this.width && y >= this.getY() && y < this.getY() + this.height;
    }

    protected boolean scrollbarVisible() {
        return this.getInnerHeight() > this.getHeight();
    }

    public int scrollbarWidth() {
        return 8;
    }

    protected abstract int getInnerHeight();

    protected abstract double scrollRate();

    protected abstract void renderContents(GuiGraphics var1, int var2, int var3, float var4);
}
