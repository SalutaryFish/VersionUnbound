package net.salutaryfish.versionunbound.loader;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.DeferredWorkQueue;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Drives NeoForge mods through their lifecycle on Fabric.
 */
public final class NeoModLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger("VersionUnbound");

    private static List<NeoModCandidate> candidates = List.of();
    private static final List<NeoModContainer> containers = new ArrayList<>();

    private NeoModLoader() {}

    static void setCandidates(List<NeoModCandidate> mods) {
        candidates = List.copyOf(mods);
    }

    public static List<NeoModContainer> getContainers() {
        return List.copyOf(containers);
    }

    /** Constructs every discovered mod, then fires the common setup event. */
    static void loadMods() {
        Dist dist = FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT ? Dist.CLIENT : Dist.DEDICATED_SERVER;
        ClassLoader classLoader = NeoModLoader.class.getClassLoader();

        for (NeoModCandidate candidate : candidates) {
            List<Class<?>> modClasses = new ArrayList<>();
            for (String className : candidate.modClasses()) {
                try {
                    modClasses.add(Class.forName(className, true, classLoader));
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException("Mod class " + className + " of " + candidate.modId() + " is not on the classpath", e);
                }
            }
            NeoModContainer container = new NeoModContainer(candidate.modId(), modClasses, dist);
            container.constructMod();
            containers.add(container);
            LOGGER.info("Constructed NeoForge mod {}", candidate.modId());
        }

        DeferredWorkQueue commonSetupQueue = new DeferredWorkQueue("common setup");
        for (NeoModContainer container : containers) {
            container.acceptEvent(new FMLCommonSetupEvent(container, commonSetupQueue));
        }
        commonSetupQueue.runTasks();
    }
}
