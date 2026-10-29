/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.registries;

import com.mojang.logging.LogUtils;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.IdMapper;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModLoader;
import net.neoforged.fml.loading.progress.StartupNotificationManager;
import net.salutaryfish.versionunbound.neoforge.mixin.registries.BuiltInRegistriesAccessor;
import org.jetbrains.annotations.ApiStatus;
import org.slf4j.Logger;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

@ApiStatus.Internal
public class GameData {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Marker REGISTRIES = MarkerFactory.getMarker("REGISTRIES");

    // VersionUnbound: NeoForge's patches make vanilla's maps these GameData maps, which its registry callbacks fill.
    // On Fabric they stay vanilla's maps, and Fabric API keeps them up to date as entries are registered.
    public static Map<Block, Item> getBlockItemMap() {
        return Item.BY_BLOCK;
    }

    public static IdMapper<BlockState> getBlockStateIDMap() {
        return Block.BLOCK_STATE_REGISTRY;
    }

    public static Map<BlockState, Holder<PoiType>> getBlockStatePointOfInterestTypeMap() {
        return PoiTypes.TYPE_BY_STATE;
    }

    // VersionUnbound: getFlowerPotBlockTable and getLegacyFlowerPotBlockTable come with NeoForge's FlowerPotBlock patch, and
    // vanillaSnapshot with registry snapshots (see RegistryManager).

    public static void unfreezeData() {
        // VersionUnbound: nothing to do. Fabric API keeps vanilla's registries open until it has initialized Fabric mods,
        // so they are still open when mods register here.
        LOGGER.debug(REGISTRIES, "Unfreezing registries");
    }

    public static void freezeData() {
        // VersionUnbound: registries stay open. Freezing here would stop Fabric mods from registering in onInitialize,
        // which runs later; Fabric API freezes every registry in BuiltInRegistries.REGISTRY once that is done.
        LOGGER.debug(REGISTRIES, "Freezing registries");
    }

    public static void postRegisterEvents() {
        Set<Identifier> ordered = GameData.getRegistrationOrder();

        RuntimeException aggregate = new RuntimeException();
        for (Identifier rootRegistryName : ordered) {
            try {
                ResourceKey<? extends Registry<?>> registryKey = ResourceKey.createRegistryKey(rootRegistryName);
                Registry<?> registry = Objects.requireNonNull(BuiltInRegistries.REGISTRY.getValue(rootRegistryName));
                RegisterEvent registerEvent = new RegisterEvent(registryKey, registry);

                StartupNotificationManager.modLoaderMessage("REGISTERING " + registryKey.identifier());

                ModLoader.postEventWrapContainerInModOrder(registerEvent);
            } catch (Throwable t) {
                aggregate.addSuppressed(t);
            }
        }
        if (aggregate.getSuppressed().length > 0) {
            LOGGER.error("Failed to register some entries, see suppressed exceptions for details", aggregate);
            // VersionUnbound: no rollback to the vanilla snapshot (see RegistryManager); the error stops the game.
            throw aggregate;
        } else {
            // VersionUnbound: each of these arrives with its feature: entity attributes and spawn placements with the game
            // events (Phase 2 step 4), the rest with the extension interfaces they rely on (step 5).
            // CommonHooks.modifyAttributes();
            // SpawnPlacements.fireSpawnPlacementEvent();
            // ModLoader.postEvent(new BlockEntityTypeAddBlocksEvent());
            // CreativeModeTabRegistry.sortTabs();
            // GameRuleCategory.registerModdedCategories();
            // ItemTooltipHandler.init();
        }
    }

    /**
     * Creates a {@link LinkedHashSet} containing the ordered list of registry names in the registration order.
     * <p>
     * The order is Attributes, then the remaining vanilla registries in vanilla order, then modded registries in alphabetical order.
     * <p>
     * Due to static init issues, this is not necessarily the order that vanilla objects are bootstrapped in.
     * 
     * @return A {@link LinkedHashSet} containing the registration order.
     */
    public static Set<Identifier> getRegistrationOrder() {
        Set<Identifier> ordered = new LinkedHashSet<>();
        ordered.add(Registries.ATTRIBUTE.identifier()); // Vanilla order is incorrect, both Item and MobEffect depend on Attribute at construction time.
        ordered.add(Registries.DATA_COMPONENT_TYPE.identifier()); // Vanilla order is incorrect, Item depends on data components at construction time.
        ordered.add(Registries.PARTICLE_TYPE.identifier()); // Vanilla order is incorrect, both Block and MobEffect depend on ParticleType at construction time.
        // VersionUnbound: NeoForge adds getVanillaRegistrationOrder() to BuiltInRegistries; it returns this key set, unmodifiable.
        ordered.addAll(BuiltInRegistriesAccessor.getLoaders().keySet());
        ordered.addAll(BuiltInRegistries.REGISTRY.keySet().stream().sorted(Identifier::compareNamespaced).toList());
        return ordered;
    }
}
