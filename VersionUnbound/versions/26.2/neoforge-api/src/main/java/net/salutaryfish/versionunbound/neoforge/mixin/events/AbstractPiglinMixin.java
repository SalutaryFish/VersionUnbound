package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.neoforged.neoforge.event.EventHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * NeoForge's patch to {@code AbstractPiglin} (piglins and brutes): {@code LivingConversionEvent.Pre} once the piglin has
 * spent long enough in the overworld (canceling it keeps the piglin; the timer goes on, so it fires again next tick unless
 * a listener sets it), and {@code Post} at the end of the conversion callback, after the zombified piglin gets nausea.
 */
@Mixin(AbstractPiglin.class)
abstract class AbstractPiglinMixin {
    @Shadow
    protected int timeInOverworld;

    @Definition(id = "timeInOverworld", field = "Lnet/minecraft/world/entity/monster/piglin/AbstractPiglin;timeInOverworld:I")
    @Expression("this.timeInOverworld > 300")
    @ModifyExpressionValue(method = "customServerAiStep", at = @At("MIXINEXTRAS:EXPRESSION"))
    private boolean versionunbound$canLivingConvert(boolean converts) {
        return converts && EventHooks.canLivingConvert((LivingEntity) (Object) this, EntityTypes.ZOMBIFIED_PIGLIN, timer -> this.timeInOverworld = timer);
    }

    // Vanilla's callback is a static lambda (it doesn't use the piglin), so the event is added around it.
    @ModifyArg(
            method = "finishConversion",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/monster/piglin/AbstractPiglin;convertTo(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/entity/ConversionParams;Lnet/minecraft/world/entity/ConversionParams$AfterConversion;)Lnet/minecraft/world/entity/Mob;"))
    private ConversionParams.AfterConversion<ZombifiedPiglin> versionunbound$onLivingConvert(ConversionParams.AfterConversion<ZombifiedPiglin> afterConversion) {
        return zombified -> {
            afterConversion.finalizeConversion(zombified);
            EventHooks.onLivingConvert((LivingEntity) (Object) this, zombified);
        };
    }
}
