package net.minecraft.world.item;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.stats.Stats;
import net.minecraft.util.StringUtil;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.Level;

public class WrittenBookItem extends Item {
    public WrittenBookItem(Item.Properties properties) {
        super(properties);
    }

    /**
     * Gets the title name of the book
     */
    @Override
    public Component getName(ItemStack stack) {
        WrittenBookContent writtenBookContent = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
        if (writtenBookContent != null) {
            String string = writtenBookContent.title().raw();
            if (!StringUtil.isBlank(string)) {
                return Component.literal(string);
            }
        }

        return super.getName(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        WrittenBookContent writtenBookContent = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
        if (writtenBookContent != null) {
            if (!StringUtil.isBlank(writtenBookContent.author())) {
                tooltipComponents.add(Component.translatable("book.byAuthor", writtenBookContent.author()).withStyle(ChatFormatting.GRAY));
            }

            tooltipComponents.add(Component.translatable("book.generation." + writtenBookContent.generation()).withStyle(ChatFormatting.GRAY));
        }
    }

    /**
     * Called to trigger the item's "innate" right click behavior. To handle when this item is used on a Block, see {@link #onItemUse}.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemStack = player.getItemInHand(hand);
        player.openItemGui(itemStack, hand);
        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResultHolder.sidedSuccess(itemStack, level.isClientSide());
    }

    public static boolean resolveBookComponents(ItemStack bookStack, CommandSourceStack resolvingSource, @Nullable Player resolvingPlayer) {
        WrittenBookContent writtenBookContent = bookStack.get(DataComponents.WRITTEN_BOOK_CONTENT);
        if (writtenBookContent != null && !writtenBookContent.resolved()) {
            WrittenBookContent writtenBookContent2 = writtenBookContent.resolve(resolvingSource, resolvingPlayer);
            if (writtenBookContent2 != null) {
                bookStack.set(DataComponents.WRITTEN_BOOK_CONTENT, writtenBookContent2);
                return true;
            }

            bookStack.set(DataComponents.WRITTEN_BOOK_CONTENT, writtenBookContent.markResolved());
        }

        return false;
    }
}
