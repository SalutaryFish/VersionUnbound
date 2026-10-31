package net.minecraft.world.item;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;

public abstract class ProjectileWeaponItem extends Item {
    public static final Predicate<ItemStack> ARROW_ONLY = stack -> stack.is(ItemTags.ARROWS);
    public static final Predicate<ItemStack> ARROW_OR_FIREWORK = ARROW_ONLY.or(stack -> stack.is(Items.FIREWORK_ROCKET));

    public ProjectileWeaponItem(Item.Properties properties) {
        super(properties);
    }

    public Predicate<ItemStack> getSupportedHeldProjectiles() {
        return this.getAllSupportedProjectiles();
    }

    public abstract Predicate<ItemStack> getAllSupportedProjectiles();

    public static ItemStack getHeldProjectile(LivingEntity shooter, Predicate<ItemStack> isAmmo) {
        if (isAmmo.test(shooter.getItemInHand(InteractionHand.OFF_HAND))) {
            return shooter.getItemInHand(InteractionHand.OFF_HAND);
        } else {
            return isAmmo.test(shooter.getItemInHand(InteractionHand.MAIN_HAND)) ? shooter.getItemInHand(InteractionHand.MAIN_HAND) : ItemStack.EMPTY;
        }
    }

    @Override
    public int getEnchantmentValue() {
        return 1;
    }

    public abstract int getDefaultProjectileRange();

    protected void shoot(
        ServerLevel level,
        LivingEntity shooter,
        InteractionHand hand,
        ItemStack weapon,
        List<ItemStack> projectileItems,
        float velocity,
        float inaccuracy,
        boolean isCrit,
        @Nullable LivingEntity target
    ) {
        float f = EnchantmentHelper.processProjectileSpread(level, weapon, shooter, 0.0F);
        float g = projectileItems.size() == 1 ? 0.0F : 2.0F * f / (projectileItems.size() - 1);
        float h = (projectileItems.size() - 1) % 2 * g / 2.0F;
        float i = 1.0F;

        for (int j = 0; j < projectileItems.size(); j++) {
            ItemStack itemStack = projectileItems.get(j);
            if (!itemStack.isEmpty()) {
                float k = h + i * ((j + 1) / 2) * g;
                i = -i;
                Projectile projectile = this.createProjectile(level, shooter, weapon, itemStack, isCrit);
                this.shootProjectile(shooter, projectile, j, velocity, inaccuracy, k, target);
                level.addFreshEntity(projectile);
                weapon.hurtAndBreak(this.getDurabilityUse(itemStack), shooter, LivingEntity.getSlotForHand(hand));
                if (weapon.isEmpty()) {
                    break;
                }
            }
        }
    }

    protected int getDurabilityUse(ItemStack stack) {
        return 1;
    }

    protected abstract void shootProjectile(LivingEntity var1, Projectile var2, int var3, float var4, float var5, float var6, @Nullable LivingEntity var7);

    protected Projectile createProjectile(Level level, LivingEntity shooter, ItemStack weapon, ItemStack ammo, boolean isCrit) {
        ArrowItem arrowItem2 = ammo.getItem() instanceof ArrowItem arrowItem ? arrowItem : (ArrowItem)Items.ARROW;
        AbstractArrow abstractArrow = arrowItem2.createArrow(level, ammo, shooter, weapon);
        if (isCrit) {
            abstractArrow.setCritArrow(true);
        }

        return abstractArrow;
    }

    protected static List<ItemStack> draw(ItemStack weapon, ItemStack ammo, LivingEntity shooter) {
        if (ammo.isEmpty()) {
            return List.of();
        }

        int i = shooter.level() instanceof ServerLevel serverLevel ? EnchantmentHelper.processProjectileCount(serverLevel, weapon, shooter, 1) : 1;
        List<ItemStack> list = new ArrayList<>(i);
        ItemStack itemStack = ammo.copy();

        for (int j = 0; j < i; j++) {
            ItemStack itemStack2 = useAmmo(weapon, j == 0 ? ammo : itemStack, shooter, j > 0);
            if (!itemStack2.isEmpty()) {
                list.add(itemStack2);
            }
        }

        return list;
    }

    protected static ItemStack useAmmo(ItemStack weapon, ItemStack ammo, LivingEntity shooter, boolean intangable) {
        int i = !intangable && !shooter.hasInfiniteMaterials() && shooter.level() instanceof ServerLevel serverLevel
            ? EnchantmentHelper.processAmmoUse(serverLevel, weapon, ammo, 1)
            : 0;
        if (i > ammo.getCount()) {
            return ItemStack.EMPTY;
        }

        if (i == 0) {
            ItemStack itemStack = ammo.copyWithCount(1);
            itemStack.set(DataComponents.INTANGIBLE_PROJECTILE, Unit.INSTANCE);
            return itemStack;
        }

        ItemStack itemStack = ammo.split(i);
        if (ammo.isEmpty() && shooter instanceof Player player) {
            player.getInventory().removeItem(ammo);
        }

        return itemStack;
    }
}
