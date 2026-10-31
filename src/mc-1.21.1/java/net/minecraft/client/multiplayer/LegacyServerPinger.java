package net.minecraft.client.multiplayer;

import com.google.common.base.Splitter;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import java.util.List;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.server.network.LegacyProtocolUtils;
import net.minecraft.util.Mth;

public class LegacyServerPinger extends SimpleChannelInboundHandler<ByteBuf> {
    private static final Splitter SPLITTER = Splitter.on('\u0000').limit(6);
    private final ServerAddress address;
    private final LegacyServerPinger.Output output;

    public LegacyServerPinger(ServerAddress address, LegacyServerPinger.Output output) {
        this.address = address;
        this.output = output;
    }

    @Override
    public void channelActive(ChannelHandlerContext context) throws Exception {
        super.channelActive(context);
        ByteBuf byteBuf = context.alloc().buffer();

        try {
            byteBuf.writeByte(254);
            byteBuf.writeByte(1);
            byteBuf.writeByte(250);
            LegacyProtocolUtils.writeLegacyString(byteBuf, "MC|PingHost");
            int i = byteBuf.writerIndex();
            byteBuf.writeShort(0);
            int j = byteBuf.writerIndex();
            byteBuf.writeByte(127);
            LegacyProtocolUtils.writeLegacyString(byteBuf, this.address.getHost());
            byteBuf.writeInt(this.address.getPort());
            int k = byteBuf.writerIndex() - j;
            byteBuf.setShort(i, k);
            context.channel().writeAndFlush(byteBuf).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
        } catch (Exception exception) {
            byteBuf.release();
            throw exception;
        }
    }

    protected void channelRead0(ChannelHandlerContext context, ByteBuf buffer) {
        short s = buffer.readUnsignedByte();
        if (s == 255) {
            String string = LegacyProtocolUtils.readLegacyString(buffer);
            List<String> list = SPLITTER.splitToList(string);
            if ("§1".equals(list.get(0))) {
                int i = Mth.getInt(list.get(1), 0);
                String string2 = list.get(2);
                String string3 = list.get(3);
                int j = Mth.getInt(list.get(4), -1);
                int k = Mth.getInt(list.get(5), -1);
                this.output.handleResponse(i, string2, string3, j, k);
            }
        }

        context.close();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext context, Throwable exception) {
        context.close();
    }

    @FunctionalInterface
    public interface Output {
        void handleResponse(int var1, String var2, String var3, int var4, int var5);
    }
}
