package net.salutaryfish.versionunbound.loader;

import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.BusBuilder;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.IModBusEvent;

/**
 * The container for a NeoForge mod loaded on Fabric. Mirrors FML's {@code FMLModContainer}:
 * one event bus per mod, and mod constructors receive their arguments by type.
 */
public class NeoModContainer extends ModContainer {
    private final IEventBus eventBus;
    private final List<Class<?>> modClasses;
    private final Dist dist;

    public NeoModContainer(String modId, List<Class<?>> modClasses, Dist dist) {
        super(modId);
        this.modClasses = modClasses;
        this.dist = dist;
        this.eventBus = BusBuilder.builder()
                .markerType(IModBusEvent.class)
                .allowPerPhasePost()
                .build();
    }

    @Override
    public IEventBus getEventBus() {
        return eventBus;
    }

    @Override
    protected void constructMod() {
        for (Class<?> modClass : modClasses) {
            var constructors = modClass.getConstructors();
            if (constructors.length != 1) {
                throw new RuntimeException("Mod class " + modClass + " must have exactly 1 public constructor, found " + constructors.length);
            }
            var constructor = constructors[0];

            Map<Class<?>, Object> allowedConstructorArgs = Map.of(
                    IEventBus.class, eventBus,
                    ModContainer.class, this,
                    NeoModContainer.class, this,
                    Dist.class, dist);

            var parameterTypes = constructor.getParameterTypes();
            Object[] constructorArgs = new Object[parameterTypes.length];
            Set<Class<?>> foundArgs = new HashSet<>();
            for (int i = 0; i < parameterTypes.length; i++) {
                Object arg = allowedConstructorArgs.get(parameterTypes[i]);
                if (arg == null) {
                    throw new RuntimeException("Mod constructor has unsupported argument " + parameterTypes[i] + ". Allowed optional argument classes: " +
                            allowedConstructorArgs.keySet().stream().map(Class::getSimpleName).collect(Collectors.joining(", ")));
                }
                if (!foundArgs.add(parameterTypes[i])) {
                    throw new RuntimeException("Duplicate mod constructor argument type: " + parameterTypes[i]);
                }
                constructorArgs[i] = arg;
            }

            try {
                constructor.newInstance(constructorArgs);
            } catch (InvocationTargetException e) {
                throw new RuntimeException("Failed to construct mod " + modId + " (" + modClass.getName() + ")", e.getCause());
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("Failed to construct mod " + modId + " (" + modClass.getName() + ")", e);
            }
        }
    }
}
