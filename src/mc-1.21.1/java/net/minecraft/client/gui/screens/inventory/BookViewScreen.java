package net.minecraft.client.gui.screens.inventory;

import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.component.WrittenBookContent;

public class BookViewScreen extends Screen {
    public static final int PAGE_INDICATOR_TEXT_Y_OFFSET = 16;
    public static final int PAGE_TEXT_X_OFFSET = 36;
    public static final int PAGE_TEXT_Y_OFFSET = 30;
    public static final BookViewScreen.BookAccess EMPTY_ACCESS = new BookViewScreen.BookAccess(List.of());
    public static final ResourceLocation BOOK_LOCATION = ResourceLocation.withDefaultNamespace("textures/gui/book.png");
    protected static final int TEXT_WIDTH = 114;
    protected static final int TEXT_HEIGHT = 128;
    protected static final int IMAGE_WIDTH = 192;
    protected static final int IMAGE_HEIGHT = 192;
    private BookViewScreen.BookAccess bookAccess;
    private int currentPage;
    /**
     * Holds a copy of the page text, split into page width lines
     */
    private List<FormattedCharSequence> cachedPageComponents = Collections.emptyList();
    private int cachedPage = -1;
    private Component pageMsg = CommonComponents.EMPTY;
    private PageButton forwardButton;
    private PageButton backButton;
    /**
     * Determines if a sound is played when the page is turned
     */
    private final boolean playTurnSound;

    public BookViewScreen(BookViewScreen.BookAccess bookAccess) {
        this(bookAccess, true);
    }

    public BookViewScreen() {
        this(EMPTY_ACCESS, false);
    }

    private BookViewScreen(BookViewScreen.BookAccess bookAccess, boolean playTurnSound) {
        super(GameNarrator.NO_TITLE);
        this.bookAccess = bookAccess;
        this.playTurnSound = playTurnSound;
    }

    public void setBookAccess(BookViewScreen.BookAccess bookAccess) {
        this.bookAccess = bookAccess;
        this.currentPage = Mth.clamp(this.currentPage, 0, bookAccess.getPageCount());
        this.updateButtonVisibility();
        this.cachedPage = -1;
    }

    /**
     * Moves the book to the specified page and returns true if it exists, {@code false} otherwise.
     */
    public boolean setPage(int pageNum) {
        int i = Mth.clamp(pageNum, 0, this.bookAccess.getPageCount() - 1);
        if (i != this.currentPage) {
            this.currentPage = i;
            this.updateButtonVisibility();
            this.cachedPage = -1;
            return true;
        } else {
            return false;
        }
    }

    /**
     * I'm not sure why this exists. The function it calls is public and does all the work.
     */
    protected boolean forcePage(int pageNum) {
        return this.setPage(pageNum);
    }

    @Override
    protected void init() {
        this.createMenuControls();
        this.createPageControlButtons();
    }

