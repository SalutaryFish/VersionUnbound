package net.minecraft.client.gui.components;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.Message;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.context.ParsedArgument;
import com.mojang.brigadier.context.SuggestionContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec2;

public class CommandSuggestions {
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("(\\s+)");
    private static final Style UNPARSED_STYLE = Style.EMPTY.withColor(ChatFormatting.RED);
    private static final Style LITERAL_STYLE = Style.EMPTY.withColor(ChatFormatting.GRAY);
    private static final List<Style> ARGUMENT_STYLES = Stream.of(
            ChatFormatting.AQUA, ChatFormatting.YELLOW, ChatFormatting.GREEN, ChatFormatting.LIGHT_PURPLE, ChatFormatting.GOLD
        )
        .map(Style.EMPTY::withColor)
        .collect(ImmutableList.toImmutableList());
    final Minecraft minecraft;
    private final Screen screen;
    final EditBox input;
    final Font font;
    private final boolean commandsOnly;
    private final boolean onlyShowIfCursorPastError;
    final int lineStartOffset;
    final int suggestionLineLimit;
    final boolean anchorToBottom;
    final int fillColor;
    private final List<FormattedCharSequence> commandUsage = Lists.newArrayList();
    private int commandUsagePosition;
    private int commandUsageWidth;
    @Nullable
    private ParseResults<SharedSuggestionProvider> currentParse;
    @Nullable
    private CompletableFuture<Suggestions> pendingSuggestions;
    @Nullable
    private CommandSuggestions.SuggestionsList suggestions;
    private boolean allowSuggestions;
    boolean keepSuggestions;
    private boolean allowHiding = true;

    public CommandSuggestions(
        Minecraft minecraft,
        Screen screen,
        EditBox input,
        Font font,
        boolean commandsOnly,
        boolean onlyShowIfCursorPastError,
        int lineStartOffset,
        int suggestionLineLimit,
        boolean anchorToBottom,
        int fillColor
    ) {
        this.minecraft = minecraft;
        this.screen = screen;
        this.input = input;
        this.font = font;
        this.commandsOnly = commandsOnly;
        this.onlyShowIfCursorPastError = onlyShowIfCursorPastError;
        this.lineStartOffset = lineStartOffset;
        this.suggestionLineLimit = suggestionLineLimit;
        this.anchorToBottom = anchorToBottom;
        this.fillColor = fillColor;
        input.setFormatter(this::formatChat);
    }

    public void setAllowSuggestions(boolean autoSuggest) {
        this.allowSuggestions = autoSuggest;
        if (!autoSuggest) {
            this.suggestions = null;
        }
    }

    public void setAllowHiding(boolean allowHiding) {
        this.allowHiding = allowHiding;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean bl = this.suggestions != null;
        if (bl && this.suggestions.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }

        if (this.screen.getFocused() != this.input || keyCode != 258 || this.allowHiding && !bl) {
            return false;
        }

        this.showSuggestions(true);
        return true;
    }

    public boolean mouseScrolled(double delta) {
        return this.suggestions != null && this.suggestions.mouseScrolled(Mth.clamp(delta, -1.0, 1.0));
    }

