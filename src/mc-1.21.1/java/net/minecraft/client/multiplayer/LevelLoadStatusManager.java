package net.minecraft.client.multiplayer;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;

public class LevelLoadStatusManager {
    private final LocalPlayer player;
    private final ClientLevel level;
    private final LevelRenderer levelRenderer;
    private LevelLoadStatusManager.Status status = LevelLoadStatusManager.Status.WAITING_FOR_SERVER;

    public LevelLoadStatusManager(LocalPlayer player, ClientLevel level, LevelRenderer levelRenderer) {
        this.player = player;
        this.level = level;
        this.levelRenderer = levelRenderer;
    }

    public void tick() {
        switch (this.status) {
            case WAITING_FOR_PLAYER_CHUNK:
                BlockPos blockPos = this.player.blockPosition();
                boolean bl = this.level.isOutsideBuildHeight(blockPos.getY());
                if (bl || this.levelRenderer.isSectionCompiled(blockPos) || this.player.isSpectator() || !this.player.isAlive()) {
                    this.status = LevelLoadStatusManager.Status.LEVEL_READY;
                }
            case WAITING_FOR_SERVER:
            case LEVEL_READY:
        }
    }

    public boolean levelReady() {
        return this.status == LevelLoadStatusManager.Status.LEVEL_READY;
    }

    public void loadingPacketsReceived() {
        if (this.status == LevelLoadStatusManager.Status.WAITING_FOR_SERVER) {
            this.status = LevelLoadStatusManager.Status.WAITING_FOR_PLAYER_CHUNK;
        }
    }

    enum Status {
        WAITING_FOR_SERVER,
        WAITING_FOR_PLAYER_CHUNK,
        LEVEL_READY;
    }
}
