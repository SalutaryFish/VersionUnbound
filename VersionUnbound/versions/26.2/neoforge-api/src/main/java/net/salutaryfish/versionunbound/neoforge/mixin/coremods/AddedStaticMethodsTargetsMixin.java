package net.salutaryfish.versionunbound.neoforge.mixin.coremods;

import org.spongepowered.asm.mixin.Mixin;

/**
 * Carrier for the static methods NeoForge's patches add to vanilla classes: {@code CoremodMixinPlugin} adds them to
 * these classes ({@code AddedStaticMethods} lists them). One target per class that gets a method.
 */
@Mixin(targets = {"net.minecraft.world.level.block.CropBlock", "net.minecraft.world.item.BoneMealItem"})
abstract class AddedStaticMethodsTargetsMixin {}
