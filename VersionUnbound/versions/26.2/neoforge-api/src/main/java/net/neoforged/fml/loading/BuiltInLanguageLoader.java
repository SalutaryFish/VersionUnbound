/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import net.neoforged.fml.FMLVersion;
import net.neoforged.neoforgespi.language.IModLanguageLoader;

public abstract class BuiltInLanguageLoader implements IModLanguageLoader {
    /**
     * VersionUnbound: built-in loaders always report the FML version that Layer A implements. FML reads the
     * jar manifest instead, but our classes sit inside a nested Fabric jar without FML's manifest.
     */
    @Override
    public String version() {
        return FMLVersion.getVersion();
    }
}
