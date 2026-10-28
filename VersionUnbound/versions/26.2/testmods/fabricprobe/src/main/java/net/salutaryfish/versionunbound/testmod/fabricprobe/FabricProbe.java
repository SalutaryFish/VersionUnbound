package net.salutaryfish.versionunbound.testmod.fabricprobe;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sets a system property when Fabric runs its mods' initializers. The lifecycle testmod, a NeoForge mod that can't
 * see Fabric mods, reads it to tell whether its loading steps ran before or after.
 */
public class FabricProbe implements ModInitializer {
    private static final String INITIALIZED = "versionunbound.testprobe.fabricInitialized";
    private static final Logger LOGGER = LoggerFactory.getLogger("FabricProbe");

    @Override
    public void onInitialize() {
        System.setProperty(INITIALIZED, "true");
        LOGGER.info("[fabricprobe] Fabric is initializing its mods");
    }
}
