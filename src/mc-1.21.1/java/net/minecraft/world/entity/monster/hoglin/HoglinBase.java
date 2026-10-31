package net.minecraft.world.entity.monster.hoglin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;

public interface HoglinBase {
    int ATTACK_ANIMATION_DURATION = 10;

    int getAttackAnimationRemainingTicks();

    static boolean hurtAndThrowTarget(LivingEntity hoglin, LivingEntity target) {
        float f = (float)hoglin.getAttributeValue(Attributes.ATTACK_DAMAGE);
        float g;
        if (!hoglin.isBaby() && (int)f > 0) {
            g = f / 2.0F + hoglin.level().random.nextInt((int)f);
        } else {
            g = f;
        }

        DamageSource damageSource = hoglin.damageSources().mobAttack(hoglin);
        boolean bl = target.hurt(damageSource, g);
        if (bl) {
            if (hoglin.level() instanceof ServerLevel serverLevel) {
                EnchantmentHelper.doPostAttackEffects(serverLevel, target, damageSource);
            }

            if (!hoglin.isBaby()) {
                throwTarget(hoglin, target);
            }
        }

        return bl;
    }

    static void throwTarget(LivingEntity hoglin, LivingEntity target) {
        double d = hoglin.getAttributeValue(Attributes.ATTACK_KNOCKBACK);
        double e = target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        double f = d - e;
        if (!(f <= 0.0)) {
            double g = target.getX() - hoglin.getX();
            double h = target.getZ() - hoglin.getZ();
            float i = hoglin.level().random.nextInt(21) - 10;
            double j = f * (hoglin.level().random.nextFloat() * 0.5F + 0.2F);
            Vec3 vec3 = new Vec3(g, 0.0, h).normalize().scale(j).yRot(i);
            double k = f * hoglin.level().random.nextFloat() * 0.5;
            target.push(vec3.x, k, vec3.z);
            target.hurtMarked = true;
        }
    }
}
