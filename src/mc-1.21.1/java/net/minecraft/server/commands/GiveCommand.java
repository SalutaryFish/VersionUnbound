package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Collection;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

public class GiveCommand {
    public static final int MAX_ALLOWED_ITEMSTACKS = 100;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(
            Commands.literal("give")
                .requires(source -> source.hasPermission(2))
                .then(
                    Commands.argument("targets", EntityArgument.players())
                        .then(
                            Commands.argument("item", ItemArgument.item(context))
                                .executes(
                                    contextx -> giveItem(
                                        (CommandSourceStack)contextx.getSource(),
                                        ItemArgument.getItem(contextx, "item"),
                                        EntityArgument.getPlayers(contextx, "targets"),
                                        1
                                    )
                                )
                                .then(
                                    Commands.argument("count", IntegerArgumentType.integer(1))
                                        .executes(
                                            contextx -> giveItem(
                                                (CommandSourceStack)contextx.getSource(),
                                                ItemArgument.getItem(contextx, "item"),
                                                EntityArgument.getPlayers(contextx, "targets"),
                                                IntegerArgumentType.getInteger(contextx, "count")
                                            )
                                        )
                                )
                        )
                )
        );
    }

    private static int giveItem(CommandSourceStack source, ItemInput item, Collection<ServerPlayer> targets, int count) throws CommandSyntaxException {
        ItemStack itemStack = item.createItemStack(1, false);
        int i = itemStack.getMaxStackSize();
        int j = i * 100;
        if (count > j) {
            source.sendFailure(Component.translatable("commands.give.failed.toomanyitems", j, itemStack.getDisplayName()));
            return 0;
        }

        for (ServerPlayer serverPlayer : targets) {
            int k = count;

            while (k > 0) {
                int l = Math.min(i, k);
                k -= l;
                ItemStack itemStack2 = item.createItemStack(l, false);
                boolean bl = serverPlayer.getInventory().add(itemStack2);
                if (bl && itemStack2.isEmpty()) {
                    ItemEntity itemEntity = serverPlayer.drop(itemStack, false);
                    if (itemEntity != null) {
                        itemEntity.makeFakeItem();
                    }

                    serverPlayer.level()
                        .playSound(
                            null,
                            serverPlayer.getX(),
                            serverPlayer.getY(),
                            serverPlayer.getZ(),
                            SoundEvents.ITEM_PICKUP,
                            SoundSource.PLAYERS,
                            0.2F,
                            ((serverPlayer.getRandom().nextFloat() - serverPlayer.getRandom().nextFloat()) * 0.7F + 1.0F) * 2.0F
                        );
                    serverPlayer.containerMenu.broadcastChanges();
                } else {
                    ItemEntity itemEntity = serverPlayer.drop(itemStack2, false);
                    if (itemEntity != null) {
                        itemEntity.setNoPickUpDelay();
                        itemEntity.setTarget(serverPlayer.getUUID());
                    }
                }
            }
        }

        if (targets.size() == 1) {
            source.sendSuccess(
                () -> Component.translatable("commands.give.success.single", count, itemStack.getDisplayName(), targets.iterator().next().getDisplayName()),
                true
            );
        } else {
            source.sendSuccess(() -> Component.translatable("commands.give.success.single", count, itemStack.getDisplayName(), targets.size()), true);
        }

        return targets.size();
    }
}
