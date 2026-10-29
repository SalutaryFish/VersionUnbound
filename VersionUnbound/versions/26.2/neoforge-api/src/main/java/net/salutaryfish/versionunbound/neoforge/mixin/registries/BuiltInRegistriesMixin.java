package net.salutaryfish.versionunbound.neoforge.mixin.registries;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.function.Consumer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.registries.RegistryManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Vanilla validates its registries once, at bootstrap: none may be empty, and a defaulted one must hold its default.
 * Fabric API runs that again when it freezes the registries, by which time NeoForge mods have added theirs. NeoForge
 * doesn't hold modded registries to those rules, so the registries made in {@code NewRegistryEvent} are left out.
 */
@Mixin(BuiltInRegistries.class)
abstract class BuiltInRegistriesMixin {
    @WrapOperation(method = "validate", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/Registry;forEach(Ljava/util/function/Consumer;)V"))
    private static <T extends Registry<?>> void versionunbound$skipNewRegistries(Registry<T> registry, Consumer<T> check, Operation<Void> original) {
        original.call(registry, (Consumer<T>) entry -> {
            if (!RegistryManager.getNewRegistryKeys().contains(entry.key().identifier())) {
                check.accept(entry);
            }
        });
    }
}
