package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.neoforged.neoforge.event.EventHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NeoForge's patch to {@code ZombieVillager}: {@code LivingConversionEvent.Pre} once a cure has run its time (canceling
 * it keeps the zombie villager; the timer counts on below zero, so it fires again next tick unless a listener sets it),
 * and {@code Post} at the end of the conversion callback.
 */
@Mixin(ZombieVillager.class)
abstract class ZombieVillagerMixin {
    @Shadow
    private int villagerConversionTime;

    @Definition(id = "villagerConversionTime", field = "Lnet/minecraft/world/entity/monster/zombie/ZombieVillager;villagerConversionTime:I")
    @Expression("this.villagerConversionTime <= 0")
    @ModifyExpressionValue(method = "tick", at = @At("MIXINEXTRAS:EXPRESSION"))
    private boolean versionunbound$canLivingConvert(boolean converts) {
        return converts && EventHooks.canLivingConvert((LivingEntity) (Object) this, EntityTypes.VILLAGER, timer -> this.villagerConversionTime = timer);
    }

    @Inject(method = "lambda$finishConversion$0", at = @At("TAIL"))
    private void versionunbound$onLivingConvert(ServerLevel level, Villager villager, CallbackInfo ci) {
        EventHooks.onLivingConvert((LivingEntity) (Object) this, villager);
    }
}
