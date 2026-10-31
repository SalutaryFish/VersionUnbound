package com.mojang.realmsclient.util.task;

import com.mojang.logging.LogUtils;
import com.mojang.realmsclient.client.RealmsClient;
import com.mojang.realmsclient.dto.WorldTemplate;
import com.mojang.realmsclient.exception.RetryCallException;
import com.mojang.realmsclient.gui.screens.RealmsConfigureWorldScreen;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;

public class SwitchMinigameTask extends LongRunningTask {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Component TITLE = Component.translatable("mco.minigame.world.starting.screen.title");
    private final long realmId;
    private final WorldTemplate worldTemplate;
    private final RealmsConfigureWorldScreen lastScreen;

    public SwitchMinigameTask(long worldId, WorldTemplate worldTemplate, RealmsConfigureWorldScreen lastScreen) {
        this.realmId = worldId;
        this.worldTemplate = worldTemplate;
        this.lastScreen = lastScreen;
    }

    @Override
    public void run() {
        RealmsClient realmsClient = RealmsClient.create();

        for (int i = 0; i < 25; i++) {
            try {
                if (this.aborted()) {
                    return;
                }

                if (realmsClient.putIntoMinigameMode(this.realmId, this.worldTemplate.id)) {
                    setScreen(this.lastScreen);
                    break;
                }
            } catch (RetryCallException retryCallException) {
                if (this.aborted()) {
                    return;
                }

                pause(retryCallException.delaySeconds);
            } catch (Exception exception) {
                if (this.aborted()) {
                    return;
                }

                LOGGER.error("Couldn't start mini game!");
                this.error(exception);
            }
        }
    }

    @Override
    public Component getTitle() {
        return TITLE;
    }
}
