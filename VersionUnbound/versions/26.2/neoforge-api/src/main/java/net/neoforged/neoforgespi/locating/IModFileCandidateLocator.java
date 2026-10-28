/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforgespi.locating;

import net.neoforged.neoforgespi.ILaunchContext;

/**
 * Loaded as a ServiceLoader. Takes mechanisms for locating candidate "mod" JARs.
 */
public interface IModFileCandidateLocator extends IOrderedProvider {
    // VersionUnbound: forFolder(File, String) is omitted; VersionUnbound does its own discovery and runs no locator plugins.

    /**
     * Discovers potential mods to be loaded by FML.
     *
     * @param pipeline Adds discovered mods and issues to this pipeline.
     */
    void findCandidates(ILaunchContext context, IDiscoveryPipeline pipeline);
}
