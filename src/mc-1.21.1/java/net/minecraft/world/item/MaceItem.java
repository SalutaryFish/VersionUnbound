package net.minecraft.world.item;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class MaceItem extends Item {
    private static final int DEFAULT_ATTACK_DAMAGE = 5;
    private static final float DEFAULT_ATTACK_SPEED = -3.4F;
    public static final float SMASH_ATTACK_FALL_THRESHOLD = 1.5F;
    private static final float SMASH_ATTACK_HEAVY_THRESHOLD = 5.0F;
    public static final float SMASH_ATTACK_KNOCKBACK_RADIUS = 3.5F;
    private static final float SMASH_ATTACK_KNOCKBACK_POWER = 0.7F;

    public MaceItem(Item.Properties properties) {
        super(properties);
    }

    public static ItemAttributeModifiers createAttributes() {
        return ItemAttributeModifiers.builder()
            .add(
                Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_ID, 5.0, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND
            )
            .add(
                Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_ID, -3.4F, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND
            )
            .build();
    }

    public static Tool createToolProperties() {
        return new Tool(List.of(), 1.0F, 2);
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return !player.isCreative();
    }

    @Override
    public int getEnchantmentValue() {
        return 15;
    }

    /**
     * Current implementations of this method in child classes do not use the entry argument beside ev. They just raise the damage on the stack.
     */
    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (attacker instanceof ServerPlayer serverPlayer && canSmashAttack(serverPlayer)) {
            ServerLevel serverLevel = (ServerLevel)attacker.level();
            if (serverPlayer.isIgnoringFallDamageFromCurrentImpulse() && serverPlayer.currentImpulseImpactPos != null) {
                if (serverPlayer.currentImpulseImpactPos.y > serverPlayer.position().y) {
                    serverPlayer.currentImpulseImpactPos = serverPlayer.position();
                }
            } else {
                serverPlayer.currentImpulseImpactPos = serverPlayer.position();
            }

            serverPlayer.setIgnoreFallDamageFromCurrentImpulse(true);
            serverPlayer.setDeltaMovement(serverPlayer.getDeltaMovement().with(Direction.Axis.Y, 0.01F));
            serverPlayer.connection.send(new ClientboundSetEntityMotionPacket(serverPlayer));
            if (target.onGround()) {
                serverPlayer.setSpawnExtraParticlesOnFall(true);
                SoundEvent soundEvent = serverPlayer.fallDistance > 5.0F ? SoundEvents.MACE_SMASH_GROUND_HEAVY : SoundEvents.MACE_SMASH_GROUND;
                serverLevel.playSound(
                    null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(), soundEvent, serverPlayer.getSoundSource(), 1.0F, 1.0F
                );
            } else {
                serverLevel.playSound(
                    null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(), SoundEvents.MACE_SMASH_AIR, serverPlayer.getSoundSource(), 1.0F, 1.0F
                );
            }

            knockback(serverLevel, serverPlayer, target);
        }

        return true;
    }

    @Override
    public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        stack.hurtAndBreak(1, attacker, EquipmentSlot.MAINHAND);
        if (canSmashAttack(attacker)) {
            attacker.resetFallDistance();
        }
    }

    /**
     * Return whether this item is repairable in an anvil.
     */
    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repairCandidate) {
        return repairCandidate.is(Items.BREEZE_ROD);
    }

    @Override
    public float getAttackDamageBonus(Entity target, float damage, DamageSource damageSource) {
        if (damageSource.getDirectEntity() instanceof LivingEntity livingEntity) {
            if (!canSmashAttack(livingEntity)) {
                return 0.0F;
            }

            float f = 3.0F;
            float g = 8.0F;
            float h = livingEntity.fallDistance;
            float i;
            if (h <= 3.0F) {
                i = 4.0F * h;
            } else if (h <= 8.0F) {
                i = 12.0F + 2.0F * (h - 3.0F);
            } else {
                i = 22.0F + h - 8.0F;
            }

            return livingEntity.level() instanceof ServerLevel serverLevel
                ? i + EnchantmentHelper.modifyFallBasedDamage(serverLevel, livingEntity.getWeaponItem(), target, damageSource, 0.0F) * h
                : i;
        } else {
            return 0.0F;
        }
    }

    private static void knockback(Level level, Player player, Entity entity) {
        level.levelEvent(2013, entity.getOnPos(), 750);
        level.getEntitiesOfClass(LivingEntity.class, entity.getBoundingBox().inflate(3.5), knockbackPredicate(player, entity)).forEach(livingEntity -> {
            Vec3 vec3 = livingEntity.position().subtract(entity.position());
            double d = getKnockbackPower(player, livingEntity, vec3);
            Vec3 vec32 = vec3.normalize().scale(d);
            if (d > 0.0) {
                livingEntity.push(vec32.x, 0.7F, vec32.z);
                if (livingEntity instanceof ServerPlayer serverPlayer) {
                    serverPlayer.connection.send(new ClientboundSetEntityMotionPacket(serverPlayer));
                }
            }
        });
    }

    private static Predicate<LivingEntity> knockbackPredicate(Player player, Entity entity) {
        return livingEntity -> {
            boolean bl = !livingEntity.isSpectator();
            boolean bl2 = livingEntity != player && livingEntity != entity;
            boolean bl3 = !player.isAlliedTo(livingEntity);
            boolean bl4 = !(
                livingEntity instanceof TamableAnimal tamableAnimal && tamableAnimal.isTame() && player.getUUID().equals(tamableAnimal.getOwnerUUID())
            );
            boolean bl5 = !(livingEntity instanceof ArmorStand armorStand && armorStand.isMarker());
            boolean bl6 = entity.distanceToSqr(livingEntity) <= Math.pow(3.5, 2.0);
            return bl && bl2 && bl3 && bl4 && bl5 && bl6;
        };
    }

    private static double getKnockbackPower(Player player, LivingEntity entity, Vec3 entityPos) {
        return (3.5 - entityPos.length()) * 0.7F * (player.fallDistance > 5.0F ? 2 : 1) * (1.0 - entity.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
    }

    public static boolean canSmashAttack(LivingEntity entity) {
        return entity.fallDistance > 1.5F && !entity.isFallFlying();
    }
}
