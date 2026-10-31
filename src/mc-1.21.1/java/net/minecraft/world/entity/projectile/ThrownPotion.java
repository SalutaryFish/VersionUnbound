package net.minecraft.world.entity.projectile;

import it.unimi.dsi.fastutil.doubles.DoubleDoubleImmutablePair;
import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractCandleBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

public class ThrownPotion extends ThrowableItemProjectile implements ItemSupplier {
    public static final double SPLASH_RANGE = 4.0;
    private static final double SPLASH_RANGE_SQ = 16.0;
    public static final Predicate<LivingEntity> WATER_SENSITIVE_OR_ON_FIRE = livingEntity -> livingEntity.isSensitiveToWater() || livingEntity.isOnFire();

    public ThrownPotion(EntityType<? extends ThrownPotion> entityType, Level level) {
        super(entityType, level);
    }

    public ThrownPotion(Level level, LivingEntity shooter) {
        super(EntityType.POTION, shooter, level);
    }

    public ThrownPotion(Level level, double x, double y, double z) {
        super(EntityType.POTION, x, y, z, level);
    }

    @Override
    protected Item getDefaultItem() {
        return Items.SPLASH_POTION;
    }

    @Override
    protected double getDefaultGravity() {
        return 0.05;
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (!this.level().isClientSide) {
            ItemStack itemStack = this.getItem();
            Direction direction = result.getDirection();
            BlockPos blockPos = result.getBlockPos();
            BlockPos blockPos2 = blockPos.relative(direction);
            PotionContents potionContents = itemStack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
            if (potionContents.is(Potions.WATER)) {
                this.dowseFire(blockPos2);
                this.dowseFire(blockPos2.relative(direction.getOpposite()));

                for (Direction direction2 : Direction.Plane.HORIZONTAL) {
                    this.dowseFire(blockPos2.relative(direction2));
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
            ItemStack itemStack = this.getItem();
            PotionContents potionContents = itemStack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
            if (potionContents.is(Potions.WATER)) {
                this.applyWater();
            } else if (potionContents.hasEffects()) {
                if (this.isLingering()) {
                    this.makeAreaOfEffectCloud(potionContents);
                } else {
                    this.applySplash(potionContents.getAllEffects(), result.getType() == HitResult.Type.ENTITY ? ((EntityHitResult)result).getEntity() : null);
                }
            }

            int i = potionContents.potion().isPresent() && potionContents.potion().get().value().hasInstantEffects() ? 2007 : 2002;
            this.level().levelEvent(i, this.blockPosition(), potionContents.getColor());
            this.discard();
        }
    }

    private void applyWater() {
        AABB aABB = this.getBoundingBox().inflate(4.0, 2.0, 4.0);

        for (LivingEntity livingEntity : this.level().getEntitiesOfClass(LivingEntity.class, aABB, WATER_SENSITIVE_OR_ON_FIRE)) {
            double d = this.distanceToSqr(livingEntity);
            if (d < 16.0) {
                if (livingEntity.isSensitiveToWater()) {
                    livingEntity.hurt(this.damageSources().indirectMagic(this, this.getOwner()), 1.0F);
                }

                if (livingEntity.isOnFire() && livingEntity.isAlive()) {
                    livingEntity.extinguishFire();
                }
            }
        }

        for (Axolotl axolotl : this.level().getEntitiesOfClass(Axolotl.class, aABB)) {
            axolotl.rehydrate();
        }
    }

    private void applySplash(Iterable<MobEffectInstance> effects, @Nullable Entity entity) {
        AABB aABB = this.getBoundingBox().inflate(4.0, 2.0, 4.0);
        List<LivingEntity> list = this.level().getEntitiesOfClass(LivingEntity.class, aABB);
        if (!list.isEmpty()) {
            Entity entity2 = this.getEffectSource();

            for (LivingEntity livingEntity : list) {
                if (livingEntity.isAffectedByPotions()) {
                    double d = this.distanceToSqr(livingEntity);
                    if (d < 16.0) {
                        double e;
                        if (livingEntity == entity) {
                            e = 1.0;
                        } else {
                            e = 1.0 - Math.sqrt(d) / 4.0;
                        }

                        for (MobEffectInstance mobEffectInstance : effects) {
                            Holder<MobEffect> holder = mobEffectInstance.getEffect();
                            if (holder.value().isInstantenous()) {
                                holder.value().applyInstantenousEffect(this, this.getOwner(), livingEntity, mobEffectInstance.getAmplifier(), e);
                            } else {
                                int i = mobEffectInstance.mapDuration(ix -> (int)(e * ix + 0.5));
                                MobEffectInstance mobEffectInstance2 = new MobEffectInstance(
                                    holder, i, mobEffectInstance.getAmplifier(), mobEffectInstance.isAmbient(), mobEffectInstance.isVisible()
                                );
                                if (!mobEffectInstance2.endsWithin(20)) {
                                    livingEntity.addEffect(mobEffectInstance2, entity2);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void makeAreaOfEffectCloud(PotionContents potionContents) {
        AreaEffectCloud areaEffectCloud = new AreaEffectCloud(this.level(), this.getX(), this.getY(), this.getZ());
        if (this.getOwner() instanceof LivingEntity livingEntity) {
            areaEffectCloud.setOwner(livingEntity);
        }

        areaEffectCloud.setRadius(3.0F);
        areaEffectCloud.setRadiusOnUse(-0.5F);
        areaEffectCloud.setWaitTime(10);
        areaEffectCloud.setRadiusPerTick(-areaEffectCloud.getRadius() / areaEffectCloud.getDuration());
        areaEffectCloud.setPotionContents(potionContents);
        this.level().addFreshEntity(areaEffectCloud);
    }

    private boolean isLingering() {
        return this.getItem().is(Items.LINGERING_POTION);
    }

    private void dowseFire(BlockPos pos) {
        BlockState blockState = this.level().getBlockState(pos);
        if (blockState.is(BlockTags.FIRE)) {
            this.level().destroyBlock(pos, false, this);
        } else if (AbstractCandleBlock.isLit(blockState)) {
            AbstractCandleBlock.extinguish(null, blockState, this.level(), pos);
        } else if (CampfireBlock.isLitCampfire(blockState)) {
            this.level().levelEvent(null, 1009, pos, 0);
            CampfireBlock.dowse(this.getOwner(), this.level(), pos, blockState);
            this.level().setBlockAndUpdate(pos, blockState.setValue(CampfireBlock.LIT, false));
        }
    }

    @Override
    public DoubleDoubleImmutablePair calculateHorizontalHurtKnockbackDirection(LivingEntity entity, DamageSource damageSource) {
        double d = entity.position().x - this.position().x;
        double e = entity.position().z - this.position().z;
        return DoubleDoubleImmutablePair.of(d, e);
    }
}
