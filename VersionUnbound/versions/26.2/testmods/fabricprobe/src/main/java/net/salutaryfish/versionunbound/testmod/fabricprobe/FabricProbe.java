package net.salutaryfish.versionunbound.testmod.fabricprobe;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.effect.ServerMobEffectEvents;
import net.fabricmc.fabric.api.event.player.ItemEvents;
import net.fabricmc.fabric.api.event.registry.RegistryAttribute;
import net.fabricmc.fabric.api.event.registry.RegistryAttributeHolder;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.world.poi.PoiHelper;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sets system properties when Fabric runs its mods' initializers, for NeoForge testmods to read: they can't see Fabric
 * mods. The lifecycle testmod checks whether its loading steps ran before or after; the registries testmod checks what
 * Fabric mods see of NeoForge mods' registrations, which by now are done, and that Fabric mods can still register. The
 * events testmod tags entities for the mob effect listeners here, and names item stacks for the item use listener.
 */
public class FabricProbe implements ModInitializer {
    private static final String PREFIX = "versionunbound.testprobe.";
    private static final Logger LOGGER = LoggerFactory.getLogger("FabricProbe");

    @Override
    public void onInitialize() {
        System.setProperty(PREFIX + "fabricInitialized", "true");
        LOGGER.info("[fabricprobe] Fabric is initializing its mods");
        probeRegistries();
        probeEntities();
        probeEffects();
        probeItemUse();
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

    /**
     * Registers a mob with Fabric's default attribute registry, which writes into vanilla's table, next to NeoForge's
     * attributes; and looks at the attributes the registries testmod's NeoForge events gave its mob and vanilla's zombie.
     */
    @SuppressWarnings("unchecked")
    private static void probeEntities() {
        var mobKey = ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath("versionunbound_fabricprobe", "probe_mob"));
        try {
            EntityType<Pig> mob = Registry.register(BuiltInRegistries.ENTITY_TYPE, mobKey, EntityType.Builder.of(Pig::new, MobCategory.CREATURE).build(mobKey));
            FabricDefaultAttributeRegistry.register(mob, Pig.createAttributes());
        } catch (RuntimeException e) {
            LOGGER.error("[fabricprobe] could not register a mob", e);
        }

        var neoForgeMob = (EntityType<? extends LivingEntity>) BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.fromNamespaceAndPath("registries", "probe_mob"));
        System.setProperty(PREFIX + "sawNeoForgeAttributes", String.valueOf(neoForgeMob != EntityTypes.PIG && DefaultAttributes.hasSupplier(neoForgeMob)
                && DefaultAttributes.getSupplier(neoForgeMob).getBaseValue(Attributes.MAX_HEALTH) == 12.0
                && DefaultAttributes.getSupplier(EntityTypes.ZOMBIE).hasAttribute(Attributes.LUCK)));
    }

    /**
     * Fabric API's own mob effect events, which fire from the same vanilla methods as NeoForge's, for the events
     * testmod's effect checks: entities with these tags refuse every effect, or keep regeneration when their effects are
     * removed early.
     */
    private static void probeEffects() {
        ServerMobEffectEvents.ALLOW_ADD.register((effect, entity, context) -> !entity.entityTags().contains("versionunbound_fabric_refuses_effects"));
        ServerMobEffectEvents.ALLOW_EARLY_REMOVE.register((effect, entity, context) ->
                !(entity.entityTags().contains("versionunbound_fabric_keeps_regeneration") && effect.is(MobEffects.REGENERATION)));
    }

    /**
     * Fabric API's item use event on a block, which wraps the same {@code Item.useOn} call in {@code ItemStack.useOn} as
     * Layer A's block placement does, for the events testmod's placement checks: a stack with this name can't be used on
     * a block.
     */
    private static void probeItemUse() {
        ItemEvents.USE_ON.register(context -> {
            Component name = context.getItemInHand().get(DataComponents.CUSTOM_NAME);
            return name != null && name.getString().equals("versionunbound_fabric_refuses_use_on") ? InteractionResult.FAIL : null;
        });
    }
}
