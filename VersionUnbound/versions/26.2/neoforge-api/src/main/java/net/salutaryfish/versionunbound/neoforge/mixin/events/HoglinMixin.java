package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zoglin;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.neoforged.neoforge.event.EventHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * NeoForge's patch to {@code Hoglin}: {@code LivingConversionEvent.Pre} once the hoglin has spent long enough in the
 * overworld (canceling it keeps the hoglin; the timer goes on, so it fires again next tick unless a listener sets it),
 * and {@code Post} at the end of the conversion callback, after the zoglin gets nausea.
 */
@Mixin(Hoglin.class)
abstract class HoglinMixin {
    @Shadow
    private int timeInOverworld;

    @Definition(id = "timeInOverworld", field = "Lnet/minecraft/world/entity/monster/hoglin/Hoglin;timeInOverworld:I")
    @Expression("this.timeInOverworld > 300")
    @ModifyExpressionValue(method = "customServerAiStep", at = @At("MIXINEXTRAS:EXPRESSION"))
    private boolean versionunbound$canLivingConvert(boolean converts) {
        return converts && EventHooks.canLivingConvert((LivingEntity) (Object) this, EntityTypes.ZOGLIN, timer -> this.timeInOverworld = timer);
    }

    // Vanilla's callback is a static lambda (it doesn't use the hoglin), so the event is added around it.
    @ModifyArg(
            method = "finishConversion",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/monster/hoglin/Hoglin;convertTo(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/entity/ConversionParams;Lnet/minecraft/world/entity/ConversionParams$AfterConversion;)Lnet/minecraft/world/entity/Mob;"))
    private ConversionParams.AfterConversion<Zoglin> versionunbound$onLivingConvert(ConversionParams.AfterConversion<Zoglin> afterConversion) {
        return zoglin -> {
            afterConversion.finalizeConversion(zoglin);
            EventHooks.onLivingConvert((LivingEntity) (Object) this, zoglin);
        };
    }
}
