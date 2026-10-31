package net.minecraft.client.multiplayer;

import com.google.common.base.Suppliers;
import com.mojang.authlib.GameProfile;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.RemoteChatSession;
import net.minecraft.network.chat.SignedMessageValidator;
import net.minecraft.world.entity.player.ProfilePublicKey;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.PlayerTeam;

public class PlayerInfo {
    /**
     * The GameProfile for the player represented by this NetworkPlayerInfo instance
     */
    private final GameProfile profile;
    private final Supplier<PlayerSkin> skinLookup;
    private GameType gameMode = GameType.DEFAULT_MODE;
    private int latency;
    /**
     * When this is non-null, it is displayed instead of the player's real name
     */
    @Nullable
    private Component tabListDisplayName;
    @Nullable
    private RemoteChatSession chatSession;
    private SignedMessageValidator messageValidator;

    public PlayerInfo(GameProfile profile, boolean enforeSecureChat) {
        this.profile = profile;
        this.messageValidator = fallbackMessageValidator(enforeSecureChat);
        Supplier<Supplier<PlayerSkin>> supplier = Suppliers.memoize(() -> createSkinLookup(profile));
        this.skinLookup = () -> supplier.get().get();
    }

    private static Supplier<PlayerSkin> createSkinLookup(GameProfile profile) {
        Minecraft minecraft = Minecraft.getInstance();
        SkinManager skinManager = minecraft.getSkinManager();
        CompletableFuture<PlayerSkin> completableFuture = skinManager.getOrLoad(profile);
        boolean bl = !minecraft.isLocalPlayer(profile.getId());
        PlayerSkin playerSkin = DefaultPlayerSkin.get(profile);
        return () -> {
            PlayerSkin playerSkin2 = completableFuture.getNow(playerSkin);
            return bl && !playerSkin2.secure() ? playerSkin : playerSkin2;
        };
    }

    public GameProfile getProfile() {
        return this.profile;
    }

    @Nullable
    public RemoteChatSession getChatSession() {
        return this.chatSession;
    }

    public SignedMessageValidator getMessageValidator() {
        return this.messageValidator;
    }

    public boolean hasVerifiableChat() {
        return this.chatSession != null;
    }

    protected void setChatSession(RemoteChatSession chatSession) {
        this.chatSession = chatSession;
        this.messageValidator = chatSession.createMessageValidator(ProfilePublicKey.EXPIRY_GRACE_PERIOD);
    }

    protected void clearChatSession(boolean enforcesSecureChat) {
        this.chatSession = null;
        this.messageValidator = fallbackMessageValidator(enforcesSecureChat);
    }

    private static SignedMessageValidator fallbackMessageValidator(boolean enforeSecureChat) {
        return enforeSecureChat ? SignedMessageValidator.REJECT_ALL : SignedMessageValidator.ACCEPT_UNSIGNED;
    }

    public GameType getGameMode() {
        return this.gameMode;
    }

    protected void setGameMode(GameType gameMode) {
        this.gameMode = gameMode;
    }

    public int getLatency() {
        return this.latency;
    }

    protected void setLatency(int latency) {
        this.latency = latency;
    }

    public PlayerSkin getSkin() {
        return this.skinLookup.get();
    }

    @Nullable
    public PlayerTeam getTeam() {
        return Minecraft.getInstance().level.getScoreboard().getPlayersTeam(this.getProfile().getName());
    }

    public void setTabListDisplayName(@Nullable Component displayName) {
        this.tabListDisplayName = displayName;
    }

    @Nullable
    public Component getTabListDisplayName() {
        return this.tabListDisplayName;
    }
}
