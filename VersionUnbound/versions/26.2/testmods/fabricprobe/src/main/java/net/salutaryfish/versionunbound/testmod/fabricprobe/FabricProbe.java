package net.salutaryfish.versionunbound.testmod.fabricprobe;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.registry.RegistryAttribute;
import net.fabricmc.fabric.api.event.registry.RegistryAttributeHolder;
import net.fabricmc.fabric.api.object.builder.v1.world.poi.PoiHelper;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sets system properties when Fabric runs its mods' initializers, for NeoForge testmods to read: they can't see Fabric
 * mods. The lifecycle testmod checks whether its loading steps ran before or after; the registries testmod checks what
 * Fabric mods see of NeoForge mods' registrations, which by now are done, and that Fabric mods can still register.
 */
public class FabricProbe implements ModInitializer {
    private static final String PREFIX = "versionunbound.testprobe.";
    private static final Logger LOGGER = LoggerFactory.getLogger("FabricProbe");

    @Override
    public void onInitialize() {
        System.setProperty(PREFIX + "fabricInitialized", "true");
        LOGGER.info("[fabricprobe] Fabric is initializing its mods");
        probeRegistries();
    }

    private static void probeRegistries() {
        var itemKey = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("versionunbound_fabricprobe", "probe_item"));
        try {
            Registry.register(BuiltInRegistries.ITEM, itemKey, new Item(new Item.Properties().setId(itemKey)));
            System.setProperty(PREFIX + "fabricItemRegistered", "true");
        } catch (RuntimeException e) {
            LOGGER.error("[fabricprobe] could not register an item", e);
        }

        System.setProperty(PREFIX + "sawNeoForgeItem", String.valueOf(BuiltInRegistries.ITEM.containsKey(Identifier.fromNamespaceAndPath("registries", "probe_item"))));

        var shapes = RegistryAttributeHolder.get(ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath("registries", "shapes")));
        System.setProperty(PREFIX + "shapesSyncAttributes", String.valueOf(shapes.hasAttribute(RegistryAttribute.SYNCED) && shapes.hasAttribute(RegistryAttribute.MODDED)));

        // Registers through vanilla's PoiTypes.register, which maps the block states a second time after the registry
        // callback NeoForge mods rely on has mapped them.
        try {
            PoiHelper.register(Identifier.fromNamespaceAndPath("versionunbound_fabricprobe", "probe_poi"), 1, 1, Blocks.DRIED_KELP_BLOCK);
            System.setProperty(PREFIX + "fabricPoiRegistered", "true");
        } catch (RuntimeException e) {
            LOGGER.error("[fabricprobe] could not register a point of interest type", e);
        }
    }
}
