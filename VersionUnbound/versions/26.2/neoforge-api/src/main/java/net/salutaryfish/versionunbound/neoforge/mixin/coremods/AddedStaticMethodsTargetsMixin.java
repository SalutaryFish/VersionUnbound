package net.salutaryfish.versionunbound.neoforge.mixin.coremods;

import org.spongepowered.asm.mixin.Mixin;

/**
 * Carrier for the static methods NeoForge's patches add to vanilla classes: {@code CoremodMixinPlugin} adds them to
 * these classes ({@code AddedStaticMethods} lists them, and the constructors). One target per class that gets one.
 */
@Mixin(targets = {"net.minecraft.world.level.block.CropBlock", "net.minecraft.world.item.BoneMealItem", "net.minecraft.resources.RegistryOps",
        "net.minecraft.core.registries.BuiltInRegistries", "net.minecraft.world.entity.SpawnPlacements",
        "net.minecraft.world.entity.ai.attributes.AttributeSupplier$Builder"})
abstract class AddedStaticMethodsTargetsMixin {}
