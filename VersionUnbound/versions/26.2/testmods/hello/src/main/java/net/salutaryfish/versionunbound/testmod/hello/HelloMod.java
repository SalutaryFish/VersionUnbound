package net.salutaryfish.versionunbound.testmod.hello;

import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;

@Mod(HelloMod.MOD_ID)
public class HelloMod {
    public static final String MOD_ID = "hello";
    public static final Logger LOGGER = LogUtils.getLogger();

    public HelloMod(IEventBus modBus, ModContainer container, Dist dist) {
        LOGGER.info("[hello] constructed: mod={}, dist={}", container.getModId(), dist);
        modBus.addListener(FMLCommonSetupEvent.class, event -> {
            LOGGER.info("[hello] common setup");
            event.enqueueWork(() -> LOGGER.info("[hello] deferred work ran"));
        });
    }
}
