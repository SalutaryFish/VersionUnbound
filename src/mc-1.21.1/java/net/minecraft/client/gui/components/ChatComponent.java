package net.minecraft.client.gui.components;

import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.Optionull;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.Style;
import net.minecraft.util.ArrayListDeque;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.ChatVisiblity;
import org.slf4j.Logger;

public class ChatComponent {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_CHAT_HISTORY = 100;
    private static final int MESSAGE_NOT_FOUND = -1;
    private static final int MESSAGE_INDENT = 4;
    private static final int MESSAGE_TAG_MARGIN_LEFT = 4;
    private static final int BOTTOM_MARGIN = 40;
    private static final int TIME_BEFORE_MESSAGE_DELETION = 60;
    private static final Component DELETED_CHAT_MESSAGE = Component.translatable("chat.deleted_marker").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
    private final Minecraft minecraft;
    /**
     * A list of messages previously sent through the chat GUI
     */
    private final ArrayListDeque<String> recentChat = new ArrayListDeque<>(100);
    /**
     * Chat lines to be displayed in the chat box
     */
    private final List<GuiMessage> allMessages = Lists.newArrayList();
    /**
     * List of the ChatLines currently drawn
     */
    private final List<GuiMessage.Line> trimmedMessages = Lists.newArrayList();
    private int chatScrollbarPos;
    private boolean newMessageSinceScroll;
    private final List<ChatComponent.DelayedMessageDeletion> messageDeletionQueue = new ArrayList<>();

    public ChatComponent(Minecraft minecraft) {
        this.minecraft = minecraft;
        this.recentChat.addAll(minecraft.commandHistory().history());
    }

    public void tick() {
        if (!this.messageDeletionQueue.isEmpty()) {
            this.processMessageDeletionQueue();
        }
    }

    public void render(GuiGraphics guiGraphics, int tickCount, int mouseX, int mouseY, boolean focused) {
        if (!this.isChatHidden()) {
            int i = this.getLinesPerPage();
            int j = this.trimmedMessages.size();
            if (j > 0) {
                this.minecraft.getProfiler().push("chat");
                float f = (float)this.getScale();
                int k = Mth.ceil(this.getWidth() / f);
                int l = guiGraphics.guiHeight();
                guiGraphics.pose().pushPose();
                guiGraphics.pose().scale(f, f, 1.0F);
                guiGraphics.pose().translate(4.0F, 0.0F, 0.0F);
                int m = Mth.floor((l - 40) / f);
                int n = this.getMessageEndIndexAt(this.screenToChatX(mouseX), this.screenToChatY(mouseY));
                double d = this.minecraft.options.chatOpacity().get() * 0.9F + 0.1F;
                double e = this.minecraft.options.textBackgroundOpacity().get();
                double g = this.minecraft.options.chatLineSpacing().get();
                int o = this.getLineHeight();
                int p = (int)Math.round(-8.0 * (g + 1.0) + 4.0 * g);
                int q = 0;

                for (int r = 0; r + this.chatScrollbarPos < this.trimmedMessages.size() && r < i; r++) {
                    int s = r + this.chatScrollbarPos;
                    GuiMessage.Line line = this.trimmedMessages.get(s);
                    if (line != null) {
                        int t = tickCount - line.addedTime();
                        if (t < 200 || focused) {
                            double h = focused ? 1.0 : getTimeFactor(t);
                            int u = (int)(255.0 * h * d);
                            int v = (int)(255.0 * h * e);
                            q++;
                            if (u > 3) {
                                int w = 0;
                                int x = m - r * o;
                                int y = x + p;
                                guiGraphics.fill(-4, x - o, 0 + k + 4 + 4, x, v << 24);
                                GuiMessageTag guiMessageTag = line.tag();
                                if (guiMessageTag != null) {
                                    int z = guiMessageTag.indicatorColor() | u << 24;
                                    guiGraphics.fill(-4, x - o, -2, x, z);
                                    if (s == n && guiMessageTag.icon() != null) {
                                        int aa = this.getTagIconLeft(line);
                                        int ab = y + 9;
                                        this.drawTagIcon(guiGraphics, aa, ab, guiMessageTag.icon());
                                    }
                                }

                                guiGraphics.pose().pushPose();
                                guiGraphics.pose().translate(0.0F, 0.0F, 50.0F);
                                guiGraphics.drawString(this.minecraft.font, line.content(), 0, y, 16777215 + (u << 24));
                                guiGraphics.pose().popPose();
                            }
                        }
                    }
                }

                long ac = this.minecraft.getChatListener().queueSize();
                if (ac > 0L) {
                    int ad = (int)(128.0 * d);
                    int t = (int)(255.0 * e);
                    guiGraphics.pose().pushPose();
                    guiGraphics.pose().translate(0.0F, m, 0.0F);
                    guiGraphics.fill(-2, 0, k + 4, 9, t << 24);
                    guiGraphics.pose().translate(0.0F, 0.0F, 50.0F);
                    guiGraphics.drawString(this.minecraft.font, Component.translatable("chat.queue", ac), 0, 1, 16777215 + (ad << 24));
                    guiGraphics.pose().popPose();
                }

                if (focused) {
                    int ad = this.getLineHeight();
                    int t = j * ad;
                    int ae = q * ad;
                    int af = this.chatScrollbarPos * ae / j - m;
                    int u = ae * ae / t;
                    if (t != ae) {
                        int v = af > 0 ? 170 : 96;
                        int w = this.newMessageSinceScroll ? 13382451 : 3355562;
                        int x = k + 4;
                        guiGraphics.fill(x, -af, x + 2, -af - u, 100, w + (v << 24));
                        guiGraphics.fill(x + 2, -af, x + 1, -af - u, 100, 13421772 + (v << 24));
                    }
                }

                guiGraphics.pose().popPose();
                this.minecraft.getProfiler().pop();
            }
        }
    }

