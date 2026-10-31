package net.minecraft.world.entity.projectile;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class SmallFireball extends Fireball {
    public SmallFireball(EntityType<? extends SmallFireball> entityType, Level level) {
        super(entityType, level);
    }

    public SmallFireball(Level level, LivingEntity owner, Vec3 movement) {
        super(EntityType.SMALL_FIREBALL, owner, movement, level);
    }

    public SmallFireball(Level level, double x, double y, double z, Vec3 movement) {
        super(EntityType.SMALL_FIREBALL, x, y, z, movement, level);
    }

    /**
     * Called when the arrow hits an entity
     */
    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (this.level() instanceof ServerLevel serverLevel) {
            Entity var7 = result.getEntity();
            Entity entity2 = this.getOwner();
            int i = var7.getRemainingFireTicks();
            var7.igniteForSeconds(5.0F);
            DamageSource damageSource = this.damageSources().fireball(this, entity2);
            if (!var7.hurt(damageSource, 5.0F)) {
                var7.setRemainingFireTicks(i);
            } else {
                EnchantmentHelper.doPostAttackEffects(serverLevel, var7, damageSource);
            }
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (!this.level().isClientSide) {
            Entity entity = this.getOwner();
            if (!(entity instanceof Mob) || this.level().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
                BlockPos blockPos = result.getBlockPos().relative(result.getDirection());
                if (this.level().isEmptyBlock(blockPos)) {
                    this.level().setBlockAndUpdate(blockPos, BaseFireBlock.getState(this.level(), blockPos));
                }
            }
        }
    }

    /**
     * Called when this EntityFireball hits a block or entity.
     */
    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!this.level().isClientSide) {
            this.discard();
        }
    }

    /**
     * Called when the entity is attacked.
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }
}
