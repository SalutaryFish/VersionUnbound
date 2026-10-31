package com.mojang.realmsclient.util.task;

import com.mojang.logging.LogUtils;
import com.mojang.realmsclient.client.RealmsClient;
import com.mojang.realmsclient.exception.RealmsServiceException;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;

public class RealmCreationTask extends LongRunningTask {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Component TITLE = Component.translatable("mco.create.world.wait");
    private final String name;
    private final String motd;
    private final long realmId;

    public RealmCreationTask(long realmId, String name, String motd) {
        this.realmId = realmId;
        this.name = name;
        this.motd = motd;
    }

    @Override
    public void run() {
        RealmsClient realmsClient = RealmsClient.create();

        try {
            realmsClient.initializeRealm(this.realmId, this.name, this.motd);
        } catch (RealmsServiceException realmsServiceException) {
            LOGGER.error("Couldn't create world", realmsServiceException);
            this.error(realmsServiceException);
        } catch (Exception exception) {
            LOGGER.error("Could not create world", exception);
            this.error(exception);
        }
    }

    @Override
    public Component getTitle() {
        return TITLE;
    }
}
