package net.salutaryfish.versionunbound.neoforge.coremods;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Stands in for NeoForge's coremods (its {@code coremods} subproject), which rewrite vanilla classes before mixins
 * apply. A carrier mixin without members names the classes a coremod transforms; in this plugin's {@link #preApply},
 * for the carrier, the coremod's transformation runs on the class.
 * <p>
 * Why {@code preApply}: Mixin applies all mixins of a class, from every config, in one batch, and calls every mixin's
 * {@code preApply} (this plugin's for ours) before any injector runs. So the rewritten code is what every mod's
 * injectors see, as with NeoForge's coremods, which run before Mixin.
 * <p>
 * Ported so far: {@link MethodRedirector} (vanilla's {@code Mob.finalizeSpawn} calls). Not ported: the two field
 * coremods ({@code ReplaceFieldWithGetterAccess}, {@code ReplaceFieldComparisonWithInstanceOf}), which belong to
 * features that aren't ported yet.
 * <p>
 * The same way, not a coremod of NeoForge's: the static methods NeoForge's patches add to vanilla classes
 * ({@link AddedStaticMethods}, carrier {@code AddedStaticMethodsTargetsMixin}), which a mixin can't add.
 */
public final class CoremodMixinPlugin implements IMixinConfigPlugin {
    private static final Logger LOGGER = LogManager.getLogger();

    private static final Map<String, List<MethodRedirector.MethodRedirection>> REDIRECTIONS_BY_CARRIER = Map.of(
            "net.salutaryfish.versionunbound.neoforge.mixin.coremods.FinalizeSpawnTargetsMixin", MethodRedirector.REDIRECTIONS);
    private static final String ADDED_STATIC_METHODS_CARRIER = "net.salutaryfish.versionunbound.neoforge.mixin.coremods.AddedStaticMethodsTargetsMixin";

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        if (mixinClassName.equals(ADDED_STATIC_METHODS_CARRIER)) {
            // Every target gets at least one method, so none means the lists in the carrier and AddedStaticMethods differ.
            if (AddedStaticMethods.add(targetClass) == 0) {
                LOGGER.warn("Added no static method to {} for {}", targetClassName, mixinClassName);
            }
            return;
        }
        var redirections = REDIRECTIONS_BY_CARRIER.get(mixinClassName);
        if (redirections == null) {
            return;
        }
        int replaced = MethodRedirector.transform(targetClass, redirections);
        // Every target has at least one call (NeoForge generates its list from the vanilla jar), so none means that
        // vanilla changed or that something else rewrote the class first.
        if (replaced == 0) {
            LOGGER.warn("Found no call to redirect in {} for {}", targetClassName, mixinClassName);
        } else {
            LOGGER.debug("Redirected {} call(s) in {} for {}", replaced, targetClassName, mixinClassName);
        }
    }

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
