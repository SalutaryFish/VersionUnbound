package net.minecraft.network.protocol.game;

import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public record ClientboundDamageEventPacket(int entityId, Holder<DamageType> sourceType, int sourceCauseId, int sourceDirectId, Optional<Vec3> sourcePosition)
    implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundDamageEventPacket> STREAM_CODEC = Packet.codec(
        ClientboundDamageEventPacket::write, ClientboundDamageEventPacket::new
    );

    public ClientboundDamageEventPacket(Entity entity, DamageSource damageSource) {
        this(
            entity.getId(),
            damageSource.typeHolder(),
            damageSource.getEntity() != null ? damageSource.getEntity().getId() : -1,
            damageSource.getDirectEntity() != null ? damageSource.getDirectEntity().getId() : -1,
            Optional.ofNullable(damageSource.sourcePositionRaw())
        );
    }

    private ClientboundDamageEventPacket(RegistryFriendlyByteBuf buffer) {
        this(
            buffer.readVarInt(),
            DamageType.STREAM_CODEC.decode(buffer),
            readOptionalEntityId(buffer),
            readOptionalEntityId(buffer),
            buffer.readOptional(friendlyByteBuf -> new Vec3(friendlyByteBuf.readDouble(), friendlyByteBuf.readDouble(), friendlyByteBuf.readDouble()))
        );
    }

    private static void writeOptionalEntityId(FriendlyByteBuf buffer, int optionalEntityId) {
        buffer.writeVarInt(optionalEntityId + 1);
    }

    private static int readOptionalEntityId(FriendlyByteBuf buffer) {
        return buffer.readVarInt() - 1;
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(this.entityId);
        DamageType.STREAM_CODEC.encode(buffer, this.sourceType);
        writeOptionalEntityId(buffer, this.sourceCauseId);
        writeOptionalEntityId(buffer, this.sourceDirectId);
        buffer.writeOptional(this.sourcePosition, (bufferx, vec3) -> {
            bufferx.writeDouble(vec3.x());
            bufferx.writeDouble(vec3.y());
            bufferx.writeDouble(vec3.z());
        });
    }

    @Override
    public PacketType<ClientboundDamageEventPacket> type() {
        return GamePacketTypes.CLIENTBOUND_DAMAGE_EVENT;
    }

    /**
     * Passes this Packet on to the PacketListener for processing.
     */
    public void handle(ClientGamePacketListener handler) {
        handler.handleDamageEvent(this);
    }

    public DamageSource getSource(Level level) {
        if (this.sourcePosition.isPresent()) {
            return new DamageSource(this.sourceType, this.sourcePosition.get());
        }

        Entity entity = level.getEntity(this.sourceCauseId);
        Entity entity2 = level.getEntity(this.sourceDirectId);
        return new DamageSource(this.sourceType, entity2, entity);
    }
}
