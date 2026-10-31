package com.mojang.realmsclient.util.task;

import com.mojang.logging.LogUtils;
import com.mojang.realmsclient.client.RealmsClient;
import com.mojang.realmsclient.dto.Backup;
import com.mojang.realmsclient.exception.RealmsServiceException;
import com.mojang.realmsclient.exception.RetryCallException;
import com.mojang.realmsclient.gui.screens.RealmsConfigureWorldScreen;
import com.mojang.realmsclient.gui.screens.RealmsGenericErrorScreen;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;

public class RestoreTask extends LongRunningTask {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Component TITLE = Component.translatable("mco.backup.restoring");
    private final Backup backup;
    private final long realmId;
    private final RealmsConfigureWorldScreen lastScreen;

    public RestoreTask(Backup backup, long worldId, RealmsConfigureWorldScreen lastScreen) {
        this.backup = backup;
        this.realmId = worldId;
        this.lastScreen = lastScreen;
    }

    @Override
    public void run() {
        RealmsClient realmsClient = RealmsClient.create();
        int i = 0;

        while (i < 25) {
            try {
                if (this.aborted()) {
                    return;
                }

                realmsClient.restoreWorld(this.realmId, this.backup.backupId);
                pause(1L);
                if (this.aborted()) {
                    return;
                }

                setScreen(this.lastScreen.getNewScreen());
                return;
            } catch (RetryCallException retryCallException) {
                if (this.aborted()) {
                    return;
                }

                pause(retryCallException.delaySeconds);
                i++;
            } catch (RealmsServiceException realmsServiceException) {
                if (this.aborted()) {
                    return;
                }

                LOGGER.error("Couldn't restore backup", realmsServiceException);
                setScreen(new RealmsGenericErrorScreen(realmsServiceException, this.lastScreen));
                return;
            } catch (Exception exception) {
                if (this.aborted()) {
                    return;
                }

                LOGGER.error("Couldn't restore backup", exception);
                this.error(exception);
                return;
            }
        }
    }

    @Override
    public Component getTitle() {
        return TITLE;
    }
}