    protected void createMenuControls() {
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose()).bounds(this.width / 2 - 100, 196, 200, 20).build());
    }

    protected void createPageControlButtons() {
        int i = (this.width - 192) / 2;
        int j = 2;
        this.forwardButton = this.addRenderableWidget(new PageButton(i + 116, 159, true, button -> this.pageForward(), this.playTurnSound));
        this.backButton = this.addRenderableWidget(new PageButton(i + 43, 159, false, button -> this.pageBack(), this.playTurnSound));
        this.updateButtonVisibility();
    }

    private int getNumPages() {
        return this.bookAccess.getPageCount();
    }

    protected void pageBack() {
        if (this.currentPage > 0) {
            this.currentPage--;
        }

        this.updateButtonVisibility();
    }

    protected void pageForward() {
        if (this.currentPage < this.getNumPages() - 1) {
            this.currentPage++;
        }

        this.updateButtonVisibility();
    }

    private void updateButtonVisibility() {
        this.forwardButton.visible = this.currentPage < this.getNumPages() - 1;
        this.backButton.visible = this.currentPage > 0;
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
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }

        switch (keyCode) {
            case 266:
                this.backButton.onPress();
                return true;
            case 267:
                this.forwardButton.onPress();
                return true;
            default:
                return false;
        }
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
        int i = (this.width - 192) / 2;
        int j = 2;
        if (this.cachedPage != this.currentPage) {
            FormattedText formattedText = this.bookAccess.getPage(this.currentPage);
            this.cachedPageComponents = this.font.split(formattedText, 114);
            this.pageMsg = Component.translatable("book.pageIndicator", this.currentPage + 1, Math.max(this.getNumPages(), 1));
        }

        this.cachedPage = this.currentPage;
        int k = this.font.width(this.pageMsg);
        guiGraphics.drawString(this.font, this.pageMsg, i - k + 192 - 44, 18, 0, false);
        int l = Math.min(128 / 9, this.cachedPageComponents.size());

        for (int m = 0; m < l; m++) {
            FormattedCharSequence formattedCharSequence = this.cachedPageComponents.get(m);
            guiGraphics.drawString(this.font, formattedCharSequence, i + 36, 32 + m * 9, 0, false);
        }

        Style style = this.getClickedComponentStyleAt(mouseX, mouseY);
        if (style != null) {
            guiGraphics.renderComponentHoverEffect(this.font, style, mouseX, mouseY);
        }
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(guiGraphics);
        guiGraphics.blit(BOOK_LOCATION, (this.width - 192) / 2, 2, 0, 0, 192, 192);
    }

    /**
     * Called when a mouse button is clicked within the GUI element.
     * <p>
     * @return {@code true} if the event is consumed, {@code false} otherwise.
     *
     * @param mouseX the X coordinate of the mouse.
     * @param mouseY the Y coordinate of the mouse.
     * @param button the button that was clicked.
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            Style style = this.getClickedComponentStyleAt(mouseX, mouseY);
            if (style != null && this.handleComponentClicked(style)) {
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean handleComponentClicked(Style style) {
        ClickEvent clickEvent = style.getClickEvent();
        if (clickEvent == null) {
            return false;
        }

        if (clickEvent.getAction() == ClickEvent.Action.CHANGE_PAGE) {
            String string = clickEvent.getValue();

            try {
                int i = Integer.parseInt(string) - 1;
                return this.forcePage(i);
            } catch (Exception var5) {
                return false;
            }
        } else {
            boolean bl = super.handleComponentClicked(style);
            if (bl && clickEvent.getAction() == ClickEvent.Action.RUN_COMMAND) {
                this.closeScreen();
            }

            return bl;
        }
    }

    protected void closeScreen() {
        this.minecraft.setScreen(null);
    }

    @Nullable
    public Style getClickedComponentStyleAt(double mouseX, double mouseY) {
        if (this.cachedPageComponents.isEmpty()) {
            return null;
        }

        int i = Mth.floor(mouseX - (this.width - 192) / 2 - 36.0);
        int j = Mth.floor(mouseY - 2.0 - 30.0);
        if (i >= 0 && j >= 0) {
            int k = Math.min(128 / 9, this.cachedPageComponents.size());
            if (i <= 114 && j < 9 * k + k) {
                int l = j / 9;
                if (l >= 0 && l < this.cachedPageComponents.size()) {
                    FormattedCharSequence formattedCharSequence = this.cachedPageComponents.get(l);
                    return this.minecraft.font.getSplitter().componentStyleAtWidth(formattedCharSequence, i);
                } else {
                    return null;
                }
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    public record BookAccess(List<Component> pages) {
        public int getPageCount() {
            return this.pages.size();
        }

        public FormattedText getPage(int page) {
            return page >= 0 && page < this.getPageCount() ? this.pages.get(page) : FormattedText.EMPTY;
        }

        @Nullable
        public static BookViewScreen.BookAccess fromItem(ItemStack stack) {
            boolean bl = Minecraft.getInstance().isTextFilteringEnabled();
            WrittenBookContent writtenBookContent = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
            if (writtenBookContent != null) {
                return new BookViewScreen.BookAccess(writtenBookContent.getPages(bl));
            }

            WritableBookContent writableBookContent = stack.get(DataComponents.WRITABLE_BOOK_CONTENT);
            return writableBookContent != null ? new BookViewScreen.BookAccess(writableBookContent.getPages(bl).map(Component::literal).toList()) : null;
        }
    }
}
