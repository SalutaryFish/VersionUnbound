package net.salutaryfish.versionunbound.neoforge.mixin.registries;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.serialization.Lifecycle;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.resources.RegistryLoadTask;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.registries.DataPackRegistriesHooks;
import net.neoforged.neoforge.registries.RegistryBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * NeoForge's patch to {@link RegistryLoadTask}: a datapack registry is built with the {@link RegistryBuilder} options
 * its mod gave in {@code DataPackRegistryEvent.NewRegistry}. NeoForge keeps those options in a component it adds to
 * {@code RegistryDataLoader.RegistryData}; here {@link DataPackRegistriesHooks} keeps them by registry key.
 */
@Mixin(RegistryLoadTask.class)
abstract class RegistryLoadTaskMixin<T> {
    @WrapOperation(method = "<init>", at = @At(value = "NEW", target = "net/minecraft/core/MappedRegistry"))
    private MappedRegistry<T> versionunbound$buildRegistry(ResourceKey<? extends Registry<T>> key, Lifecycle lifecycle, Operation<MappedRegistry<T>> original) {
        var consumer = DataPackRegistriesHooks.getRegistryBuilderConsumer(key);
        if (consumer == null) {
            return original.call(key, lifecycle);
        }
        var registryBuilder = new RegistryBuilder<>(key);
        consumer.accept(registryBuilder);
        return (MappedRegistry<T>) registryBuilder.disableRegistrationCheck().create();
    }
}
