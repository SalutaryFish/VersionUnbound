package net.minecraft.network.protocol.game;

import com.google.common.base.MoreObjects;
import com.mojang.authlib.GameProfile;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.Optionull;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.RemoteChatSession;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

public class ClientboundPlayerInfoUpdatePacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundPlayerInfoUpdatePacket> STREAM_CODEC = Packet.codec(
        ClientboundPlayerInfoUpdatePacket::write, ClientboundPlayerInfoUpdatePacket::new
    );
    private final EnumSet<ClientboundPlayerInfoUpdatePacket.Action> actions;
    private final List<ClientboundPlayerInfoUpdatePacket.Entry> entries;

    public ClientboundPlayerInfoUpdatePacket(EnumSet<ClientboundPlayerInfoUpdatePacket.Action> actions, Collection<ServerPlayer> players) {
        this.actions = actions;
        this.entries = players.stream().map(ClientboundPlayerInfoUpdatePacket.Entry::new).toList();
    }

    public ClientboundPlayerInfoUpdatePacket(ClientboundPlayerInfoUpdatePacket.Action action, ServerPlayer player) {
        this.actions = EnumSet.of(action);
        this.entries = List.of(new ClientboundPlayerInfoUpdatePacket.Entry(player));
    }

    public static ClientboundPlayerInfoUpdatePacket createPlayerInitializing(Collection<ServerPlayer> players) {
        EnumSet<ClientboundPlayerInfoUpdatePacket.Action> enumSet = EnumSet.of(
            ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER,
            ClientboundPlayerInfoUpdatePacket.Action.INITIALIZE_CHAT,
            ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE,
            ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED,
            ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY,
            ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME
        );
        return new ClientboundPlayerInfoUpdatePacket(enumSet, players);
    }

    private ClientboundPlayerInfoUpdatePacket(RegistryFriendlyByteBuf buffer) {
        this.actions = buffer.readEnumSet(ClientboundPlayerInfoUpdatePacket.Action.class);
        this.entries = buffer.readList(buf -> {
            ClientboundPlayerInfoUpdatePacket.EntryBuilder entryBuilder = new ClientboundPlayerInfoUpdatePacket.EntryBuilder(buf.readUUID());

            for (ClientboundPlayerInfoUpdatePacket.Action action : this.actions) {
                action.reader.read(entryBuilder, (RegistryFriendlyByteBuf)buf);
            }

            return entryBuilder.build();
        });
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeEnumSet(this.actions, ClientboundPlayerInfoUpdatePacket.Action.class);
        buffer.writeCollection(this.entries, (buf, entry) -> {
            buf.writeUUID(entry.profileId());

            for (ClientboundPlayerInfoUpdatePacket.Action action : this.actions) {
                action.writer.write((RegistryFriendlyByteBuf)buf, entry);
            }
        });
    }

    @Override
    public PacketType<ClientboundPlayerInfoUpdatePacket> type() {
        return GamePacketTypes.CLIENTBOUND_PLAYER_INFO_UPDATE;
    }

    /**
     * Passes this Packet on to the PacketListener for processing.
     */
    public void handle(ClientGamePacketListener handler) {
        handler.handlePlayerInfoUpdate(this);
    }

    public EnumSet<ClientboundPlayerInfoUpdatePacket.Action> actions() {
        return this.actions;
    }

    public List<ClientboundPlayerInfoUpdatePacket.Entry> entries() {
        return this.entries;
    }

    public List<ClientboundPlayerInfoUpdatePacket.Entry> newEntries() {
        return this.actions.contains(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER) ? this.entries : List.of();
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("actions", this.actions).add("entries", this.entries).toString();
    }

    public enum Action {
        ADD_PLAYER((entryBuilder, registryFriendlyByteBuf) -> {
            GameProfile gameProfile = new GameProfile(entryBuilder.profileId, registryFriendlyByteBuf.readUtf(16));
            gameProfile.getProperties().putAll(ByteBufCodecs.GAME_PROFILE_PROPERTIES.decode(registryFriendlyByteBuf));
            entryBuilder.profile = gameProfile;
        }, (registryFriendlyByteBuf, entry) -> {
            GameProfile gameProfile = Objects.requireNonNull(entry.profile());
            registryFriendlyByteBuf.writeUtf(gameProfile.getName(), 16);
            ByteBufCodecs.GAME_PROFILE_PROPERTIES.encode(registryFriendlyByteBuf, gameProfile.getProperties());
        }),
        INITIALIZE_CHAT(
            (entryBuilder, registryFriendlyByteBuf) -> entryBuilder.chatSession = registryFriendlyByteBuf.readNullable(RemoteChatSession.Data::read),
            (registryFriendlyByteBuf, entry) -> registryFriendlyByteBuf.writeNullable(entry.chatSession, RemoteChatSession.Data::write)
        ),
        UPDATE_GAME_MODE(
            (entryBuilder, registryFriendlyByteBuf) -> entryBuilder.gameMode = GameType.byId(registryFriendlyByteBuf.readVarInt()),
            (registryFriendlyByteBuf, entry) -> registryFriendlyByteBuf.writeVarInt(entry.gameMode().getId())
        ),
        UPDATE_LISTED(
            (entryBuilder, registryFriendlyByteBuf) -> entryBuilder.listed = registryFriendlyByteBuf.readBoolean(),
            (registryFriendlyByteBuf, entry) -> registryFriendlyByteBuf.writeBoolean(entry.listed())
        ),
        UPDATE_LATENCY(
            (entryBuilder, registryFriendlyByteBuf) -> entryBuilder.latency = registryFriendlyByteBuf.readVarInt(),
            (registryFriendlyByteBuf, entry) -> registryFriendlyByteBuf.writeVarInt(entry.latency())
        ),
        UPDATE_DISPLAY_NAME(
            (entryBuilder, registryFriendlyByteBuf) -> entryBuilder.displayName = FriendlyByteBuf.readNullable(
                registryFriendlyByteBuf, ComponentSerialization.TRUSTED_STREAM_CODEC
            ),
            (registryFriendlyByteBuf, entry) -> FriendlyByteBuf.writeNullable(
                registryFriendlyByteBuf, entry.displayName(), ComponentSerialization.TRUSTED_STREAM_CODEC
            )
        );

        final ClientboundPlayerInfoUpdatePacket.Action.Reader reader;
        final ClientboundPlayerInfoUpdatePacket.Action.Writer writer;

        Action(final ClientboundPlayerInfoUpdatePacket.Action.Reader reader, final ClientboundPlayerInfoUpdatePacket.Action.Writer writer) {
            this.reader = reader;
            this.writer = writer;
        }

        public interface Reader {
            void read(ClientboundPlayerInfoUpdatePacket.EntryBuilder var1, RegistryFriendlyByteBuf var2);
        }

        public interface Writer {
            void write(RegistryFriendlyByteBuf var1, ClientboundPlayerInfoUpdatePacket.Entry var2);
        }
    }

    public record Entry(
        UUID profileId,
        @Nullable GameProfile profile,
        boolean listed,
        int latency,
        GameType gameMode,
        @Nullable Component displayName,
        @Nullable RemoteChatSession.Data chatSession
    ) {
        Entry(ServerPlayer player) {
            this(
                player.getUUID(),
                player.getGameProfile(),
                true,
                player.connection.latency(),
                player.gameMode.getGameModeForPlayer(),
                player.getTabListDisplayName(),
                Optionull.map(player.getChatSession(), RemoteChatSession::asData)
            );
        }
    }

    static class EntryBuilder {
        final UUID profileId;
        @Nullable
        GameProfile profile;
        boolean listed;
        int latency;
        GameType gameMode = GameType.DEFAULT_MODE;
        @Nullable
        Component displayName;
        @Nullable
        RemoteChatSession.Data chatSession;

        EntryBuilder(UUID profileId) {
            this.profileId = profileId;
        }

        ClientboundPlayerInfoUpdatePacket.Entry build() {
            return new ClientboundPlayerInfoUpdatePacket.Entry(
                this.profileId, this.profile, this.listed, this.latency, this.gameMode, this.displayName, this.chatSession
            );
        }
    }
}
