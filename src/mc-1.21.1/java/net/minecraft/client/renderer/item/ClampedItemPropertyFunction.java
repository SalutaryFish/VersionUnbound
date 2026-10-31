package net.minecraft.client.renderer.item;

import javax.annotation.Nullable;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public interface ClampedItemPropertyFunction extends ItemPropertyFunction {
    @Deprecated
    @Override
    default float call(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity, int seed) {
        return Mth.clamp(this.unclampedCall(stack, level, entity, seed), 0.0F, 1.0F);
    }

    float unclampedCall(ItemStack var1, @Nullable ClientLevel var2, @Nullable LivingEntity var3, int var4);
}
