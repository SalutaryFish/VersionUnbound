package net.salutaryfish.versionunbound.testmod.events;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableCommentedConfig;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;

/**
 * The smallest config spec FML accepts: a single {@code probe = true}. Mods normally use NeoForge's ModConfigSpec,
 * which Layer A doesn't have yet; this exercises FML's config loading on its own.
 */
final class ProbeConfigSpec implements IConfigSpec {
    volatile ILoadedConfig loaded;

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public void validateSpec(ModConfig config) {}

    @Override
    public boolean isCorrect(UnmodifiableCommentedConfig config) {
        return config.get("probe") instanceof Boolean;
    }

    @Override
    public void correct(CommentedConfig config) {
        config.set("probe", true);
    }

    @Override
    public void acceptConfig(ILoadedConfig config) {
        loaded = config;
    }
}
