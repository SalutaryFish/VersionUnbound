package net.salutaryfish.versionunbound.neoforge.mixin.coremods;

import org.spongepowered.asm.mixin.Mixin;

/**
 * Carrier for the static methods NeoForge's patches add to vanilla classes: {@code CoremodMixinPlugin} adds them to
 * these classes ({@code AddedStaticMethods} lists them, and the constructors). One target per class that gets one.
 */
@Mixin(targets = {"net.minecraft.world.level.block.CropBlock", "net.minecraft.world.item.BoneMealItem", "net.minecraft.resources.RegistryOps",
        "net.minecraft.core.registries.BuiltInRegistries", "net.minecraft.world.entity.SpawnPlacements",
        "net.minecraft.world.entity.ai.attributes.AttributeSupplier$Builder", "net.minecraft.tags.BlockTags", "net.minecraft.tags.ItemTags",
        "net.minecraft.tags.FluidTags", "net.minecraft.world.item.DyeColor", "net.minecraft.world.item.AxeItem", "net.minecraft.world.item.ShovelItem"})
abstract class AddedStaticMethodsTargetsMixin {}