    private void drawTagIcon(GuiGraphics guiGraphics, int left, int bottom, GuiMessageTag.Icon tagIcon) {
        int i = bottom - tagIcon.height - 1;
        tagIcon.draw(guiGraphics, left, i);
    }

    private int getTagIconLeft(GuiMessage.Line line) {
        return this.minecraft.font.width(line.content()) + 4;
    }

    private boolean isChatHidden() {
        return this.minecraft.options.chatVisibility().get() == ChatVisiblity.HIDDEN;
    }

    private static double getTimeFactor(int counter) {
        double d = counter / 200.0;
        d = 1.0 - d;
        d *= 10.0;
        d = Mth.clamp(d, 0.0, 1.0);
        return d * d;
    }

    /**
     * Clears the chat.
     *
     * @param clearSentMsgHistory Whether to clear the user's sent message history
     */
    public void clearMessages(boolean clearSentMsgHistory) {
        this.minecraft.getChatListener().clearQueue();
        this.messageDeletionQueue.clear();
        this.trimmedMessages.clear();
        this.allMessages.clear();
        if (clearSentMsgHistory) {
            this.recentChat.clear();
            this.recentChat.addAll(this.minecraft.commandHistory().history());
        }
    }

    public void addMessage(Component chatComponent) {
        this.addMessage(chatComponent, null, this.minecraft.isSingleplayer() ? GuiMessageTag.systemSinglePlayer() : GuiMessageTag.system());
    }

    public void addMessage(Component chatComponent, @Nullable MessageSignature headerSignature, @Nullable GuiMessageTag tag) {
        GuiMessage guiMessage = new GuiMessage(this.minecraft.gui.getGuiTicks(), chatComponent, headerSignature, tag);
        this.logChatMessage(guiMessage);
        this.addMessageToDisplayQueue(guiMessage);
        this.addMessageToQueue(guiMessage);
    }

    private void logChatMessage(GuiMessage message) {
        String string = message.content().getString().replaceAll("\r", "\\\\r").replaceAll("\n", "\\\\n");
        String string2 = Optionull.map(message.tag(), GuiMessageTag::logTag);
        if (string2 != null) {
            LOGGER.info("[{}] [CHAT] {}", string2, string);
        } else {
            LOGGER.info("[CHAT] {}", string);
        }
    }

    private void addMessageToDisplayQueue(GuiMessage message) {
        int i = Mth.floor(this.getWidth() / this.getScale());
        GuiMessageTag.Icon icon = message.icon();
        if (icon != null) {
            i -= icon.width + 4 + 2;
        }

        List<FormattedCharSequence> list = ComponentRenderUtils.wrapComponents(message.content(), i, this.minecraft.font);
        boolean bl = this.isChatFocused();

        for (int j = 0; j < list.size(); j++) {
            FormattedCharSequence formattedCharSequence = list.get(j);
            if (bl && this.chatScrollbarPos > 0) {
                this.newMessageSinceScroll = true;
                this.scrollChat(1);
            }

            boolean bl2 = j == list.size() - 1;
            this.trimmedMessages.add(0, new GuiMessage.Line(message.addedTime(), formattedCharSequence, message.tag(), bl2));
        }

        while (this.trimmedMessages.size() > 100) {
            this.trimmedMessages.remove(this.trimmedMessages.size() - 1);
        }
    }

