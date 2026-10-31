package net.minecraft.client.multiplayer.chat;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.util.ExtraCodecs;

public interface LoggedChatMessage extends LoggedChatEvent {
    static LoggedChatMessage.Player player(GameProfile profile, PlayerChatMessage message, ChatTrustLevel trustLevel) {
        return new LoggedChatMessage.Player(profile, message, trustLevel);
    }

    static LoggedChatMessage.System system(Component message, Instant timestamp) {
        return new LoggedChatMessage.System(message, timestamp);
    }

    Component toContentComponent();

    default Component toNarrationComponent() {
        return this.toContentComponent();
    }

    boolean canReport(UUID var1);

    record Player(GameProfile profile, PlayerChatMessage message, ChatTrustLevel trustLevel) implements LoggedChatMessage {
        public static final MapCodec<LoggedChatMessage.Player> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    ExtraCodecs.GAME_PROFILE.fieldOf("profile").forGetter(LoggedChatMessage.Player::profile),
                    PlayerChatMessage.MAP_CODEC.forGetter(LoggedChatMessage.Player::message),
                    ChatTrustLevel.CODEC.optionalFieldOf("trust_level", ChatTrustLevel.SECURE).forGetter(LoggedChatMessage.Player::trustLevel)
                )
                .apply(instance, LoggedChatMessage.Player::new)
        );
        private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT);

        @Override
        public Component toContentComponent() {
            if (!this.message.filterMask().isEmpty()) {
                Component component = this.message.filterMask().applyWithFormatting(this.message.signedContent());
                return component != null ? component : Component.empty();
            } else {
                return this.message.decoratedContent();
            }
        }

        @Override
        public Component toNarrationComponent() {
            Component component = this.toContentComponent();
            Component component2 = this.getTimeComponent();
            return Component.translatable("gui.chatSelection.message.narrate", this.profile.getName(), component, component2);
        }

        public Component toHeadingComponent() {
            Component component = this.getTimeComponent();
            return Component.translatable("gui.chatSelection.heading", this.profile.getName(), component);
        }

        private Component getTimeComponent() {
            LocalDateTime localDateTime = LocalDateTime.ofInstant(this.message.timeStamp(), ZoneOffset.systemDefault());
            return Component.literal(localDateTime.format(TIME_FORMATTER)).withStyle(ChatFormatting.ITALIC, ChatFormatting.GRAY);
        }

        @Override
        public boolean canReport(UUID uuid) {
            return this.message.hasSignatureFrom(uuid);
        }

        public UUID profileId() {
            return this.profile.getId();
        }

        @Override
        public LoggedChatEvent.Type type() {
            return LoggedChatEvent.Type.PLAYER;
        }
    }

    record System(Component message, Instant timeStamp) implements LoggedChatMessage {
        public static final MapCodec<LoggedChatMessage.System> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    ComponentSerialization.CODEC.fieldOf("message").forGetter(LoggedChatMessage.System::message),
                    ExtraCodecs.INSTANT_ISO8601.fieldOf("time_stamp").forGetter(LoggedChatMessage.System::timeStamp)
                )
                .apply(instance, LoggedChatMessage.System::new)
        );

        @Override
        public Component toContentComponent() {
            return this.message;
        }

        @Override
        public boolean canReport(UUID uuid) {
            return false;
        }

        @Override
        public LoggedChatEvent.Type type() {
            return LoggedChatEvent.Type.SYSTEM;
        }
    }
}
