package net.minecraft.world.entity.monster;

import javax.annotation.Nullable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public interface CrossbowAttackMob extends RangedAttackMob {
    void setChargingCrossbow(boolean var1);

    @Nullable
    LivingEntity getTarget();

    void onCrossbowAttackPerformed();

    default void performCrossbowAttack(LivingEntity user, float velocity) {
        InteractionHand interactionHand = ProjectileUtil.getWeaponHoldingHand(user, Items.CROSSBOW);
        ItemStack itemStack = user.getItemInHand(interactionHand);
        if (itemStack.getItem() instanceof CrossbowItem crossbowItem) {
            crossbowItem.performShooting(
                user.level(), user, interactionHand, itemStack, velocity, 14 - user.level().getDifficulty().getId() * 4, this.getTarget()
            );
        }

        this.onCrossbowAttackPerformed();
    }
}
