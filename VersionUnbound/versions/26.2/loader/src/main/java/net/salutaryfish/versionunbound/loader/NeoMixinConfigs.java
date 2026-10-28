/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.salutaryfish.versionunbound.loader;

import java.util.HashMap;
import java.util.Map;
import net.neoforged.fml.ModLoader;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.fml.loading.moddiscovery.ModFile;
import net.neoforged.fml.loading.moddiscovery.ModFileParser;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.FabricUtil;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.transformer.Config;

/**
 * Registers the {@code [[mixins]]} configs of NeoForge mods with Fabric's Mixin. Adapted from FML's
 * {@code MixinFacade.addMixins}: same checks for required mods, behavior version and duplicates, and each config is
 * tagged with its mod id and behavior version the way both FML and Fabric Loader do. FML hands Mixin the config
 * bytes directly; here Mixin reads them through Knot, so the mod jars must already be on the classpath.
 */
final class NeoMixinConfigs {
    private static final Logger LOGGER = LoggerFactory.getLogger("VersionUnbound");

    // Increment to break compatibility; during a BC window, this should be set to the latest version. This is _not_ set
    // to COMPATIBILITY_LATEST, so that if mixin is bumped past a BC it does not break mods.
    private static final int DEFAULT_BEHAVIOUR_VERSION = FabricUtil.COMPATIBILITY_0_17_1;
    private static final ArtifactVersion HIGHEST_MIXIN_VERSION = versionOf(FabricUtil.COMPATIBILITY_LATEST);
    private static final ArtifactVersion LOWEST_MIXIN_VERSION = versionOf(DEFAULT_BEHAVIOUR_VERSION);

    private NeoMixinConfigs() {}

    static void register(LoadingModList modList) {
        record AnnotationInfo(ModFile modFile, int behaviorVersion) {}

        Map<String, AnnotationInfo> configAnnotationInfo = new HashMap<>();

        for (var modFileInfo : modList.getModFiles()) {
            var modFile = modFileInfo.getFile();
            for (var mixinConfig : modFile.getMixinConfigs()) {
                if (!areRequiredModsPresent(modFile, mixinConfig, modList)) {
                    continue;
                }

                // Validate the mixin version is supported
                if (!validateMixinBehavior(modFile, mixinConfig)) {
                    continue;
                }

                var currentInfo = new AnnotationInfo(modFile, calculateBehaviorVersion(mixinConfig.behaviorVersion()));
                var existingInfo = configAnnotationInfo.putIfAbsent(mixinConfig.config(), currentInfo);
                if (existingInfo != null && existingInfo.modFile() != modFile) {
                    ModLoader.addLoadingIssue(ModLoadingIssue.error(
                            "fml.modloadingissue.mixin.duplicate_config",
                            mixinConfig.config(),
                            existingInfo.modFile)
                            .withAffectedModFile(modFile));
                    continue;
                }

                if (!modFile.getContents().containsFile(mixinConfig.config())) {
                    ModLoader.addLoadingIssue(ModLoadingIssue.error(
                            "fml.modloadingissue.mixin.missing_config",
                            mixinConfig.config())
                            .withAffectedModFile(modFile));
                    continue;
                }

                Mixins.addConfiguration(mixinConfig.config());
            }
        }

        for (Config config : Mixins.getConfigs()) {
            var annotationInfo = configAnnotationInfo.get(config.getName());
            if (annotationInfo != null) {
                config.getConfig().decorate(FabricUtil.KEY_MOD_ID, annotationInfo.modFile().getId());
                config.getConfig().decorate(FabricUtil.KEY_COMPATIBILITY, annotationInfo.behaviorVersion());
            }
        }
    }

    private static boolean areRequiredModsPresent(ModFile modFile, ModFileParser.MixinConfig mixinConfig, LoadingModList modList) {
        for (var requiredModId : mixinConfig.requiredMods()) {
            if (modList.getModFileById(requiredModId) == null) {
                LOGGER.info("Mixin config {} from {} not applied as required mod '{}' is missing", mixinConfig.config(), modFile, requiredModId);
                return false;
            }
        }
        return true;
    }

    private static boolean validateMixinBehavior(ModFile modFile, ModFileParser.MixinConfig mixinConfig) {
        var behaviorVersion = mixinConfig.behaviorVersion();
        if (behaviorVersion == null) {
            return true; // Just uses default
        }

        if (behaviorVersion.compareTo(HIGHEST_MIXIN_VERSION) > 0) {
            ModLoader.addLoadingIssue(ModLoadingIssue.error(
                    "fml.modloadingissue.mixin.requested_behavior_too_new",
                    mixinConfig.config(),
                    behaviorVersion,
                    HIGHEST_MIXIN_VERSION).withAffectedModFile(modFile));
            return false;
        } else if (behaviorVersion.compareTo(LOWEST_MIXIN_VERSION) < 0) {
            ModLoader.addLoadingIssue(ModLoadingIssue.error(
                    "fml.modloadingissue.mixin.requested_behavior_too_old",
                    mixinConfig.config(),
                    behaviorVersion,
                    LOWEST_MIXIN_VERSION).withAffectedModFile(modFile));
            return false;
        }
        return true;
    }

    private static int calculateBehaviorVersion(@Nullable ArtifactVersion behaviorVersion) {
        if (behaviorVersion == null) {
            return DEFAULT_BEHAVIOUR_VERSION;
        }
        return behaviorVersion.getMajorVersion() * (1000 * 1000) +
                behaviorVersion.getMinorVersion() * 1000 +
                behaviorVersion.getIncrementalVersion();
    }

    /** Turns a {@link FabricUtil} compatibility constant such as 17004 back into "0.17.4". */
    private static ArtifactVersion versionOf(int compatibility) {
        return new DefaultArtifactVersion((compatibility / 1_000_000) + "." + (compatibility / 1000 % 1000) + "." + (compatibility % 1000));
    }
}
