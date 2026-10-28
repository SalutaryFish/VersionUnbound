package net.salutaryfish.versionunbound.loader;

import net.fabricmc.api.ModInitializer;

public class VersionUnboundInit implements ModInitializer {
    @Override
    public void onInitialize() {
        NeoModLoader.loadMods();
    }
}
