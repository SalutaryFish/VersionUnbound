package net.minecraft.network.protocol;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.network.PacketListener;

public interface BundlerInfo {
    int BUNDLE_SIZE_LIMIT = 4096;

    static <T extends PacketListener, P extends BundlePacket<? super T>> BundlerInfo createForPacket(
        PacketType<P> type, Function<Iterable<Packet<? super T>>, P> bundler, BundleDelimiterPacket<? super T> packet
    ) {
        return new BundlerInfo() {
            @Override
            public void unbundlePacket(Packet<?> packet, Consumer<Packet<?>> consumer) {
                if (packet.type() == type) {
                    P bundlePacket = (P)packet;
                    consumer.accept(packet);
                    bundlePacket.subPackets().forEach(consumer);
                    consumer.accept(packet);
                } else {
                    consumer.accept(packet);
                }
            }

            @Nullable
            @Override
            public BundlerInfo.Bundler startPacketBundling(Packet<?> packet) {
                return packet == packet ? new BundlerInfo.Bundler() {
                    private final List<Packet<? super T>> bundlePackets = new ArrayList<>();

                    @Nullable
                    @Override
                    public Packet<?> addPacket(Packet<?> packet) {
                        if (packet == packet) {
                            return bundler.apply(this.bundlePackets);
                        }

                        Packet<T> packet2 = (Packet<T>)packet;
                        if (this.bundlePackets.size() >= 4096) {
                            throw new IllegalStateException("Too many packets in a bundle");
                        }

                        this.bundlePackets.add(packet2);
                        return null;
                    }
                } : null;
            }
        };
    }

    void unbundlePacket(Packet<?> var1, Consumer<Packet<?>> var2);

    @Nullable
    BundlerInfo.Bundler startPacketBundling(Packet<?> var1);

    interface Bundler {
        @Nullable
        Packet<?> addPacket(Packet<?> var1);
    }
}
