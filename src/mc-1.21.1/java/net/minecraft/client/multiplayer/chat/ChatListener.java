package net.minecraft.client.multiplayer.chat;

import com.google.common.collect.Queues;
import com.mojang.authlib.GameProfile;
import java.time.Instant;
import java.util.Deque;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FilterMask;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.util.StringDecomposer;
import org.apache.commons.lang3.StringUtils;

public class ChatListener {
    private static final Component CHAT_VALIDATION_ERROR = Component.translatable("chat.validation_error").withStyle(ChatFormatting.RED, ChatFormatting.ITALIC);
    private final Minecraft minecraft;
    private final Deque<ChatListener.Message> delayedMessageQueue = Queues.newArrayDeque();
    private long messageDelay;
    private long previousMessageTime;

    public ChatListener(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    public void tick() {
        if (this.messageDelay != 0L) {
            if (Util.getMillis() >= this.previousMessageTime + this.messageDelay) {
                ChatListener.Message message = this.delayedMessageQueue.poll();

                while (message != null && !message.accept()) {
                    message = this.delayedMessageQueue.poll();
                }
            }
        }
    }

    public void setMessageDelay(double delaySeconds) {
        long l = (long)(delaySeconds * 1000.0);
        if (l == 0L && this.messageDelay > 0L) {
            this.delayedMessageQueue.forEach(ChatListener.Message::accept);
            this.delayedMessageQueue.clear();
        }

        this.messageDelay = l;
    }

    public void acceptNextDelayedMessage() {
        this.delayedMessageQueue.remove().accept();
    }

    public long queueSize() {
        return this.delayedMessageQueue.size();
    }

    public void clearQueue() {
        this.delayedMessageQueue.forEach(ChatListener.Message::accept);
        this.delayedMessageQueue.clear();
    }

    public boolean removeFromDelayedMessageQueue(MessageSignature signature) {
        return this.delayedMessageQueue.removeIf(message -> signature.equals(message.signature()));
    }

    private boolean willDelayMessages() {
        return this.messageDelay > 0L && Util.getMillis() < this.previousMessageTime + this.messageDelay;
    }

    private void handleMessage(@Nullable MessageSignature signature, BooleanSupplier handler) {
        if (this.willDelayMessages()) {
            this.delayedMessageQueue.add(new ChatListener.Message(signature, handler));
        } else {
            handler.getAsBoolean();
        }
    }

    public void handlePlayerChatMessage(PlayerChatMessage chatMessage, GameProfile gameProfile, ChatType.Bound boundChatType) {
        boolean bl = this.minecraft.options.onlyShowSecureChat().get();
        PlayerChatMessage playerChatMessage = bl ? chatMessage.removeUnsignedContent() : chatMessage;
        Component component = boundChatType.decorate(playerChatMessage.decoratedContent());
        Instant instant = Instant.now();
        this.handleMessage(chatMessage.signature(), () -> {
            boolean bl2 = this.showMessageToPlayer(boundChatType, chatMessage, component, gameProfile, bl, instant);
            ClientPacketListener clientPacketListener = this.minecraft.getConnection();
            if (clientPacketListener != null) {
                clientPacketListener.markMessageAsProcessed(chatMessage, bl2);
            }

            return bl2;
        });
    }

    public void handleChatMessageError(UUID sender, ChatType.Bound boundChatType) {
        this.handleMessage(null, () -> {
            if (this.minecraft.isBlocked(sender)) {
                return false;
            }

            Component component = boundChatType.decorate(CHAT_VALIDATION_ERROR);
            this.minecraft.gui.getChat().addMessage(component, null, GuiMessageTag.chatError());
            this.previousMessageTime = Util.getMillis();
            return true;
        });
    }

    public void handleDisguisedChatMessage(Component message, ChatType.Bound boundChatType) {
        Instant instant = Instant.now();
        this.handleMessage(null, () -> {
            Component component2 = boundChatType.decorate(message);
            this.minecraft.gui.getChat().addMessage(component2);
            this.narrateChatMessage(boundChatType, message);
            this.logSystemMessage(component2, instant);
            this.previousMessageTime = Util.getMillis();
            return true;
        });
    }

    private boolean showMessageToPlayer(
        ChatType.Bound boundChatType,
        PlayerChatMessage chatMessage,
        Component decoratedServerContent,
        GameProfile gameProfile,
        boolean onlyShowSecureChat,
        Instant timestamp
    ) {
        ChatTrustLevel chatTrustLevel = this.evaluateTrustLevel(chatMessage, decoratedServerContent, timestamp);
        if (onlyShowSecureChat && chatTrustLevel.isNotSecure()) {
            return false;
        }

        if (!this.minecraft.isBlocked(chatMessage.sender()) && !chatMessage.isFullyFiltered()) {
            GuiMessageTag guiMessageTag = chatTrustLevel.createTag(chatMessage);
            MessageSignature messageSignature = chatMessage.signature();
            FilterMask filterMask = chatMessage.filterMask();
            if (filterMask.isEmpty()) {
                this.minecraft.gui.getChat().addMessage(decoratedServerContent, messageSignature, guiMessageTag);
                this.narrateChatMessage(boundChatType, chatMessage.decoratedContent());
            } else {
                Component component = filterMask.applyWithFormatting(chatMessage.signedContent());
                if (component != null) {
                    this.minecraft.gui.getChat().addMessage(boundChatType.decorate(component), messageSignature, guiMessageTag);
                    this.narrateChatMessage(boundChatType, component);
                }
            }

            this.logPlayerMessage(chatMessage, boundChatType, gameProfile, chatTrustLevel);
            this.previousMessageTime = Util.getMillis();
            return true;
        } else {
            return false;
        }
    }

    private void narrateChatMessage(ChatType.Bound boundChatType, Component message) {
        this.minecraft.getNarrator().sayChat(boundChatType.decorateNarration(message));
    }

    private ChatTrustLevel evaluateTrustLevel(PlayerChatMessage chatMessage, Component decoratedServerContent, Instant timestamp) {
        return this.isSenderLocalPlayer(chatMessage.sender()) ? ChatTrustLevel.SECURE : ChatTrustLevel.evaluate(chatMessage, decoratedServerContent, timestamp);
    }

    private void logPlayerMessage(PlayerChatMessage message, ChatType.Bound boundChatType, GameProfile gameProfile, ChatTrustLevel trustLevel) {
        ChatLog chatLog = this.minecraft.getReportingContext().chatLog();
        chatLog.push(LoggedChatMessage.player(gameProfile, message, trustLevel));
    }

    private void logSystemMessage(Component message, Instant timestamp) {
        ChatLog chatLog = this.minecraft.getReportingContext().chatLog();
        chatLog.push(LoggedChatMessage.system(message, timestamp));
    }

    public void handleSystemMessage(Component message, boolean isOverlay) {
        if (!this.minecraft.options.hideMatchedNames().get() || !this.minecraft.isBlocked(this.guessChatUUID(message))) {
            if (isOverlay) {
                this.minecraft.gui.setOverlayMessage(message, false);
            } else {
                this.minecraft.gui.getChat().addMessage(message);
                this.logSystemMessage(message, Instant.now());
            }

            this.minecraft.getNarrator().say(message);
        }
    }

    private UUID guessChatUUID(Component message) {
        String string = StringDecomposer.getPlainText(message);
        String string2 = StringUtils.substringBetween(string, "<", ">");
        return string2 == null ? Util.NIL_UUID : this.minecraft.getPlayerSocialManager().getDiscoveredUUID(string2);
    }

    private boolean isSenderLocalPlayer(UUID sender) {
        if (this.minecraft.isLocalServer() && this.minecraft.player != null) {
            UUID uUID = this.minecraft.player.getGameProfile().getId();
            return uUID.equals(sender);
        } else {
            return false;
        }
    }

    record Message(@Nullable MessageSignature signature, BooleanSupplier handler) {
        public boolean accept() {
            return this.handler.getAsBoolean();
        }
    }
}