    public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
        return this.suggestions != null && this.suggestions.mouseClicked((int)mouseX, (int)mouseY, mouseButton);
    }

    public void showSuggestions(boolean narrateFirstSuggestion) {
        if (this.pendingSuggestions != null && this.pendingSuggestions.isDone()) {
            Suggestions suggestions = this.pendingSuggestions.join();
            if (!suggestions.isEmpty()) {
                int i = 0;

                for (Suggestion suggestion : suggestions.getList()) {
                    i = Math.max(i, this.font.width(suggestion.getText()));
                }

                int j = Mth.clamp(this.input.getScreenX(suggestions.getRange().getStart()), 0, this.input.getScreenX(0) + this.input.getInnerWidth() - i);
                int k = this.anchorToBottom ? this.screen.height - 12 : 72;
                this.suggestions = new CommandSuggestions.SuggestionsList(j, k, i, this.sortSuggestions(suggestions), narrateFirstSuggestion);
            }
        }
    }

    public boolean isVisible() {
        return this.suggestions != null;
    }

    public Component getUsageNarration() {
        if (this.suggestions != null && this.suggestions.tabCycles) {
            return this.allowHiding
                ? Component.translatable("narration.suggestion.usage.cycle.hidable")
                : Component.translatable("narration.suggestion.usage.cycle.fixed");
        } else {
            return this.allowHiding
                ? Component.translatable("narration.suggestion.usage.fill.hidable")
                : Component.translatable("narration.suggestion.usage.fill.fixed");
        }
    }

    public void hide() {
        this.suggestions = null;
    }

    private List<Suggestion> sortSuggestions(Suggestions suggestions) {
        String string = this.input.getValue().substring(0, this.input.getCursorPosition());
        int i = getLastWordIndex(string);
        String string2 = string.substring(i).toLowerCase(Locale.ROOT);
        List<Suggestion> list = Lists.newArrayList();
        List<Suggestion> list2 = Lists.newArrayList();

        for (Suggestion suggestion : suggestions.getList()) {
            if (!suggestion.getText().startsWith(string2) && !suggestion.getText().startsWith("minecraft:" + string2)) {
                list2.add(suggestion);
            } else {
                list.add(suggestion);
            }
        }

        list.addAll(list2);
        return list;
    }

    public void updateCommandInfo() {
        String string = this.input.getValue();
        if (this.currentParse != null && !this.currentParse.getReader().getString().equals(string)) {
            this.currentParse = null;
        }

        if (!this.keepSuggestions) {
            this.input.setSuggestion(null);
            this.suggestions = null;
        }

        this.commandUsage.clear();
        StringReader stringReader = new StringReader(string);
        boolean bl = stringReader.canRead() && stringReader.peek() == '/';
        if (bl) {
            stringReader.skip();
        }

        boolean bl2 = this.commandsOnly || bl;
        int i = this.input.getCursorPosition();
        if (bl2) {
            CommandDispatcher<SharedSuggestionProvider> commandDispatcher = this.minecraft.player.connection.getCommands();
            if (this.currentParse == null) {
                this.currentParse = commandDispatcher.parse(stringReader, this.minecraft.player.connection.getSuggestionsProvider());
            }

            int j = this.onlyShowIfCursorPastError ? stringReader.getCursor() : 1;
            if (i >= j && (this.suggestions == null || !this.keepSuggestions)) {
                this.pendingSuggestions = commandDispatcher.getCompletionSuggestions(this.currentParse, i);
                this.pendingSuggestions.thenRun(() -> {
                    if (this.pendingSuggestions.isDone()) {
                        this.updateUsageInfo();
                    }
                });
            }
        } else {
            String string2 = string.substring(0, i);
            int j = getLastWordIndex(string2);
            Collection<String> collection = this.minecraft.player.connection.getSuggestionsProvider().getCustomTabSugggestions();
            this.pendingSuggestions = SharedSuggestionProvider.suggest(collection, new SuggestionsBuilder(string2, j));
        }
    }

    private static int getLastWordIndex(String text) {
        if (Strings.isNullOrEmpty(text)) {
            return 0;
        }

        int i = 0;
        Matcher matcher = WHITESPACE_PATTERN.matcher(text);

        while (matcher.find()) {
            i = matcher.end();
        }

        return i;
    }

    private static FormattedCharSequence getExceptionMessage(CommandSyntaxException exception) {
        Component component = ComponentUtils.fromMessage(exception.getRawMessage());
        String string = exception.getContext();
        return string == null
            ? component.getVisualOrderText()
            : Component.translatable("command.context.parse_error", component, exception.getCursor(), string).getVisualOrderText();
    }

    private void updateUsageInfo() {
        boolean bl = false;
        if (this.input.getCursorPosition() == this.input.getValue().length()) {
            if (this.pendingSuggestions.join().isEmpty() && !this.currentParse.getExceptions().isEmpty()) {
                int i = 0;

                for (Entry<CommandNode<SharedSuggestionProvider>, CommandSyntaxException> entry : this.currentParse.getExceptions().entrySet()) {
                    CommandSyntaxException commandSyntaxException = entry.getValue();
                    if (commandSyntaxException.getType() == CommandSyntaxException.BUILT_IN_EXCEPTIONS.literalIncorrect()) {
                        i++;
                    } else {
                        this.commandUsage.add(getExceptionMessage(commandSyntaxException));
                    }
                }

                if (i > 0) {
                    this.commandUsage.add(getExceptionMessage(CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand().create()));
                }
            } else if (this.currentParse.getReader().canRead()) {
                bl = true;
            }
        }

        this.commandUsagePosition = 0;
        this.commandUsageWidth = this.screen.width;
        if (this.commandUsage.isEmpty() && !this.fillNodeUsage(ChatFormatting.GRAY) && bl) {
            this.commandUsage.add(getExceptionMessage(Commands.getParseException(this.currentParse)));
        }

        this.suggestions = null;
        if (this.allowSuggestions && this.minecraft.options.autoSuggestions().get()) {
            this.showSuggestions(false);
        }
    }

    private boolean fillNodeUsage(ChatFormatting chatFormatting) {
        CommandContextBuilder<SharedSuggestionProvider> commandContextBuilder = this.currentParse.getContext();
        SuggestionContext<SharedSuggestionProvider> suggestionContext = commandContextBuilder.findSuggestionContext(this.input.getCursorPosition());
        Map<CommandNode<SharedSuggestionProvider>, String> map = this.minecraft
            .player
            .connection
            .getCommands()
            .getSmartUsage(suggestionContext.parent, this.minecraft.player.connection.getSuggestionsProvider());
        List<FormattedCharSequence> list = Lists.newArrayList();
        int i = 0;
        Style style = Style.EMPTY.withColor(chatFormatting);

        for (Entry<CommandNode<SharedSuggestionProvider>, String> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof LiteralCommandNode)) {
                list.add(FormattedCharSequence.forward(entry.getValue(), style));
                i = Math.max(i, this.font.width(entry.getValue()));
            }
        }

        if (!list.isEmpty()) {
            this.commandUsage.addAll(list);
            this.commandUsagePosition = Mth.clamp(
                this.input.getScreenX(suggestionContext.startPos), 0, this.input.getScreenX(0) + this.input.getInnerWidth() - i
            );
            this.commandUsageWidth = i;
            return true;
        } else {
            return false;
        }
    }

    private FormattedCharSequence formatChat(String command, int maxLength) {
        return this.currentParse != null ? formatText(this.currentParse, command, maxLength) : FormattedCharSequence.forward(command, Style.EMPTY);
    }

    @Nullable
    static String calculateSuggestionSuffix(String inputText, String suggestionText) {
        return suggestionText.startsWith(inputText) ? suggestionText.substring(inputText.length()) : null;
    }

    private static FormattedCharSequence formatText(ParseResults<SharedSuggestionProvider> provider, String command, int maxLength) {
        List<FormattedCharSequence> list = Lists.newArrayList();
        int i = 0;
        int j = -1;
        CommandContextBuilder<SharedSuggestionProvider> commandContextBuilder = provider.getContext().getLastChild();

        for (ParsedArgument<SharedSuggestionProvider, ?> parsedArgument : commandContextBuilder.getArguments().values()) {
            if (++j >= ARGUMENT_STYLES.size()) {
                j = 0;
            }

            int k = Math.max(parsedArgument.getRange().getStart() - maxLength, 0);
            if (k >= command.length()) {
                break;
            }

            int l = Math.min(parsedArgument.getRange().getEnd() - maxLength, command.length());
            if (l > 0) {
                list.add(FormattedCharSequence.forward(command.substring(i, k), LITERAL_STYLE));
                list.add(FormattedCharSequence.forward(command.substring(k, l), ARGUMENT_STYLES.get(j)));
                i = l;
            }
        }

        if (provider.getReader().canRead()) {
            int m = Math.max(provider.getReader().getCursor() - maxLength, 0);
            if (m < command.length()) {
                int n = Math.min(m + provider.getReader().getRemainingLength(), command.length());
                list.add(FormattedCharSequence.forward(command.substring(i, m), LITERAL_STYLE));
                list.add(FormattedCharSequence.forward(command.substring(m, n), UNPARSED_STYLE));
                i = n;
            }
        }

        list.add(FormattedCharSequence.forward(command.substring(i), LITERAL_STYLE));
        return FormattedCharSequence.composite(list);
    }

    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (!this.renderSuggestions(guiGraphics, mouseX, mouseY)) {
            this.renderUsage(guiGraphics);
        }
    }

    public boolean renderSuggestions(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (this.suggestions != null) {
            this.suggestions.render(guiGraphics, mouseX, mouseY);
            return true;
        } else {
            return false;
        }
    }

    public void renderUsage(GuiGraphics guiGraphics) {
        int i = 0;

        for (FormattedCharSequence formattedCharSequence : this.commandUsage) {
            int j = this.anchorToBottom ? this.screen.height - 14 - 13 - 12 * i : 72 + 12 * i;
            guiGraphics.fill(this.commandUsagePosition - 1, j, this.commandUsagePosition + this.commandUsageWidth + 1, j + 12, this.fillColor);
            guiGraphics.drawString(this.font, formattedCharSequence, this.commandUsagePosition, j + 2, -1);
            i++;
        }
    }

    public Component getNarrationMessage() {
        return this.suggestions != null ? CommonComponents.NEW_LINE.copy().append(this.suggestions.getNarrationMessage()) : CommonComponents.EMPTY;
    }

    public class SuggestionsList {
        private final Rect2i rect;
        private final String originalContents;
        private final List<Suggestion> suggestionList;
        private int offset;
        private int current;
        private Vec2 lastMouse = Vec2.ZERO;
        boolean tabCycles;
        private int lastNarratedEntry;

        SuggestionsList(final int xPos, final int yPos, final int width, final List<Suggestion> suggestionList, final boolean narrateFirstSuggestion) {
            int i = xPos - (CommandSuggestions.this.input.isBordered() ? 0 : 1);
            int j = CommandSuggestions.this.anchorToBottom
                ? yPos - 3 - Math.min(suggestionList.size(), CommandSuggestions.this.suggestionLineLimit) * 12
                : yPos - (CommandSuggestions.this.input.isBordered() ? 1 : 0);
            this.rect = new Rect2i(i, j, width + 1, Math.min(suggestionList.size(), CommandSuggestions.this.suggestionLineLimit) * 12);
            this.originalContents = CommandSuggestions.this.input.getValue();
            this.lastNarratedEntry = narrateFirstSuggestion ? -1 : 0;
            this.suggestionList = suggestionList;
            this.select(0);
        }

        public void render(GuiGraphics guiGraphics, int mouseX, int mouseY) {
            int i = Math.min(this.suggestionList.size(), CommandSuggestions.this.suggestionLineLimit);
            int j = -5592406;
            boolean bl = this.offset > 0;
            boolean bl2 = this.suggestionList.size() > this.offset + i;
            boolean bl3 = bl || bl2;
            boolean bl4 = this.lastMouse.x != mouseX || this.lastMouse.y != mouseY;
            if (bl4) {
                this.lastMouse = new Vec2(mouseX, mouseY);
            }

            if (bl3) {
                guiGraphics.fill(
                    this.rect.getX(), this.rect.getY() - 1, this.rect.getX() + this.rect.getWidth(), this.rect.getY(), CommandSuggestions.this.fillColor
                );
                guiGraphics.fill(
                    this.rect.getX(),
                    this.rect.getY() + this.rect.getHeight(),
                    this.rect.getX() + this.rect.getWidth(),
                    this.rect.getY() + this.rect.getHeight() + 1,
                    CommandSuggestions.this.fillColor
                );
                if (bl) {
                    for (int k = 0; k < this.rect.getWidth(); k++) {
                        if (k % 2 == 0) {
                            guiGraphics.fill(this.rect.getX() + k, this.rect.getY() - 1, this.rect.getX() + k + 1, this.rect.getY(), -1);
                        }
                    }
                }

                if (bl2) {
                    for (int k = 0; k < this.rect.getWidth(); k++) {
                        if (k % 2 == 0) {
                            guiGraphics.fill(
                                this.rect.getX() + k,
                                this.rect.getY() + this.rect.getHeight(),
                                this.rect.getX() + k + 1,
                                this.rect.getY() + this.rect.getHeight() + 1,
                                -1
                            );
                        }
                    }
                }
            }

            boolean bl5 = false;

            for (int l = 0; l < i; l++) {
                Suggestion suggestion = this.suggestionList.get(l + this.offset);
                guiGraphics.fill(
                    this.rect.getX(),
                    this.rect.getY() + 12 * l,
                    this.rect.getX() + this.rect.getWidth(),
                    this.rect.getY() + 12 * l + 12,
                    CommandSuggestions.this.fillColor
                );
                if (mouseX > this.rect.getX()
                    && mouseX < this.rect.getX() + this.rect.getWidth()
                    && mouseY > this.rect.getY() + 12 * l
                    && mouseY < this.rect.getY() + 12 * l + 12) {
                    if (bl4) {
                        this.select(l + this.offset);
                    }

                    bl5 = true;
                }

                guiGraphics.drawString(
                    CommandSuggestions.this.font,
                    suggestion.getText(),
                    this.rect.getX() + 1,
                    this.rect.getY() + 2 + 12 * l,
                    l + this.offset == this.current ? -256 : -5592406
                );
            }

            if (bl5) {
                Message message = this.suggestionList.get(this.current).getTooltip();
                if (message != null) {
                    guiGraphics.renderTooltip(CommandSuggestions.this.font, ComponentUtils.fromMessage(message), mouseX, mouseY);
                }
            }
        }

        public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
            if (!this.rect.contains(mouseX, mouseY)) {
                return false;
            }

            int i = (mouseY - this.rect.getY()) / 12 + this.offset;
            if (i >= 0 && i < this.suggestionList.size()) {
                this.select(i);
                this.useSuggestion();
            }

            return true;
        }

        public boolean mouseScrolled(double delta) {
            int i = (int)(
                CommandSuggestions.this.minecraft.mouseHandler.xpos()
                    * CommandSuggestions.this.minecraft.getWindow().getGuiScaledWidth()
                    / CommandSuggestions.this.minecraft.getWindow().getScreenWidth()
            );
            int j = (int)(
                CommandSuggestions.this.minecraft.mouseHandler.ypos()
                    * CommandSuggestions.this.minecraft.getWindow().getGuiScaledHeight()
                    / CommandSuggestions.this.minecraft.getWindow().getScreenHeight()
            );
            if (this.rect.contains(i, j)) {
                this.offset = Mth.clamp((int)(this.offset - delta), 0, Math.max(this.suggestionList.size() - CommandSuggestions.this.suggestionLineLimit, 0));
                return true;
            } else {
                return false;
            }
        }

        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (keyCode == 265) {
                this.cycle(-1);
                this.tabCycles = false;
                return true;
            }

            if (keyCode == 264) {
                this.cycle(1);
                this.tabCycles = false;
                return true;
            }

            if (keyCode == 258) {
                if (this.tabCycles) {
                    this.cycle(Screen.hasShiftDown() ? -1 : 1);
                }

                this.useSuggestion();
                return true;
            } else if (keyCode == 256) {
                CommandSuggestions.this.hide();
                CommandSuggestions.this.input.setSuggestion(null);
                return true;
            } else {
                return false;
            }
        }

        public void cycle(int change) {
            this.select(this.current + change);
            int i = this.offset;
            int j = this.offset + CommandSuggestions.this.suggestionLineLimit - 1;
            if (this.current < i) {
                this.offset = Mth.clamp(this.current, 0, Math.max(this.suggestionList.size() - CommandSuggestions.this.suggestionLineLimit, 0));
            } else if (this.current > j) {
                this.offset = Mth.clamp(
                    this.current + CommandSuggestions.this.lineStartOffset - CommandSuggestions.this.suggestionLineLimit,
                    0,
                    Math.max(this.suggestionList.size() - CommandSuggestions.this.suggestionLineLimit, 0)
                );
            }
        }

        public void select(int index) {
            this.current = index;
            if (this.current < 0) {
                this.current = this.current + this.suggestionList.size();
            }

            if (this.current >= this.suggestionList.size()) {
                this.current = this.current - this.suggestionList.size();
            }

            Suggestion suggestion = this.suggestionList.get(this.current);
            CommandSuggestions.this.input
                .setSuggestion(CommandSuggestions.calculateSuggestionSuffix(CommandSuggestions.this.input.getValue(), suggestion.apply(this.originalContents)));
            if (this.lastNarratedEntry != this.current) {
                CommandSuggestions.this.minecraft.getNarrator().sayNow(this.getNarrationMessage());
            }
        }

        public void useSuggestion() {
            Suggestion suggestion = this.suggestionList.get(this.current);
            CommandSuggestions.this.keepSuggestions = true;
            CommandSuggestions.this.input.setValue(suggestion.apply(this.originalContents));
            int i = suggestion.getRange().getStart() + suggestion.getText().length();
            CommandSuggestions.this.input.setCursorPosition(i);
            CommandSuggestions.this.input.setHighlightPos(i);
            this.select(this.current);
            CommandSuggestions.this.keepSuggestions = false;
            this.tabCycles = true;
        }

        Component getNarrationMessage() {
            this.lastNarratedEntry = this.current;
            Suggestion suggestion = this.suggestionList.get(this.current);
            Message message = suggestion.getTooltip();
            return message != null
                ? Component.translatable(
                    "narration.suggestion.tooltip", this.current + 1, this.suggestionList.size(), suggestion.getText(), Component.translationArg(message)
                )
                : Component.translatable("narration.suggestion", this.current + 1, this.suggestionList.size(), suggestion.getText());
        }
    }
}
