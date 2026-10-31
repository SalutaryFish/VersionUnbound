package net.minecraft.client.multiplayer.resolver;

import com.google.common.net.HostAndPort;
import com.mojang.logging.LogUtils;
import java.net.IDN;
import org.slf4j.Logger;

public final class ServerAddress {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final HostAndPort hostAndPort;
    private static final ServerAddress INVALID = new ServerAddress(HostAndPort.fromParts("server.invalid", 25565));

    public ServerAddress(String host, int port) {
        this(HostAndPort.fromParts(host, port));
    }

    private ServerAddress(HostAndPort hostAndPort) {
        this.hostAndPort = hostAndPort;
    }

    public String getHost() {
        try {
            return IDN.toASCII(this.hostAndPort.getHost());
        } catch (IllegalArgumentException illegalArgumentException) {
            return "";
        }
    }

    public int getPort() {
        return this.hostAndPort.getPort();
    }

    public static ServerAddress parseString(String ip) {
        if (ip == null) {
            return INVALID;
        }

        try {
            HostAndPort hostAndPort = HostAndPort.fromString(ip).withDefaultPort(25565);
            return hostAndPort.getHost().isEmpty() ? INVALID : new ServerAddress(hostAndPort);
        } catch (IllegalArgumentException illegalArgumentException) {
            LOGGER.info("Failed to parse URL {}", ip, illegalArgumentException);
            return INVALID;
        }
    }

    public static boolean isValidAddress(String hostAndPort) {
        try {
            HostAndPort hostAndPort2 = HostAndPort.fromString(hostAndPort);
            String string = hostAndPort2.getHost();
            if (!string.isEmpty()) {
                IDN.toASCII(string);
                return true;
            }
        } catch (IllegalArgumentException var3) {
        }

        return false;
    }

    static int parsePort(String port) {
        try {
            return Integer.parseInt(port.trim());
        } catch (Exception var2) {
            return 25565;
        }
    }

    @Override
    public String toString() {
        return this.hostAndPort.toString();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        } else {
            return other instanceof ServerAddress ? this.hostAndPort.equals(((ServerAddress)other).hostAndPort) : false;
        }
    }

    @Override
    public int hashCode() {
        return this.hostAndPort.hashCode();
    }
}
