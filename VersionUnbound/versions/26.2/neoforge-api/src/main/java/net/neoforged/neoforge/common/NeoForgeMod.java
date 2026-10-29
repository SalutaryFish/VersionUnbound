/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.common;

import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.CrashReportCallables;
import net.neoforged.fml.FMLVersion;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.NeoForgeRegistriesSetup;
import net.neoforged.neoforge.registries.holdersets.AndHolderSet;
import net.neoforged.neoforge.registries.holdersets.AnyHolderSet;
import net.neoforged.neoforge.registries.holdersets.HolderSetType;
import net.neoforged.neoforge.registries.holdersets.NotHolderSet;
import net.neoforged.neoforge.registries.holdersets.OrHolderSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

// VersionUnbound: NeoForge's own mod, ported a feature at a time. It holds what is ported so far, in NeoForge's order;
// the rest of NeoForge's fields, listeners and registrations arrive with their features: attributes, command arguments,
// loot, biome and structure modifiers, slot displays, ingredients, conditions, fluids (and milk), entity and data
// component predicates, ticket types, environment attributes, configs, capabilities, data maps and permissions.
@SuppressWarnings("unused")
@Mod(NeoForgeMod.MOD_ID)
public class NeoForgeMod {
    public static final String MOD_ID = "neoforge";

    public static final String VERSION_CHECK_CAT = "version_checking";
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Marker NEOFORGEMOD = MarkerManager.getMarker("NEOFORGE-MOD");

    private static final DeferredRegister<HolderSetType> HOLDER_SET_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.HOLDER_SET_TYPES, MOD_ID);

    public static final Holder<HolderSetType> ANY_HOLDER_SET = HOLDER_SET_TYPES.register("any", AnyHolderSet.Type::new);

    public static final Holder<HolderSetType> AND_HOLDER_SET = HOLDER_SET_TYPES.register("and", AndHolderSet.Type::new);

    public static final Holder<HolderSetType> OR_HOLDER_SET = HOLDER_SET_TYPES.register("or", OrHolderSet.Type::new);

    public static final Holder<HolderSetType> NOT_HOLDER_SET = HOLDER_SET_TYPES.register("not", NotHolderSet.Type::new);

    public NeoForgeMod(IEventBus modEventBus, Dist dist, ModContainer container) {
        // VersionUnbound: NeoForgeVersion reads NeoForge's jar manifest; Layer A's version comes from FML's VersionInfo.
        String neoForgeVersion = FMLLoader.getCurrent().getVersionInfo().neoForgeVersion();
        LOGGER.info(NEOFORGEMOD, "NeoForge mod loading, version {}, for MC {}", neoForgeVersion, SharedConstants.getCurrentVersion().name());

        CrashReportCallables.registerCrashCallable("Crash Report UUID", () -> {
            final UUID uuid = UUID.randomUUID();
            LOGGER.fatal("Preparing crash report with UUID {}", uuid);
            return uuid.toString();
        });

        CrashReportCallables.registerCrashCallable("FML", FMLVersion::getVersion);
        CrashReportCallables.registerCrashCallable("NeoForge", () -> neoForgeVersion);

        HOLDER_SET_TYPES.register(modEventBus);
        NeoForgeRegistriesSetup.setup(modEventBus);

        NeoForge.EVENT_BUS.register(new NeoForgeEventHandler());
    }
}