    private void addMessageToQueue(GuiMessage message) {
        this.allMessages.add(0, message);

        while (this.allMessages.size() > 100) {
            this.allMessages.remove(this.allMessages.size() - 1);
        }
    }

    private void processMessageDeletionQueue() {
        int i = this.minecraft.gui.getGuiTicks();
        this.messageDeletionQueue.removeIf(message -> i >= message.deletableAfter() ? this.deleteMessageOrDelay(message.signature()) == null : false);
    }

    public void deleteMessage(MessageSignature messageSignature) {
        ChatComponent.DelayedMessageDeletion delayedMessageDeletion = this.deleteMessageOrDelay(messageSignature);
        if (delayedMessageDeletion != null) {
            this.messageDeletionQueue.add(delayedMessageDeletion);
        }
    }

    @Nullable
    private ChatComponent.DelayedMessageDeletion deleteMessageOrDelay(MessageSignature messageSignature) {
        int i = this.minecraft.gui.getGuiTicks();
        ListIterator<GuiMessage> listIterator = this.allMessages.listIterator();

        while (listIterator.hasNext()) {
            GuiMessage guiMessage = listIterator.next();
            if (messageSignature.equals(guiMessage.signature())) {
                int j = guiMessage.addedTime() + 60;
                if (i >= j) {
                    listIterator.set(this.createDeletedMarker(guiMessage));
                    this.refreshTrimmedMessages();
                    return null;
                }

                return new ChatComponent.DelayedMessageDeletion(messageSignature, j);
            }
        }

        return null;
    }

    private GuiMessage createDeletedMarker(GuiMessage message) {
        return new GuiMessage(message.addedTime(), DELETED_CHAT_MESSAGE, null, GuiMessageTag.system());
    }

    public void rescaleChat() {
        this.resetChatScroll();
        this.refreshTrimmedMessages();
    }

    private void refreshTrimmedMessages() {
        this.trimmedMessages.clear();

        for (GuiMessage guiMessage : Lists.reverse(this.allMessages)) {
            this.addMessageToDisplayQueue(guiMessage);
        }
    }

    public ArrayListDeque<String> getRecentChat() {
        return this.recentChat;
    }

    /**
     * Adds this string to the list of sent messages, for recall using the up/down arrow keys
     */
    public void addRecentChat(String message) {
        if (!message.equals(this.recentChat.peekLast())) {
            if (this.recentChat.size() >= 100) {
                this.recentChat.removeFirst();
            }

            this.recentChat.addLast(message);
        }

        if (message.startsWith("/")) {
            this.minecraft.commandHistory().addCommand(message);
        }
    }

    public void resetChatScroll() {
        this.chatScrollbarPos = 0;
        this.newMessageSinceScroll = false;
    }

    public void scrollChat(int posInc) {
        this.chatScrollbarPos += posInc;
        int i = this.trimmedMessages.size();
        if (this.chatScrollbarPos > i - this.getLinesPerPage()) {
            this.chatScrollbarPos = i - this.getLinesPerPage();
        }

        if (this.chatScrollbarPos <= 0) {
            this.chatScrollbarPos = 0;
            this.newMessageSinceScroll = false;
        }
    }

    public boolean handleChatQueueClicked(double mouseX, double mouseY) {
        if (this.isChatFocused() && !this.minecraft.options.hideGui && !this.isChatHidden()) {
            ChatListener chatListener = this.minecraft.getChatListener();
            if (chatListener.queueSize() == 0L) {
                return false;
            } else {
                double d = mouseX - 2.0;
                double e = this.minecraft.getWindow().getGuiScaledHeight() - mouseY - 40.0;
                if (d <= Mth.floor(this.getWidth() / this.getScale()) && e < 0.0 && e > Mth.floor(-9.0 * this.getScale())) {
                    chatListener.acceptNextDelayedMessage();
                    return true;
                } else {
                    return false;
                }
            }
        } else {
            return false;
        }
    }

    @Nullable
    public Style getClickedComponentStyleAt(double mouseX, double mouseY) {
        double d = this.screenToChatX(mouseX);
        double e = this.screenToChatY(mouseY);
        int i = this.getMessageLineIndexAt(d, e);
        if (i >= 0 && i < this.trimmedMessages.size()) {
            GuiMessage.Line line = this.trimmedMessages.get(i);
            return this.minecraft.font.getSplitter().componentStyleAtWidth(line.content(), Mth.floor(d));
        } else {
            return null;
        }
    }

