package net.minecraft.world.item;

import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.network.chat.Component;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class FireworkRocketItem extends Item implements ProjectileItem {
    public static final byte[] CRAFTABLE_DURATIONS = new byte[]{1, 2, 3};
    public static final double ROCKET_PLACEMENT_OFFSET = 0.15;

    public FireworkRocketItem(Item.Properties properties) {
        super(properties);
    }

    /**
     * Called when this item is used when targeting a Block
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!level.isClientSide) {
            ItemStack itemStack = context.getItemInHand();
            Vec3 vec3 = context.getClickLocation();
            Direction direction = context.getClickedFace();
            FireworkRocketEntity fireworkRocketEntity = new FireworkRocketEntity(
                level,
                context.getPlayer(),
                vec3.x + direction.getStepX() * 0.15,
                vec3.y + direction.getStepY() * 0.15,
                vec3.z + direction.getStepZ() * 0.15,
                itemStack
            );
            level.addFreshEntity(fireworkRocketEntity);
            itemStack.shrink(1);
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * Called to trigger the item's "innate" right click behavior. To handle when this item is used on a Block, see {@link #onItemUse}.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player.isFallFlying()) {
            ItemStack itemStack = player.getItemInHand(hand);
            if (!level.isClientSide) {
                FireworkRocketEntity fireworkRocketEntity = new FireworkRocketEntity(level, itemStack, player);
                level.addFreshEntity(fireworkRocketEntity);
                itemStack.consume(1, player);
                player.awardStat(Stats.ITEM_USED.get(this));
            }

            return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
        } else {
            return InteractionResultHolder.pass(player.getItemInHand(hand));
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        Fireworks fireworks = stack.get(DataComponents.FIREWORKS);
        if (fireworks != null) {
            fireworks.addToTooltip(context, tooltipComponents::add, tooltipFlag);
        }
    }

    @Override
    public Projectile asProjectile(Level level, Position pos, ItemStack stack, Direction direction) {
        return new FireworkRocketEntity(level, stack.copyWithCount(1), pos.x(), pos.y(), pos.z(), true);
    }

    @Override
    public ProjectileItem.DispenseConfig createDispenseConfig() {
        return ProjectileItem.DispenseConfig.builder()
            .positionFunction(FireworkRocketItem::getEntityPokingOutOfBlockPos)
            .uncertainty(1.0F)
            .power(0.5F)
            .overrideDispenseEvent(1004)
            .build();
    }

    private static Vec3 getEntityPokingOutOfBlockPos(BlockSource source, Direction direction) {
        return source.center()
            .add(
                direction.getStepX() * (0.5000099999997474 - EntityType.FIREWORK_ROCKET.getWidth() / 2.0),
                direction.getStepY() * (0.5000099999997474 - EntityType.FIREWORK_ROCKET.getHeight() / 2.0) - EntityType.FIREWORK_ROCKET.getHeight() / 2.0,
                direction.getStepZ() * (0.5000099999997474 - EntityType.FIREWORK_ROCKET.getWidth() / 2.0)
            );
    }
}
