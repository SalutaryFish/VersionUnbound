package net.minecraft.world.item;

import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.GlowItemFrame;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;

public class HangingEntityItem extends Item {
    private static final Component TOOLTIP_RANDOM_VARIANT = Component.translatable("painting.random").withStyle(ChatFormatting.GRAY);
    private final EntityType<? extends HangingEntity> type;

    public HangingEntityItem(EntityType<? extends HangingEntity> type, Item.Properties properties) {
        super(properties);
        this.type = type;
    }

    /**
     * Called when this item is used when targeting a Block
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        BlockPos blockPos = context.getClickedPos();
        Direction direction = context.getClickedFace();
        BlockPos blockPos2 = blockPos.relative(direction);
        Player player = context.getPlayer();
        ItemStack itemStack = context.getItemInHand();
        if (player != null && !this.mayPlace(player, direction, itemStack, blockPos2)) {
            return InteractionResult.FAIL;
        }

        Level level = context.getLevel();
        HangingEntity hangingEntity;
        if (this.type == EntityType.PAINTING) {
            Optional<Painting> optional = Painting.create(level, blockPos2, direction);
            if (optional.isEmpty()) {
                return InteractionResult.CONSUME;
            }

            hangingEntity = optional.get();
        } else if (this.type == EntityType.ITEM_FRAME) {
            hangingEntity = new ItemFrame(level, blockPos2, direction);
        } else {
            if (this.type != EntityType.GLOW_ITEM_FRAME) {
                return InteractionResult.sidedSuccess(level.isClientSide);
            }

            hangingEntity = new GlowItemFrame(level, blockPos2, direction);
        }

        CustomData customData = itemStack.getOrDefault(DataComponents.ENTITY_DATA, CustomData.EMPTY);
        if (!customData.isEmpty()) {
            EntityType.updateCustomEntityTag(level, player, hangingEntity, customData);
        }

        if (hangingEntity.survives()) {
            if (!level.isClientSide) {
                hangingEntity.playPlacementSound();
                level.gameEvent(player, GameEvent.ENTITY_PLACE, hangingEntity.position());
                level.addFreshEntity(hangingEntity);
            }

            itemStack.shrink(1);
            return InteractionResult.sidedSuccess(level.isClientSide);
        } else {
            return InteractionResult.CONSUME;
        }
    }

    protected boolean mayPlace(Player player, Direction direction, ItemStack hangingEntityStack, BlockPos pos) {
        return !direction.getAxis().isVertical() && player.mayUseItemAt(pos, direction, hangingEntityStack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
        HolderLookup.Provider provider = context.registries();
        if (provider != null && this.type == EntityType.PAINTING) {
            CustomData customData = stack.getOrDefault(DataComponents.ENTITY_DATA, CustomData.EMPTY);
            if (!customData.isEmpty()) {
                customData.read(provider.createSerializationContext(NbtOps.INSTANCE), Painting.VARIANT_MAP_CODEC)
                    .result()
                    .ifPresentOrElse(
                        holder -> {
                            holder.unwrapKey()
                                .ifPresent(
                                    resourceKey -> {
                                        tooltipComponents.add(
                                            Component.translatable(resourceKey.location().toLanguageKey("painting", "title")).withStyle(ChatFormatting.YELLOW)
                                        );
                                        tooltipComponents.add(
                                            Component.translatable(resourceKey.location().toLanguageKey("painting", "author")).withStyle(ChatFormatting.GRAY)
                                        );
                                    }
                                );
                            tooltipComponents.add(Component.translatable("painting.dimensions", holder.value().width(), holder.value().height()));
                        },
                        () -> tooltipComponents.add(TOOLTIP_RANDOM_VARIANT)
                    );
            } else if (tooltipFlag.isCreative()) {
                tooltipComponents.add(TOOLTIP_RANDOM_VARIANT);
            }
        }
    }
}
