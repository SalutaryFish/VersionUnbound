package com.mojang.realmsclient.util.task;

import com.mojang.logging.LogUtils;
import com.mojang.realmsclient.RealmsMainScreen;
import com.mojang.realmsclient.client.RealmsClient;
import com.mojang.realmsclient.dto.RealmsServer;
import com.mojang.realmsclient.exception.RetryCallException;
import com.mojang.realmsclient.gui.screens.RealmsConfigureWorldScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;

public class OpenServerTask extends LongRunningTask {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Component TITLE = Component.translatable("mco.configure.world.opening");
    private final RealmsServer serverData;
    private final Screen returnScreen;
    private final boolean join;
    private final Minecraft minecraft;

    public OpenServerTask(RealmsServer serverData, Screen returnScreen, boolean join, Minecraft minecraft) {
        this.serverData = serverData;
        this.returnScreen = returnScreen;
        this.join = join;
        this.minecraft = minecraft;
    }

    @Override
    public void run() {
        RealmsClient realmsClient = RealmsClient.create();

        for (int i = 0; i < 25; i++) {
            if (this.aborted()) {
                return;
            }

            try {
                boolean bl = realmsClient.open(this.serverData.id);
                if (bl) {
                    this.minecraft.execute(() -> {
                        if (this.returnScreen instanceof RealmsConfigureWorldScreen) {
                            ((RealmsConfigureWorldScreen)this.returnScreen).stateChanged();
                        }

                        this.serverData.state = RealmsServer.State.OPEN;
                        if (this.join) {
                            RealmsMainScreen.play(this.serverData, this.returnScreen);
                        } else {
                            this.minecraft.setScreen(this.returnScreen);
                        }
                    });
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

                LOGGER.error("Failed to open server", exception);
                this.error(exception);
            }
        }
    }

    @Override
    public Component getTitle() {
        return TITLE;
    }
}
