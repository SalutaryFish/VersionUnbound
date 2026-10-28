package net.salutaryfish.versionunbound.testmod.lifecycle;

import net.minecraft.client.Minecraft;

/** Client-only checks, in their own class so the dedicated server never loads client classes. */
final class ClientChecks {
    private ClientChecks() {}

    /** NeoForge runs client setup late in Minecraft's constructor, once all of these exist. */
    static boolean clientReady() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null
                && minecraft.getWindow() != null
                && minecraft.options != null
                && minecraft.getResourceManager() != null
                && minecraft.getTextureManager() != null
                && minecraft.gameRenderer != null;
    }
}
