package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.neoforged.neoforge.common.CommonHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NeoForge's patch to {@code DefaultAttributes}: the attributes from {@code EntityAttributeCreationEvent} and
 * {@code EntityAttributeModificationEvent} ({@link CommonHooks#modifyAttributes}) come before vanilla's. Fabric API's
 * {@code FabricDefaultAttributeRegistry} writes into vanilla's map, so NeoForge's entry for a type wins over a Fabric
 * mod's.
 */
@Mixin(DefaultAttributes.class)
abstract class DefaultAttributesMixin {
    @SuppressWarnings("deprecation")
    @Inject(method = "getSupplier", at = @At("HEAD"), cancellable = true)
    private static void versionunbound$neoForgeSupplier(EntityType<? extends LivingEntity> type, CallbackInfoReturnable<AttributeSupplier> cir) {
        AttributeSupplier supplier = CommonHooks.getAttributesView().get(type);
        if (supplier != null) cir.setReturnValue(supplier);
    }

    @SuppressWarnings("deprecation")
    @ModifyReturnValue(method = "hasSupplier", at = @At("RETURN"))
    private static boolean versionunbound$hasNeoForgeSupplier(boolean hasSupplier, EntityType<?> type) {
        return hasSupplier || CommonHooks.getAttributesView().containsKey(type);
    }
}
