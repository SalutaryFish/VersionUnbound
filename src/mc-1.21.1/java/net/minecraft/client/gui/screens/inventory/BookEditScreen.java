package net.minecraft.client.gui.screens.inventory;

import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.font.TextFieldHelper;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ServerboundEditBookPacket;
import net.minecraft.server.network.Filterable;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.StringUtil;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WritableBookContent;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.commons.lang3.mutable.MutableInt;

public class BookEditScreen extends Screen {
    private static final int TEXT_WIDTH = 114;
    private static final int TEXT_HEIGHT = 128;
    private static final int IMAGE_WIDTH = 192;
    private static final int IMAGE_HEIGHT = 192;
    private static final Component EDIT_TITLE_LABEL = Component.translatable("book.editTitle");
    private static final Component FINALIZE_WARNING_LABEL = Component.translatable("book.finalizeWarning");
    private static final FormattedCharSequence BLACK_CURSOR = FormattedCharSequence.forward("_", Style.EMPTY.withColor(ChatFormatting.BLACK));
    private static final FormattedCharSequence GRAY_CURSOR = FormattedCharSequence.forward("_", Style.EMPTY.withColor(ChatFormatting.GRAY));
    private final Player owner;
    private final ItemStack book;
    /**
     * Whether the book's title or contents has been modified since being opened
     */
    private boolean isModified;
    /**
     * Determines if the signing screen is open
     */
    private boolean isSigning;
    /**
     * Update ticks since the gui was opened
     */
    private int frameTick;
    private int currentPage;
    private final List<String> pages = Lists.newArrayList();
    private String title = "";
    private final TextFieldHelper pageEdit = new TextFieldHelper(
        this::getCurrentPageText,
        this::setCurrentPageText,
        this::getClipboard,
        this::setClipboard,
        string -> string.length() < 1024 && this.font.wordWrapHeight(string, 114) <= 128
    );
    private final TextFieldHelper titleEdit = new TextFieldHelper(
        () -> this.title, title -> this.title = title, this::getClipboard, this::setClipboard, input -> input.length() < 16
    );
    /**
     * In milliseconds
     */
    private long lastClickTime;
    private int lastIndex = -1;
    private PageButton forwardButton;
    private PageButton backButton;
    private Button doneButton;
    private Button signButton;
    private Button finalizeButton;
    private Button cancelButton;
    private final InteractionHand hand;
    @Nullable
    private BookEditScreen.DisplayCache displayCache = BookEditScreen.DisplayCache.EMPTY;
    private Component pageMsg = CommonComponents.EMPTY;
    private final Component ownerText;

    public BookEditScreen(Player owner, ItemStack book, InteractionHand hand) {
        super(GameNarrator.NO_TITLE);
        this.owner = owner;
        this.book = book;
        this.hand = hand;
        WritableBookContent writableBookContent = book.get(DataComponents.WRITABLE_BOOK_CONTENT);
        if (writableBookContent != null) {
            writableBookContent.getPages(Minecraft.getInstance().isTextFilteringEnabled()).forEach(this.pages::add);
        }

        if (this.pages.isEmpty()) {
            this.pages.add("");
        }

        this.ownerText = Component.translatable("book.byAuthor", owner.getName()).withStyle(ChatFormatting.DARK_GRAY);
    }

    private void setClipboard(String clipboardValue) {
        if (this.minecraft != null) {
            TextFieldHelper.setClipboardContents(this.minecraft, clipboardValue);
        }
    }

    private String getClipboard() {
        return this.minecraft != null ? TextFieldHelper.getClipboardContents(this.minecraft) : "";
    }

    private int getNumPages() {
        return this.pages.size();
    }

    @Override
    public void tick() {
        super.tick();
        this.frameTick++;
    }