    @Nullable
    public GuiMessageTag getMessageTagAt(double mouseX, double mouseY) {
        double d = this.screenToChatX(mouseX);
        double e = this.screenToChatY(mouseY);
        int i = this.getMessageEndIndexAt(d, e);
        if (i >= 0 && i < this.trimmedMessages.size()) {
            GuiMessage.Line line = this.trimmedMessages.get(i);
            GuiMessageTag guiMessageTag = line.tag();
            if (guiMessageTag != null && this.hasSelectedMessageTag(d, line, guiMessageTag)) {
                return guiMessageTag;
            }
        }

        return null;
    }

    private boolean hasSelectedMessageTag(double x, GuiMessage.Line line, GuiMessageTag tag) {
        if (x < 0.0) {
            return true;
        }

        GuiMessageTag.Icon icon = tag.icon();
        if (icon == null) {
            return false;
        }

        int i = this.getTagIconLeft(line);
        int j = i + icon.width;
        return x >= i && x <= j;
    }

    private double screenToChatX(double x) {
        return x / this.getScale() - 4.0;
    }

    private double screenToChatY(double y) {
        double d = this.minecraft.getWindow().getGuiScaledHeight() - y - 40.0;
        return d / (this.getScale() * this.getLineHeight());
    }

    private int getMessageEndIndexAt(double mouseX, double mouseY) {
        int i = this.getMessageLineIndexAt(mouseX, mouseY);
        if (i == -1) {
            return -1;
        }

        while (i >= 0) {
            if (this.trimmedMessages.get(i).endOfEntry()) {
                return i;
            }

            i--;
        }

        return i;
    }

    private int getMessageLineIndexAt(double mouseX, double mouseY) {
        if (this.isChatFocused() && !this.isChatHidden()) {
            if (!(mouseX < -4.0) && !(mouseX > Mth.floor(this.getWidth() / this.getScale()))) {
                int i = Math.min(this.getLinesPerPage(), this.trimmedMessages.size());
                if (mouseY >= 0.0 && mouseY < i) {
                    int j = Mth.floor(mouseY + this.chatScrollbarPos);
                    if (j >= 0 && j < this.trimmedMessages.size()) {
                        return j;
                    }
                }

                return -1;
            } else {
                return -1;
            }
        } else {
            return -1;
        }
    }

    public boolean isChatFocused() {
        return this.minecraft.screen instanceof ChatScreen;
    }

    public int getWidth() {
        return getWidth(this.minecraft.options.chatWidth().get());
    }

    public int getHeight() {
        return getHeight(this.isChatFocused() ? this.minecraft.options.chatHeightFocused().get() : this.minecraft.options.chatHeightUnfocused().get());
    }

    public double getScale() {
        return this.minecraft.options.chatScale().get();
    }

    public static int getWidth(double width) {
        int i = 320;
        int j = 40;
        return Mth.floor(width * 280.0 + 40.0);
    }

    public static int getHeight(double height) {
        int i = 180;
        int j = 20;
        return Mth.floor(height * 160.0 + 20.0);
    }

    public static double defaultUnfocusedPct() {
        int i = 180;
        int j = 20;
        return 70.0 / (getHeight(1.0) - 20);
    }

    public int getLinesPerPage() {
        return this.getHeight() / this.getLineHeight();
    }

    private int getLineHeight() {
        return (int)(9.0 * (this.minecraft.options.chatLineSpacing().get() + 1.0));
    }

    public ChatComponent.State storeState() {
        return new ChatComponent.State(List.copyOf(this.allMessages), List.copyOf(this.recentChat), List.copyOf(this.messageDeletionQueue));
    }

    public void restoreState(ChatComponent.State state) {
        this.recentChat.clear();
        this.recentChat.addAll(state.history);
        this.messageDeletionQueue.clear();
        this.messageDeletionQueue.addAll(state.delayedMessageDeletions);
        this.allMessages.clear();
        this.allMessages.addAll(state.messages);
        this.refreshTrimmedMessages();
    }

    record DelayedMessageDeletion(MessageSignature signature, int deletableAfter) {
    }

    public static class State {
        final List<GuiMessage> messages;
        final List<String> history;
        final List<ChatComponent.DelayedMessageDeletion> delayedMessageDeletions;

        public State(List<GuiMessage> messages, List<String> history, List<ChatComponent.DelayedMessageDeletion> delayedMessageDeletions) {
            this.messages = messages;
            this.history = history;
            this.delayedMessageDeletions = delayedMessageDeletions;
        }
    }
}
