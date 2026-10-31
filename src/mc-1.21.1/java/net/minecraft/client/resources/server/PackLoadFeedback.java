package net.minecraft.client.resources.server;

import java.util.UUID;

public interface PackLoadFeedback {
    void reportUpdate(UUID var1, PackLoadFeedback.Update var2);

    void reportFinalResult(UUID var1, PackLoadFeedback.FinalResult var2);

    enum FinalResult {
        DECLINED,
        APPLIED,
        DISCARDED,
        DOWNLOAD_FAILED,
        ACTIVATION_FAILED;
    }

    enum Update {
        ACCEPTED,
        DOWNLOADED;
    }
}
