package net.salutaryfish.versionunbound.testmod.events;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Checks the events NeoForge fires on its game bus. Server checks summarize when a server stops ("[events] all N server
 * checks passed"), and again for players if one played; client checks summarize after 100 client ticks in a level.
 */
@Mod(EventsMod.MOD_ID)
public class EventsMod {
    public static final String MOD_ID = "events";

    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    static final DeferredItem<VanishingItem> VANISHING_ITEM = ITEMS.registerItem("vanishing_item", VanishingItem::new);

    public EventsMod(IEventBus modBus, ModContainer container, Dist dist) {
        ITEMS.register(modBus);
        ServerEvents.register(modBus, container);
        if (dist.isClient()) {
            ClientEvents.register();
        }
    }
}
