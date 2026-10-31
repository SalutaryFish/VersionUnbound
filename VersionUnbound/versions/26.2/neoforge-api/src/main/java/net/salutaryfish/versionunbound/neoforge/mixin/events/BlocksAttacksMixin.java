package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.EventHooks;
import net.salutaryfish.versionunbound.neoforge.patches.BlocksAttacksPatch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * NeoForge's patch to {@code BlocksAttacks}: the six-argument {@code hurtBlockingItem} with a fixed shield damage (the
 * one {@code LivingShieldBlockEvent} decides; {@code -1} keeps the item damage function), which vanilla's five-argument
 * one calls with {@code -1}; and the shield's durability taken on the server only, with a break callback that also fires
 * {@code PlayerDestroyItemEvent} with a copy of the shield and stops the player using it (NeoForge's fix for MC-168573:
 * after a shield breaks, the off hand can't finish using some items). Vanilla's five-argument method keeps its body
 * (other mods' mixins target it); the overload hands it the fixed damage ({@link BlocksAttacksPatch}).
 */
@Mixin(BlocksAttacks.class)
abstract class BlocksAttacksMixin {
    // Neo: NeoForge's overload, added under its name and descriptor (mods call it).
    public void hurtBlockingItem(Level level, ItemStack item, LivingEntity user, InteractionHand hand, float damage, int fixedDamage) {
        BlocksAttacksPatch.withFixedDamage(fixedDamage, () -> ((BlocksAttacks) (Object) this).hurtBlockingItem(level, item, user, hand, damage));
    }

    // Neo: fixedDamage < 0 ? this.itemDamage.apply(damage) : fixedDamage
    @ModifyExpressionValue(method = "hurtBlockingItem(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/InteractionHand;F)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/component/BlocksAttacks$ItemDamageFunction;apply(F)I"))
    private int versionunbound$fixedDamage(int itemDamage) {
        return BlocksAttacksPatch.itemDamage(itemDamage);
    }

    // VersionUnbound: NeoForge calls the LivingEntity overload of hurtAndBreak its ItemStack patch adds (for the item
    // extension's damageItem, Phase 2 step 5); for the player this method damages shields for, vanilla's ServerPlayer
    // overload does the same. NeoForge's replacement doesn't call vanilla's, so neither does this.
    @WrapOperation(method = "hurtBlockingItem(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/InteractionHand;F)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;)V"))
    private void versionunbound$hurtShield(ItemStack item, int itemDamage, LivingEntity user, EquipmentSlot slot, Operation<Void> original,
            Level level, ItemStack itemArgument, LivingEntity userArgument, InteractionHand hand, float damage) {
        if (level instanceof ServerLevel serverLevel) {
            Player player = (Player) user;
            ItemStack copy = item.copy();
            item.hurtAndBreak(itemDamage, serverLevel, user instanceof ServerPlayer serverPlayer ? serverPlayer : null, it -> {
                user.onEquippedItemBroken(it, hand.asEquipmentSlot());
                EventHooks.onPlayerDestroyItem(player, copy, hand);
                player.stopUsingItem(); // Neo: Fix MC-168573 ("After breaking a shield, the player's off-hand can't finish using some items")
            });
        }
    }
}
