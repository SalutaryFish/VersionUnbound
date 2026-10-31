package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public class ClientboundSetEntityMotionPacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundSetEntityMotionPacket> STREAM_CODEC = Packet.codec(
        ClientboundSetEntityMotionPacket::write, ClientboundSetEntityMotionPacket::new
    );
    private final int id;
    private final int xa;
    private final int ya;
    private final int za;

    public ClientboundSetEntityMotionPacket(Entity entity) {
        this(entity.getId(), entity.getDeltaMovement());
    }

    public ClientboundSetEntityMotionPacket(int id, Vec3 deltaMovement) {
        this.id = id;
        double d = 3.9;
        double e = Mth.clamp(deltaMovement.x, -3.9, 3.9);
        double f = Mth.clamp(deltaMovement.y, -3.9, 3.9);
        double g = Mth.clamp(deltaMovement.z, -3.9, 3.9);
        this.xa = (int)(e * 8000.0);
        this.ya = (int)(f * 8000.0);
        this.za = (int)(g * 8000.0);
    }

    private ClientboundSetEntityMotionPacket(FriendlyByteBuf buffer) {
        this.id = buffer.readVarInt();
        this.xa = buffer.readShort();
        this.ya = buffer.readShort();
        this.za = buffer.readShort();
    }

    /**
     * Writes the raw packet data to the data stream.
     */
    private void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.id);
        buffer.writeShort(this.xa);
        buffer.writeShort(this.ya);
        buffer.writeShort(this.za);
    }

    @Override
    public PacketType<ClientboundSetEntityMotionPacket> type() {
        return GamePacketTypes.CLIENTBOUND_SET_ENTITY_MOTION;
    }

    /**
     * Passes this Packet on to the NetHandler for processing.
     */
    public void handle(ClientGamePacketListener handler) {
        handler.handleSetEntityMotion(this);
    }

    public int getId() {
        return this.id;
    }

    public double getXa() {
        return this.xa / 8000.0;
    }

    public double getYa() {
        return this.ya / 8000.0;
    }

    public double getZa() {
        return this.za / 8000.0;
    }
}
