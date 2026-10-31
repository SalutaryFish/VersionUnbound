package net.minecraft.world.item.enchantment.effects;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.LevelBasedValue;
import net.minecraft.world.phys.Vec3;

public record DamageItem(LevelBasedValue amount) implements EnchantmentEntityEffect {
    public static final MapCodec<DamageItem> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(LevelBasedValue.CODEC.fieldOf("amount").forGetter(damageItem -> damageItem.amount)).apply(instance, DamageItem::new)
    );

    @Override
    public void apply(ServerLevel level, int enchantmentLevel, EnchantedItemInUse item, Entity entity, Vec3 origin) {
        ServerPlayer serverPlayer2 = item.owner() instanceof ServerPlayer serverPlayer ? serverPlayer : null;
        item.itemStack().hurtAndBreak((int)this.amount.calculate(enchantmentLevel), level, serverPlayer2, item.onBreak());
    }

    @Override
    public MapCodec<DamageItem> codec() {
        return CODEC;
    }
}
