package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.neoforged.neoforge.event.EventHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NeoForge's patch to {@code Zombie}: {@code LivingConversionEvent.Pre} before a drowning zombie turns into a drowned
 * (canceling it keeps the zombie; the timer has run out, so it fires again next tick unless a listener sets it), and
 * before a villager the zombie killed may become a zombie villager (on normal and hard, ahead of normal's coin flip;
 * canceling it leaves the villager dead). {@code Post} at the end of {@code convertToZombieType} (drowned, and husks'
 * zombies) and after the zombie villager has the villager's data, before the conversion sound.
 */
@Mixin(Zombie.class)
abstract class ZombieMixin {
    @Shadow
    private int conversionTime;

    @Inject(method = "doUnderWaterConversion", at = @At("HEAD"), cancellable = true)
    private void versionunbound$canLivingConvert(ServerLevel level, CallbackInfo ci) {
        if (!EventHooks.canLivingConvert((LivingEntity) (Object) this, EntityTypes.DROWNED, timer -> this.conversionTime = timer)) {
            ci.cancel();
        }
    }

    // Vanilla's callback is a static lambda (it doesn't use the old zombie), so the event is added around it.
    @ModifyArg(
            method = "convertToZombieType",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/monster/zombie/Zombie;convertTo(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/entity/ConversionParams;Lnet/minecraft/world/entity/ConversionParams$AfterConversion;)Lnet/minecraft/world/entity/Mob;"))
    private ConversionParams.AfterConversion<Zombie> versionunbound$onLivingConvert(ConversionParams.AfterConversion<Zombie> afterConversion) {
        return newZombie -> {
            afterConversion.finalizeConversion(newZombie);
            EventHooks.onLivingConvert((LivingEntity) (Object) this, newZombie);
        };
    }

    @Definition(id = "entity", local = @Local(type = LivingEntity.class, argsOnly = true))
    @Definition(id = "Villager", type = Villager.class)
    @Expression("entity instanceof Villager")
    @ModifyExpressionValue(method = "killedEntity", at = @At("MIXINEXTRAS:EXPRESSION"))
    private boolean versionunbound$canLivingConvertVillager(boolean isVillager, @Local(argsOnly = true) LivingEntity entity) {
        return isVillager && EventHooks.canLivingConvert(entity, EntityTypes.ZOMBIE_VILLAGER, timer -> {});
    }

    @Inject(
            method = "lambda$convertVillagerToZombieVillager$0",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/monster/zombie/ZombieVillager;setVillagerXp(I)V", shift = At.Shift.AFTER))
    private void versionunbound$onLivingConvertVillager(Villager villager, ServerLevel level, ZombieVillager zombie, CallbackInfo ci) {
        EventHooks.onLivingConvert(villager, zombie);
    }
}
