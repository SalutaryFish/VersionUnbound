package net.salutaryfish.versionunbound.testmod.lifecycle;

import java.util.concurrent.atomic.AtomicBoolean;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * Registered by FML, which picks the bus per method: {@link ProbeEvent} goes to the game bus (found through the
 * bindings provider), {@link FMLCommonSetupEvent} to the mod's own bus.
 */
@EventBusSubscriber(modid = LifecycleMod.MOD_ID)
public final class GameBusSubscriber {
    static final AtomicBoolean SAW_COMMON_SETUP = new AtomicBoolean();

    private GameBusSubscriber() {}

    @SubscribeEvent
    public static void onProbe(ProbeEvent event) {
        event.heardBy.add("@EventBusSubscriber");
    }

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        SAW_COMMON_SETUP.set(true);
    }
}
