package net.minecraft.client.multiplayer;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.status.ServerStatus;
import net.minecraft.util.PngInfo;
import org.slf4j.Logger;

public class ServerData {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_ICON_SIZE = 1024;
    public String name;
    public String ip;
    public Component status;
    public Component motd;
    @Nullable
    public ServerStatus.Players players;
    public long ping;
    public int protocol = SharedConstants.getCurrentVersion().getProtocolVersion();
    public Component version = Component.literal(SharedConstants.getCurrentVersion().getName());
    public List<Component> playerList = Collections.emptyList();
    private ServerData.ServerPackStatus packStatus = ServerData.ServerPackStatus.PROMPT;
    @Nullable
    private byte[] iconBytes;
    private ServerData.Type type;
    private ServerData.State state = ServerData.State.INITIAL;

    public ServerData(String name, String ip, ServerData.Type type) {
        this.name = name;
        this.ip = ip;
        this.type = type;
    }

    public CompoundTag write() {
        CompoundTag compoundTag = new CompoundTag();
        compoundTag.putString("name", this.name);
        compoundTag.putString("ip", this.ip);
        if (this.iconBytes != null) {
            compoundTag.putString("icon", Base64.getEncoder().encodeToString(this.iconBytes));
        }

        if (this.packStatus == ServerData.ServerPackStatus.ENABLED) {
            compoundTag.putBoolean("acceptTextures", true);
        } else if (this.packStatus == ServerData.ServerPackStatus.DISABLED) {
            compoundTag.putBoolean("acceptTextures", false);
        }

        return compoundTag;
    }

    public ServerData.ServerPackStatus getResourcePackStatus() {
        return this.packStatus;
    }

    public void setResourcePackStatus(ServerData.ServerPackStatus packStatus) {
        this.packStatus = packStatus;
    }

    /**
     * Takes an NBTTagCompound with 'name' and 'ip' keys, returns a ServerData instance.
     */
    public static ServerData read(CompoundTag nbtCompound) {
        ServerData serverData = new ServerData(nbtCompound.getString("name"), nbtCompound.getString("ip"), ServerData.Type.OTHER);
        if (nbtCompound.contains("icon", 8)) {
            try {
                byte[] bs = Base64.getDecoder().decode(nbtCompound.getString("icon"));
                serverData.setIconBytes(validateIcon(bs));
            } catch (IllegalArgumentException illegalArgumentException) {
                LOGGER.warn("Malformed base64 server icon", illegalArgumentException);
            }
        }

        if (nbtCompound.contains("acceptTextures", 1)) {
            if (nbtCompound.getBoolean("acceptTextures")) {
                serverData.setResourcePackStatus(ServerData.ServerPackStatus.ENABLED);
            } else {
                serverData.setResourcePackStatus(ServerData.ServerPackStatus.DISABLED);
            }
        } else {
            serverData.setResourcePackStatus(ServerData.ServerPackStatus.PROMPT);
        }

        return serverData;
    }

    @Nullable
    public byte[] getIconBytes() {
        return this.iconBytes;
    }

    public void setIconBytes(@Nullable byte[] iconBytes) {
        this.iconBytes = iconBytes;
    }

    public boolean isLan() {
        return this.type == ServerData.Type.LAN;
    }

    public boolean isRealm() {
        return this.type == ServerData.Type.REALM;
    }

    public ServerData.Type type() {
        return this.type;
    }

    public void copyNameIconFrom(ServerData other) {
        this.ip = other.ip;
        this.name = other.name;
        this.iconBytes = other.iconBytes;
    }

    public void copyFrom(ServerData serverData) {
        this.copyNameIconFrom(serverData);
        this.setResourcePackStatus(serverData.getResourcePackStatus());
        this.type = serverData.type;
    }

    public ServerData.State state() {
        return this.state;
    }

    public void setState(ServerData.State state) {
        this.state = state;
    }

    @Nullable
    public static byte[] validateIcon(@Nullable byte[] icon) {
        if (icon != null) {
            try {
                PngInfo pngInfo = PngInfo.fromBytes(icon);
                if (pngInfo.width() <= 1024 && pngInfo.height() <= 1024) {
                    return icon;
                }
            } catch (IOException iOException) {
                LOGGER.warn("Failed to decode server icon", iOException);
            }
        }

        return null;
    }

    public enum ServerPackStatus {
        ENABLED("enabled"),
        DISABLED("disabled"),
        PROMPT("prompt");

        private final Component name;

        ServerPackStatus(final String name) {
            this.name = Component.translatable("addServer.resourcePack." + name);
        }

        public Component getName() {
            return this.name;
        }
    }

    public enum State {
        INITIAL,
        PINGING,
        UNREACHABLE,
        INCOMPATIBLE,
        SUCCESSFUL;
    }

    public enum Type {
        LAN,
        REALM,
        OTHER;
    }
}
