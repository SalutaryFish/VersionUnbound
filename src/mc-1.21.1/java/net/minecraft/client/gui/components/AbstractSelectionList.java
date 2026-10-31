package net.minecraft.client.gui.components;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.AbstractList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.ScreenDirection;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public abstract class AbstractSelectionList<E extends AbstractSelectionList.Entry<E>> extends AbstractContainerWidget {
    protected static final int SCROLLBAR_WIDTH = 6;
    private static final ResourceLocation SCROLLER_SPRITE = ResourceLocation.withDefaultNamespace("widget/scroller");
    private static final ResourceLocation SCROLLER_BACKGROUND_SPRITE = ResourceLocation.withDefaultNamespace("widget/scroller_background");
    private static final ResourceLocation MENU_LIST_BACKGROUND = ResourceLocation.withDefaultNamespace("textures/gui/menu_list_background.png");
    private static final ResourceLocation INWORLD_MENU_LIST_BACKGROUND = ResourceLocation.withDefaultNamespace("textures/gui/inworld_menu_list_background.png");
    protected final Minecraft minecraft;
    protected final int itemHeight;
    private final List<E> children = new AbstractSelectionList.TrackedList();
    protected boolean centerListVertically = true;
    private double scrollAmount;
    private boolean renderHeader;
    protected int headerHeight;
    private boolean scrolling;
    @Nullable
    private E selected;
    @Nullable
    private E hovered;

    public AbstractSelectionList(Minecraft minecraft, int width, int height, int y, int itemHeight) {
        super(0, y, width, height, CommonComponents.EMPTY);
        this.minecraft = minecraft;
        this.itemHeight = itemHeight;
    }

    protected void setRenderHeader(boolean renderHeader, int headerHeight) {
        this.renderHeader = renderHeader;
        this.headerHeight = headerHeight;
        if (!renderHeader) {
            this.headerHeight = 0;
        }
    }

    public int getRowWidth() {
        return 220;
    }

    @Nullable
    public E getSelected() {
        return this.selected;
    }

    public void setSelected(@Nullable E selected) {
        this.selected = selected;
    }

    public E getFirstElement() {
        return this.children.get(0);
    }

    @Nullable
    public E getFocused() {
        return (E)super.getFocused();
    }

    @Override
    public final List<E> children() {
        return this.children;
    }

    protected void clearEntries() {
        this.children.clear();
        this.selected = null;
    }

    protected void replaceEntries(Collection<E> entries) {
        this.clearEntries();
        this.children.addAll(entries);
    }

    protected E getEntry(int index) {
        return this.children().get(index);
    }

    protected int addEntry(E entry) {
        this.children.add(entry);
        return this.children.size() - 1;
    }

    protected void addEntryToTop(E entry) {
        double d = this.getMaxScroll() - this.getScrollAmount();
        this.children.add(0, entry);
        this.setScrollAmount(this.getMaxScroll() - d);
    }

    protected boolean removeEntryFromTop(E entry) {
        double d = this.getMaxScroll() - this.getScrollAmount();
        boolean bl = this.removeEntry(entry);
        this.setScrollAmount(this.getMaxScroll() - d);
        return bl;
    }

    protected int getItemCount() {
        return this.children().size();
    }

    protected boolean isSelectedItem(int index) {
        return Objects.equals(this.getSelected(), this.children().get(index));
    }

    @Nullable
    protected final E getEntryAtPosition(double mouseX, double mouseY) {
        int i = this.getRowWidth() / 2;
        int j = this.getX() + this.width / 2;
        int k = j - i;
        int l = j + i;
        int m = Mth.floor(mouseY - this.getY()) - this.headerHeight + (int)this.getScrollAmount() - 4;
        int n = m / this.itemHeight;
        return mouseX >= k && mouseX <= l && n >= 0 && m >= 0 && n < this.getItemCount() ? this.children().get(n) : null;
    }

    public void updateSize(int width, HeaderAndFooterLayout layout) {
        this.updateSizeAndPosition(width, layout.getContentHeight(), layout.getHeaderHeight());
    }

    public void updateSizeAndPosition(int width, int height, int y) {
        this.setSize(width, height);
        this.setPosition(0, y);
        this.clampScrollAmount();
    }

    protected int getMaxPosition() {
        return this.getItemCount() * this.itemHeight + this.headerHeight;
    }

    protected boolean clickedHeader(int x, int y) {
        return false;
    }

    protected void renderHeader(GuiGraphics guiGraphics, int x, int y) {
    }

    protected void renderDecorations(GuiGraphics guiGraphics, int mouseX, int mouseY) {
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.hovered = this.isMouseOver(mouseX, mouseY) ? this.getEntryAtPosition(mouseX, mouseY) : null;
        this.renderListBackground(guiGraphics);
        this.enableScissor(guiGraphics);
        if (this.renderHeader) {
            int i = this.getRowLeft();
            int j = this.getY() + 4 - (int)this.getScrollAmount();
            this.renderHeader(guiGraphics, i, j);
        }

        this.renderListItems(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.disableScissor();
        this.renderListSeparators(guiGraphics);
        if (this.scrollbarVisible()) {
            int i = this.getScrollbarPosition();
            int j = (int)((float)(this.height * this.height) / this.getMaxPosition());
            j = Mth.clamp(j, 32, this.height - 8);
            int k = (int)this.getScrollAmount() * (this.height - j) / this.getMaxScroll() + this.getY();
            if (k < this.getY()) {
                k = this.getY();
            }

            RenderSystem.enableBlend();
            guiGraphics.blitSprite(SCROLLER_BACKGROUND_SPRITE, i, this.getY(), 6, this.getHeight());
            guiGraphics.blitSprite(SCROLLER_SPRITE, i, k, 6, j);
            RenderSystem.disableBlend();
        }

        this.renderDecorations(guiGraphics, mouseX, mouseY);
        RenderSystem.disableBlend();
    }

    protected boolean scrollbarVisible() {
        return this.getMaxScroll() > 0;
    }

    protected void renderListSeparators(GuiGraphics guiGraphics) {
        RenderSystem.enableBlend();
        ResourceLocation resourceLocation = this.minecraft.level == null ? Screen.HEADER_SEPARATOR : Screen.INWORLD_HEADER_SEPARATOR;
        ResourceLocation resourceLocation2 = this.minecraft.level == null ? Screen.FOOTER_SEPARATOR : Screen.INWORLD_FOOTER_SEPARATOR;
        guiGraphics.blit(resourceLocation, this.getX(), this.getY() - 2, 0.0F, 0.0F, this.getWidth(), 2, 32, 2);
        guiGraphics.blit(resourceLocation2, this.getX(), this.getBottom(), 0.0F, 0.0F, this.getWidth(), 2, 32, 2);
        RenderSystem.disableBlend();
    }

    protected void renderListBackground(GuiGraphics guiGraphics) {
        RenderSystem.enableBlend();
        ResourceLocation resourceLocation = this.minecraft.level == null ? MENU_LIST_BACKGROUND : INWORLD_MENU_LIST_BACKGROUND;
        guiGraphics.blit(
            resourceLocation,
            this.getX(),
            this.getY(),
            this.getRight(),
            this.getBottom() + (int)this.getScrollAmount(),
            this.getWidth(),
            this.getHeight(),
            32,
            32
        );
        RenderSystem.disableBlend();
    }

    protected void enableScissor(GuiGraphics guiGraphics) {
        guiGraphics.enableScissor(this.getX(), this.getY(), this.getRight(), this.getBottom());
    }

    protected void centerScrollOn(E entry) {
        this.setScrollAmount(this.children().indexOf(entry) * this.itemHeight + this.itemHeight / 2 - this.height / 2);
    }

    protected void ensureVisible(E entry) {
        int i = this.getRowTop(this.children().indexOf(entry));
        int j = i - this.getY() - 4 - this.itemHeight;
        if (j < 0) {
            this.scroll(j);
        }

        int k = this.getBottom() - i - this.itemHeight - this.itemHeight;
        if (k < 0) {
            this.scroll(-k);
        }
    }

    private void scroll(int scroll) {
        this.setScrollAmount(this.getScrollAmount() + scroll);
    }

    public double getScrollAmount() {
        return this.scrollAmount;
    }

    public void setClampedScrollAmount(double scroll) {
        this.scrollAmount = Mth.clamp(scroll, 0.0, this.getMaxScroll());
    }

    public void setScrollAmount(double scroll) {
        this.setClampedScrollAmount(scroll);
    }

    public void clampScrollAmount() {
        this.setClampedScrollAmount(this.getScrollAmount());
    }

    public int getMaxScroll() {
        return Math.max(0, this.getMaxPosition() - (this.height - 4));
    }

    protected void updateScrollingState(double mouseX, double mouseY, int button) {
        this.scrolling = button == 0 && mouseX >= this.getScrollbarPosition() && mouseX < this.getScrollbarPosition() + 6;
    }

    protected int getScrollbarPosition() {
        return this.getDefaultScrollbarPosition();
    }

    protected int getDefaultScrollbarPosition() {
        return this.getRealRowRight() + this.getListOutlinePadding();
    }

    private int getListOutlinePadding() {
        return 10;
    }

    protected boolean isValidMouseClick(int button) {
        return button == 0;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.isValidMouseClick(button)) {
            return false;
        }

        this.updateScrollingState(mouseX, mouseY, button);
        if (!this.isMouseOver(mouseX, mouseY)) {
            return false;
        }

        E entry = this.getEntryAtPosition(mouseX, mouseY);
        if (entry != null) {
            if (entry.mouseClicked(mouseX, mouseY, button)) {
                E entry2 = this.getFocused();
                if (entry2 != entry && entry2 instanceof ContainerEventHandler containerEventHandler) {
                    containerEventHandler.setFocused(null);
                }

                this.setFocused(entry);
                this.setDragging(true);
                return true;
            }
        } else if (this.clickedHeader(
            (int)(mouseX - (this.getX() + this.width / 2 - this.getRowWidth() / 2)), (int)(mouseY - this.getY()) + (int)this.getScrollAmount() - 4
        )) {
            return true;
        }

        return this.scrolling;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return this.getFocused() != null ? this.getFocused().mouseReleased(mouseX, mouseY, button) : false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (super.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }

        if (button == 0 && this.scrolling) {
            if (mouseY < this.getY()) {
                this.setScrollAmount(0.0);
            } else if (mouseY > this.getBottom()) {
                this.setScrollAmount(this.getMaxScroll());
            } else {
                double d = Math.max(1, this.getMaxScroll());
                int i = this.height;
                int j = Mth.clamp((int)((float)(i * i) / this.getMaxPosition()), 32, i - 8);
                double e = Math.max(1.0, d / (i - j));
                this.setScrollAmount(this.getScrollAmount() + dragY * e);
            }

            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        this.setScrollAmount(this.getScrollAmount() - scrollY * this.itemHeight / 2.0);
        return true;
    }

    /**
     * Sets the focus state of the GUI element.
     *
     * @param focused the focused GUI element.
     */
    @Override
    public void setFocused(@Nullable GuiEventListener focused) {
        super.setFocused(focused);
        int i = this.children.indexOf(focused);
        if (i >= 0) {
            E entry = this.children.get(i);
            this.setSelected(entry);
            if (this.minecraft.getLastInputType().isKeyboard()) {
                this.ensureVisible(entry);
            }
        }
    }

    @Nullable
    protected E nextEntry(ScreenDirection direction) {
        return this.nextEntry(direction, entry -> true);
    }

    @Nullable
    protected E nextEntry(ScreenDirection direction, Predicate<E> predicate) {
        return this.nextEntry(direction, predicate, this.getSelected());
    }

    @Nullable
    protected E nextEntry(ScreenDirection direction, Predicate<E> predicate, @Nullable E selected) {
        int i = switch (direction) {
            case RIGHT, LEFT -> 0;
            case UP -> -1;
            case DOWN -> 1;
        };
        if (!this.children().isEmpty() && i != 0) {
            int j;
            if (selected == null) {
                j = i > 0 ? 0 : this.children().size() - 1;
            } else {
                j = this.children().indexOf(selected) + i;
            }

            for (int k = j; k >= 0 && k < this.children.size(); k += i) {
                E entry = this.children().get(k);
                if (predicate.test(entry)) {
                    return entry;
                }
            }
        }

        return null;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseY >= this.getY() && mouseY <= this.getBottom() && mouseX >= this.getX() && mouseX <= this.getRight();
    }

    protected void renderListItems(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int i = this.getRowLeft();
        int j = this.getRowWidth();
        int k = this.itemHeight - 4;
        int l = this.getItemCount();

        for (int m = 0; m < l; m++) {
            int n = this.getRowTop(m);
            int o = this.getRowBottom(m);
            if (o >= this.getY() && n <= this.getBottom()) {
                this.renderItem(guiGraphics, mouseX, mouseY, partialTick, m, i, n, j, k);
            }
        }
    }

    protected void renderItem(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, int index, int left, int top, int width, int height) {
        E entry = this.getEntry(index);
        entry.renderBack(guiGraphics, index, top, left, width, height, mouseX, mouseY, Objects.equals(this.hovered, entry), partialTick);
        if (this.isSelectedItem(index)) {
            int i = this.isFocused() ? -1 : -8355712;
            this.renderSelection(guiGraphics, top, width, height, i, -16777216);
        }

        entry.render(guiGraphics, index, top, left, width, height, mouseX, mouseY, Objects.equals(this.hovered, entry), partialTick);
    }

    protected void renderSelection(GuiGraphics guiGraphics, int top, int width, int height, int outerColor, int innerColor) {
        int i = this.getX() + (this.width - width) / 2;
        int j = this.getX() + (this.width + width) / 2;
        guiGraphics.fill(i, top - 2, j, top + height + 2, outerColor);
        guiGraphics.fill(i + 1, top - 1, j - 1, top + height + 1, innerColor);
    }

    public int getRowLeft() {
        return this.getX() + this.width / 2 - this.getRowWidth() / 2 + 2;
    }

    private int getRealRowLeft() {
        return this.getX() + this.width / 2 - this.getRowWidth() / 2;
    }

    public int getRowRight() {
        return this.getRowLeft() + this.getRowWidth();
    }

    private int getRealRowRight() {
        return this.getRealRowLeft() + this.getRowWidth();
    }

    protected int getRowTop(int index) {
        return this.getY() + 4 - (int)this.getScrollAmount() + index * this.itemHeight + this.headerHeight;
    }

    protected int getRowBottom(int index) {
        return this.getRowTop(index) + this.itemHeight;
    }

    @Override
    public NarratableEntry.NarrationPriority narrationPriority() {
        if (this.isFocused()) {
            return NarratableEntry.NarrationPriority.FOCUSED;
        } else {
            return this.hovered != null ? NarratableEntry.NarrationPriority.HOVERED : NarratableEntry.NarrationPriority.NONE;
        }
    }

    @Nullable
    protected E remove(int index) {
        E entry = this.children.get(index);
        return this.removeEntry(this.children.get(index)) ? entry : null;
    }

    protected boolean removeEntry(E entry) {
        boolean bl = this.children.remove(entry);
        if (bl && entry == this.getSelected()) {
            this.setSelected(null);
        }

        return bl;
    }

    @Nullable
    protected E getHovered() {
        return this.hovered;
    }

    void bindEntryToSelf(AbstractSelectionList.Entry<E> entry) {
        entry.list = this;
    }

    protected void narrateListElementPosition(NarrationElementOutput narrationElementOutput, E entry) {
        List<E> list = this.children();
        if (list.size() > 1) {
            int i = list.indexOf(entry);
            if (i != -1) {
                narrationElementOutput.add(NarratedElementType.POSITION, Component.translatable("narrator.position.list", i + 1, list.size()));
            }
        }
    }

    protected abstract static class Entry<E extends AbstractSelectionList.Entry<E>> implements GuiEventListener {
        @Deprecated
        AbstractSelectionList<E> list;

        /**
         * Sets the focus state of the GUI element.
         *
         * @param focused {@code true} to apply focus, {@code false} to remove focus
         */
        @Override
        public void setFocused(boolean focused) {
        }

        @Override
        public boolean isFocused() {
            return this.list.getFocused() == this;
        }

        public abstract void render(GuiGraphics var1, int var2, int var3, int var4, int var5, int var6, int var7, int var8, boolean var9, float var10);

        public void renderBack(
            GuiGraphics guiGraphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean isMouseOver, float partialTick
        ) {
        }

        /**
         * Checks if the given mouse coordinates are over the GUI element.
         * <p>
         * @return {@code true} if the mouse is over the GUI element, {@code false} otherwise.
         *
         * @param mouseX the X coordinate of the mouse.
         * @param mouseY the Y coordinate of the mouse.
         */
        @Override
        public boolean isMouseOver(double mouseX, double mouseY) {
            return Objects.equals(this.list.getEntryAtPosition(mouseX, mouseY), this);
        }
    }

    class TrackedList extends AbstractList<E> {
        private final List<E> delegate = Lists.newArrayList();

        public E get(int index) {
            return this.delegate.get(index);
        }

        @Override
        public int size() {
            return this.delegate.size();
        }

        public E set(int index, E entry) {
            E entry2 = this.delegate.set(index, entry);
            AbstractSelectionList.this.bindEntryToSelf(entry);
            return entry2;
        }

        public void add(int index, E entry) {
            this.delegate.add(index, entry);
            AbstractSelectionList.this.bindEntryToSelf(entry);
        }

        public E remove(int index) {
            return this.delegate.remove(index);
        }
    }
}
