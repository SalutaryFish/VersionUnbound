package net.salutaryfish.versionunbound.loader;

import java.nio.file.Path;
import java.util.List;

/**
 * A NeoForge mod found in a jar: its metadata from neoforge.mods.toml and the classes annotated with {@code @Mod}.
 */
public record NeoModCandidate(Path jar, String modId, String version, List<String> modClasses, List<String> mixinConfigs) {
}