    @Override
    protected void init() {
        this.clearDisplayCache();
        this.signButton = this.addRenderableWidget(Button.builder(Component.translatable("book.signButton"), button -> {
            this.isSigning = true;
            this.updateButtonVisibility();
        }).bounds(this.width / 2 - 100, 196, 98, 20).build());
        this.doneButton = this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> {
            this.minecraft.setScreen(null);
            this.saveChanges(false);
        }).bounds(this.width / 2 + 2, 196, 98, 20).build());
        this.finalizeButton = this.addRenderableWidget(Button.builder(Component.translatable("book.finalizeButton"), button -> {
            if (this.isSigning) {
                this.saveChanges(true);
                this.minecraft.setScreen(null);
            }
        }).bounds(this.width / 2 - 100, 196, 98, 20).build());
        this.cancelButton = this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> {
            if (this.isSigning) {
                this.isSigning = false;
            }

            this.updateButtonVisibility();
        }).bounds(this.width / 2 + 2, 196, 98, 20).build());
        int i = (this.width - 192) / 2;
        int j = 2;
        this.forwardButton = this.addRenderableWidget(new PageButton(i + 116, 159, true, button -> this.pageForward(), true));
        this.backButton = this.addRenderableWidget(new PageButton(i + 43, 159, false, button -> this.pageBack(), true));
        this.updateButtonVisibility();
    }

    private void pageBack() {
        if (this.currentPage > 0) {
            this.currentPage--;
        }

        this.updateButtonVisibility();
        this.clearDisplayCacheAfterPageChange();
    }

    private void pageForward() {
        if (this.currentPage < this.getNumPages() - 1) {
            this.currentPage++;
        } else {
            this.appendPageToBook();
            if (this.currentPage < this.getNumPages() - 1) {
                this.currentPage++;
            }
        }

        this.updateButtonVisibility();
        this.clearDisplayCacheAfterPageChange();
    }

    private void updateButtonVisibility() {
        this.backButton.visible = !this.isSigning && this.currentPage > 0;
        this.forwardButton.visible = !this.isSigning;
        this.doneButton.visible = !this.isSigning;
        this.signButton.visible = !this.isSigning;
        this.cancelButton.visible = this.isSigning;
        this.finalizeButton.visible = this.isSigning;
        this.finalizeButton.active = !StringUtil.isBlank(this.title);
    }

    private void eraseEmptyTrailingPages() {
        ListIterator<String> listIterator = this.pages.listIterator(this.pages.size());

        while (listIterator.hasPrevious() && listIterator.previous().isEmpty()) {
            listIterator.remove();
        }
    }

    private void saveChanges(boolean publish) {
        if (this.isModified) {
            this.eraseEmptyTrailingPages();
            this.updateLocalCopy();
            int i = this.hand == InteractionHand.MAIN_HAND ? this.owner.getInventory().selected : 40;
            this.minecraft.getConnection().send(new ServerboundEditBookPacket(i, this.pages, publish ? Optional.of(this.title.trim()) : Optional.empty()));
        }
    }

    private void updateLocalCopy() {
        this.book.set(DataComponents.WRITABLE_BOOK_CONTENT, new WritableBookContent(this.pages.stream().map(Filterable::passThrough).toList()));
    }

    private void appendPageToBook() {
        if (this.getNumPages() < 100) {
            this.pages.add("");
            this.isModified = true;
        }
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
        } else if (this.isSigning) {
            return this.titleKeyPressed(keyCode, scanCode, modifiers);
        } else {
            boolean bl = this.bookKeyPressed(keyCode, scanCode, modifiers);
            if (bl) {
                this.clearDisplayCache();
                return true;
            } else {
                return false;
            }
        }
    }

    /**
     * Called when a character is typed within the GUI element.
     * <p>
     * @return {@code true} if the event is consumed, {@code false} otherwise.
     *
     * @param codePoint the code point of the typed character.
     * @param modifiers the keyboard modifiers.
     */
    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (super.charTyped(codePoint, modifiers)) {
            return true;
        }

        if (this.isSigning) {
            boolean bl = this.titleEdit.charTyped(codePoint);
            if (bl) {
                this.updateButtonVisibility();
                this.isModified = true;
                return true;
            } else {
                return false;
            }
        } else if (StringUtil.isAllowedChatCharacter(codePoint)) {
            this.pageEdit.insertText(Character.toString(codePoint));
            this.clearDisplayCache();
            return true;
        } else {
            return false;
        }
    }

    /**
     * Handles keypresses, clipboard functions, and page turning
     */
    private boolean bookKeyPressed(int keyCode, int scanCode, int modifiers) {
        if (Screen.isSelectAll(keyCode)) {
            this.pageEdit.selectAll();
            return true;
        }

        if (Screen.isCopy(keyCode)) {
            this.pageEdit.copy();
            return true;
        }

        if (Screen.isPaste(keyCode)) {
            this.pageEdit.paste();
            return true;
        }

        if (Screen.isCut(keyCode)) {
            this.pageEdit.cut();
            return true;
        }

        TextFieldHelper.CursorStep cursorStep = Screen.hasControlDown() ? TextFieldHelper.CursorStep.WORD : TextFieldHelper.CursorStep.CHARACTER;
        switch (keyCode) {
            case 257:
            case 335:
                this.pageEdit.insertText("\n");
                return true;
            case 259:
                this.pageEdit.removeFromCursor(-1, cursorStep);
                return true;
            case 261:
                this.pageEdit.removeFromCursor(1, cursorStep);
                return true;
            case 262:
                this.pageEdit.moveBy(1, Screen.hasShiftDown(), cursorStep);
                return true;
            case 263:
                this.pageEdit.moveBy(-1, Screen.hasShiftDown(), cursorStep);
                return true;
            case 264:
                this.keyDown();
                return true;
            case 265:
                this.keyUp();
                return true;
            case 266:
                this.backButton.onPress();
                return true;
            case 267:
                this.forwardButton.onPress();
                return true;
            case 268:
                this.keyHome();
                return true;
            case 269:
                this.keyEnd();
                return true;
            default:
                return false;
        }
    }

    private void keyUp() {
        this.changeLine(-1);
    }

    private void keyDown() {
        this.changeLine(1);
    }

    private void changeLine(int yChange) {
        int i = this.pageEdit.getCursorPos();
        int j = this.getDisplayCache().changeLine(i, yChange);
        this.pageEdit.setCursorPos(j, Screen.hasShiftDown());
    }

    private void keyHome() {
        if (Screen.hasControlDown()) {
            this.pageEdit.setCursorToStart(Screen.hasShiftDown());
        } else {
            int i = this.pageEdit.getCursorPos();
            int j = this.getDisplayCache().findLineStart(i);
            this.pageEdit.setCursorPos(j, Screen.hasShiftDown());
        }
    }

    private void keyEnd() {
        if (Screen.hasControlDown()) {
            this.pageEdit.setCursorToEnd(Screen.hasShiftDown());
        } else {
            BookEditScreen.DisplayCache displayCache = this.getDisplayCache();
            int i = this.pageEdit.getCursorPos();
            int j = displayCache.findLineEnd(i);
            this.pageEdit.setCursorPos(j, Screen.hasShiftDown());
        }
    }

    /**
     * Handles special keys pressed while editing the book's title
     */
    private boolean titleKeyPressed(int keyCode, int scanCode, int modifiers) {
        switch (keyCode) {
            case 257:
            case 335:
                if (!this.title.isEmpty()) {
                    this.saveChanges(true);
                    this.minecraft.setScreen(null);
                }

                return true;
            case 259:
                this.titleEdit.removeCharsFromCursor(-1);
                this.updateButtonVisibility();
                this.isModified = true;
                return true;
            default:
                return false;
        }
    }

    private String getCurrentPageText() {
        return this.currentPage >= 0 && this.currentPage < this.pages.size() ? this.pages.get(this.currentPage) : "";
    }

    private void setCurrentPageText(String text) {
        if (this.currentPage >= 0 && this.currentPage < this.pages.size()) {
            this.pages.set(this.currentPage, text);
            this.isModified = true;
            this.clearDisplayCache();
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
        this.setFocused(null);
        int i = (this.width - 192) / 2;
        int j = 2;
        if (this.isSigning) {
            boolean bl = this.frameTick / 6 % 2 == 0;
            FormattedCharSequence formattedCharSequence = FormattedCharSequence.composite(
                FormattedCharSequence.forward(this.title, Style.EMPTY), bl ? BLACK_CURSOR : GRAY_CURSOR
            );
            int k = this.font.width(EDIT_TITLE_LABEL);
            guiGraphics.drawString(this.font, EDIT_TITLE_LABEL, i + 36 + (114 - k) / 2, 34, 0, false);
            int l = this.font.width(formattedCharSequence);
            guiGraphics.drawString(this.font, formattedCharSequence, i + 36 + (114 - l) / 2, 50, 0, false);
            int m = this.font.width(this.ownerText);
            guiGraphics.drawString(this.font, this.ownerText, i + 36 + (114 - m) / 2, 60, 0, false);
            guiGraphics.drawWordWrap(this.font, FINALIZE_WARNING_LABEL, i + 36, 82, 114, 0);
        } else {
            int n = this.font.width(this.pageMsg);
            guiGraphics.drawString(this.font, this.pageMsg, i - n + 192 - 44, 18, 0, false);
            BookEditScreen.DisplayCache displayCache = this.getDisplayCache();

            for (BookEditScreen.LineInfo lineInfo : displayCache.lines) {
                guiGraphics.drawString(this.font, lineInfo.asComponent, lineInfo.x, lineInfo.y, -16777216, false);
            }

            this.renderHighlight(guiGraphics, displayCache.selection);
            this.renderCursor(guiGraphics, displayCache.cursor, displayCache.cursorAtEnd);
        }
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(guiGraphics);
        guiGraphics.blit(BookViewScreen.BOOK_LOCATION, (this.width - 192) / 2, 2, 0, 0, 192, 192);
    }

    private void renderCursor(GuiGraphics guiGraphics, BookEditScreen.Pos2i cursorPos, boolean isEndOfText) {
        if (this.frameTick / 6 % 2 == 0) {
            cursorPos = this.convertLocalToScreen(cursorPos);
            if (!isEndOfText) {
                guiGraphics.fill(cursorPos.x, cursorPos.y - 1, cursorPos.x + 1, cursorPos.y + 9, -16777216);
            } else {
                guiGraphics.drawString(this.font, "_", cursorPos.x, cursorPos.y, 0, false);
            }
        }
    }

    private void renderHighlight(GuiGraphics guiGraphics, Rect2i[] highlightAreas) {
        for (Rect2i rect2i : highlightAreas) {
            int i = rect2i.getX();
            int j = rect2i.getY();
            int k = i + rect2i.getWidth();
            int l = j + rect2i.getHeight();
            guiGraphics.fill(RenderType.guiTextHighlight(), i, j, k, l, -16776961);
        }
    }

    private BookEditScreen.Pos2i convertScreenToLocal(BookEditScreen.Pos2i screenPos) {
        return new BookEditScreen.Pos2i(screenPos.x - (this.width - 192) / 2 - 36, screenPos.y - 32);
    }

    private BookEditScreen.Pos2i convertLocalToScreen(BookEditScreen.Pos2i localScreenPos) {
        return new BookEditScreen.Pos2i(localScreenPos.x + (this.width - 192) / 2 + 36, localScreenPos.y + 32);
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
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        if (button == 0) {
            long l = Util.getMillis();
            BookEditScreen.DisplayCache displayCache = this.getDisplayCache();
            int i = displayCache.getIndexAtPosition(this.font, this.convertScreenToLocal(new BookEditScreen.Pos2i((int)mouseX, (int)mouseY)));
            if (i >= 0) {
                if (i != this.lastIndex || l - this.lastClickTime >= 250L) {
                    this.pageEdit.setCursorPos(i, Screen.hasShiftDown());
                } else if (!this.pageEdit.isSelecting()) {
                    this.selectWord(i);
                } else {
                    this.pageEdit.selectAll();
                }

                this.clearDisplayCache();
            }

            this.lastIndex = i;
            this.lastClickTime = l;
        }

        return true;
    }

    private void selectWord(int index) {
        String string = this.getCurrentPageText();
        this.pageEdit.setSelectionRange(StringSplitter.getWordPosition(string, -1, index, false), StringSplitter.getWordPosition(string, 1, index, false));
    }

    /**
     * Called when the mouse is dragged within the GUI element.
     * <p>
     * @return {@code true} if the event is consumed, {@code false} otherwise.
     *
     * @param mouseX the X coordinate of the mouse.
     * @param mouseY the Y coordinate of the mouse.
     * @param button the button that is being dragged.
     * @param dragX  the X distance of the drag.
     * @param dragY  the Y distance of the drag.
     */
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (super.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }

        if (button == 0) {
            BookEditScreen.DisplayCache displayCache = this.getDisplayCache();
            int i = displayCache.getIndexAtPosition(this.font, this.convertScreenToLocal(new BookEditScreen.Pos2i((int)mouseX, (int)mouseY)));
            this.pageEdit.setCursorPos(i, true);
            this.clearDisplayCache();
        }

        return true;
    }

    private BookEditScreen.DisplayCache getDisplayCache() {
        if (this.displayCache == null) {
            this.displayCache = this.rebuildDisplayCache();
            this.pageMsg = Component.translatable("book.pageIndicator", this.currentPage + 1, this.getNumPages());
        }

        return this.displayCache;
    }

    private void clearDisplayCache() {
        this.displayCache = null;
    }

    private void clearDisplayCacheAfterPageChange() {
        this.pageEdit.setCursorToEnd();
        this.clearDisplayCache();
    }

    private BookEditScreen.DisplayCache rebuildDisplayCache() {
        String string = this.getCurrentPageText();
        if (string.isEmpty()) {
            return BookEditScreen.DisplayCache.EMPTY;
        }

        int i = this.pageEdit.getCursorPos();
        int j = this.pageEdit.getSelectionPos();
        IntList intList = new IntArrayList();
        List<BookEditScreen.LineInfo> list = Lists.newArrayList();
        MutableInt mutableInt = new MutableInt();
        MutableBoolean mutableBoolean = new MutableBoolean();
        StringSplitter stringSplitter = this.font.getSplitter();
        stringSplitter.splitLines(string, 114, Style.EMPTY, true, (style, ix, jx) -> {
            int k = mutableInt.getAndIncrement();
            String string2x = string.substring(ix, jx);
            mutableBoolean.setValue(string2x.endsWith("\n"));
            String string3 = StringUtils.stripEnd(string2x, " \n");
            int lx = k * 9;
            BookEditScreen.Pos2i pos2ix = this.convertLocalToScreen(new BookEditScreen.Pos2i(0, lx));
            intList.add(ix);
            list.add(new BookEditScreen.LineInfo(style, string3, pos2ix.x, pos2ix.y));
        });
        int[] is = intList.toIntArray();
        boolean bl = i == string.length();
        BookEditScreen.Pos2i pos2i;
        if (bl && mutableBoolean.isTrue()) {
            pos2i = new BookEditScreen.Pos2i(0, list.size() * 9);
        } else {
            int k = findLineFromPos(is, i);
            int l = this.font.width(string.substring(is[k], i));
            pos2i = new BookEditScreen.Pos2i(l, k * 9);
        }

        List<Rect2i> list2 = Lists.newArrayList();
        if (i != j) {
            int l = Math.min(i, j);
            int m = Math.max(i, j);
            int n = findLineFromPos(is, l);
            int o = findLineFromPos(is, m);
            if (n == o) {
                int p = n * 9;
                int q = is[n];
                list2.add(this.createPartialLineSelection(string, stringSplitter, l, m, p, q));
            } else {
                int p = n + 1 > is.length ? string.length() : is[n + 1];
                list2.add(this.createPartialLineSelection(string, stringSplitter, l, p, n * 9, is[n]));

                for (int q = n + 1; q < o; q++) {
                    int r = q * 9;
                    String string2 = string.substring(is[q], is[q + 1]);
                    int s = (int)stringSplitter.stringWidth(string2);
                    list2.add(this.createSelection(new BookEditScreen.Pos2i(0, r), new BookEditScreen.Pos2i(s, r + 9)));
                }

                list2.add(this.createPartialLineSelection(string, stringSplitter, is[o], m, o * 9, is[o]));
            }
        }

        return new BookEditScreen.DisplayCache(string, pos2i, bl, is, list.toArray(new BookEditScreen.LineInfo[0]), list2.toArray(new Rect2i[0]));
    }

    static int findLineFromPos(int[] lineStarts, int find) {
        int i = Arrays.binarySearch(lineStarts, find);
        return i < 0 ? -(i + 2) : i;
    }

    private Rect2i createPartialLineSelection(String input, StringSplitter splitter, int startPos, int endPos, int y, int lineStart) {
        String string = input.substring(lineStart, startPos);
        String string2 = input.substring(lineStart, endPos);
        BookEditScreen.Pos2i pos2i = new BookEditScreen.Pos2i((int)splitter.stringWidth(string), y);
        BookEditScreen.Pos2i pos2i2 = new BookEditScreen.Pos2i((int)splitter.stringWidth(string2), y + 9);
        return this.createSelection(pos2i, pos2i2);
    }

    private Rect2i createSelection(BookEditScreen.Pos2i corner1, BookEditScreen.Pos2i corner2) {
        BookEditScreen.Pos2i pos2i = this.convertLocalToScreen(corner1);
        BookEditScreen.Pos2i pos2i2 = this.convertLocalToScreen(corner2);
        int i = Math.min(pos2i.x, pos2i2.x);
        int j = Math.max(pos2i.x, pos2i2.x);
        int k = Math.min(pos2i.y, pos2i2.y);
        int l = Math.max(pos2i.y, pos2i2.y);
        return new Rect2i(i, k, j - i, l - k);
    }

    static class DisplayCache {
        static final BookEditScreen.DisplayCache EMPTY = new BookEditScreen.DisplayCache(
            "",
            new BookEditScreen.Pos2i(0, 0),
            true,
            new int[]{0},
            new BookEditScreen.LineInfo[]{new BookEditScreen.LineInfo(Style.EMPTY, "", 0, 0)},
            new Rect2i[0]
        );
        private final String fullText;
        final BookEditScreen.Pos2i cursor;
        final boolean cursorAtEnd;
        private final int[] lineStarts;
        final BookEditScreen.LineInfo[] lines;
        final Rect2i[] selection;

        public DisplayCache(
            String fullText, BookEditScreen.Pos2i cursor, boolean cursorAtEnd, int[] lineStarts, BookEditScreen.LineInfo[] lines, Rect2i[] selection
        ) {
            this.fullText = fullText;
            this.cursor = cursor;
            this.cursorAtEnd = cursorAtEnd;
            this.lineStarts = lineStarts;
            this.lines = lines;
            this.selection = selection;
        }

        public int getIndexAtPosition(Font font, BookEditScreen.Pos2i cursorPosition) {
            int i = cursorPosition.y / 9;
            if (i < 0) {
                return 0;
            }

            if (i >= this.lines.length) {
                return this.fullText.length();
            }

            BookEditScreen.LineInfo lineInfo = this.lines[i];
            return this.lineStarts[i] + font.getSplitter().plainIndexAtWidth(lineInfo.contents, cursorPosition.x, lineInfo.style);
        }

        public int changeLine(int xChange, int yChange) {
            int i = BookEditScreen.findLineFromPos(this.lineStarts, xChange);
            int j = i + yChange;
            int m;
            if (0 <= j && j < this.lineStarts.length) {
                int k = xChange - this.lineStarts[i];
                int l = this.lines[j].contents.length();
                m = this.lineStarts[j] + Math.min(k, l);
            } else {
                m = xChange;
            }

            return m;
        }

        public int findLineStart(int line) {
            int i = BookEditScreen.findLineFromPos(this.lineStarts, line);
            return this.lineStarts[i];
        }

        public int findLineEnd(int line) {
            int i = BookEditScreen.findLineFromPos(this.lineStarts, line);
            return this.lineStarts[i] + this.lines[i].contents.length();
        }
    }

    static class LineInfo {
        final Style style;
        final String contents;
        final Component asComponent;
        final int x;
        final int y;

        public LineInfo(Style style, String contents, int x, int y) {
            this.style = style;
            this.contents = contents;
            this.x = x;
            this.y = y;
            this.asComponent = Component.literal(contents).setStyle(style);
        }
    }

    static class Pos2i {
        public final int x;
        public final int y;

        Pos2i(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }
}
