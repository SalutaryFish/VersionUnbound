package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Predicate;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.item.ItemPredicateArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public class ClearInventoryCommands {
    private static final DynamicCommandExceptionType ERROR_SINGLE = new DynamicCommandExceptionType(
        player -> Component.translatableEscape("clear.failed.single", player)
    );
    private static final DynamicCommandExceptionType ERROR_MULTIPLE = new DynamicCommandExceptionType(
        playerCount -> Component.translatableEscape("clear.failed.multiple", playerCount)
    );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(
            Commands.literal("clear")
                .requires(source -> source.hasPermission(2))
                .executes(
                    contextx -> clearUnlimited(
                        (CommandSourceStack)contextx.getSource(),
                        Collections.singleton(((CommandSourceStack)contextx.getSource()).getPlayerOrException()),
                        item -> true
                    )
                )
                .then(
                    Commands.argument("targets", EntityArgument.players())
                        .executes(
                            contextx -> clearUnlimited((CommandSourceStack)contextx.getSource(), EntityArgument.getPlayers(contextx, "targets"), item -> true)
                        )
                        .then(
                            Commands.argument("item", ItemPredicateArgument.itemPredicate(context))
                                .executes(
                                    contextx -> clearUnlimited(
                                        (CommandSourceStack)contextx.getSource(),
                                        EntityArgument.getPlayers(contextx, "targets"),
                                        ItemPredicateArgument.getItemPredicate(contextx, "item")
                                    )
                                )
                                .then(
                                    Commands.argument("maxCount", IntegerArgumentType.integer(0))
                                        .executes(
                                            contextx -> clearInventory(
                                                (CommandSourceStack)contextx.getSource(),
                                                EntityArgument.getPlayers(contextx, "targets"),
                                                ItemPredicateArgument.getItemPredicate(contextx, "item"),
                                                IntegerArgumentType.getInteger(contextx, "maxCount")
                                            )
                                        )
                                )
                        )
                )
        );
    }

    private static int clearUnlimited(CommandSourceStack source, Collection<ServerPlayer> targets, Predicate<ItemStack> filter) throws CommandSyntaxException {
        return clearInventory(source, targets, filter, -1);
    }

    private static int clearInventory(CommandSourceStack source, Collection<ServerPlayer> targetPlayers, Predicate<ItemStack> itemPredicate, int maxCount) throws CommandSyntaxException {
        int i = 0;

        for (ServerPlayer serverPlayer : targetPlayers) {
            i += serverPlayer.getInventory().clearOrCountMatchingItems(itemPredicate, maxCount, serverPlayer.inventoryMenu.getCraftSlots());
            serverPlayer.containerMenu.broadcastChanges();
            serverPlayer.inventoryMenu.slotsChanged(serverPlayer.getInventory());
        }

        if (i == 0) {
            if (targetPlayers.size() == 1) {
                throw ERROR_SINGLE.create(targetPlayers.iterator().next().getName());
            } else {
                throw ERROR_MULTIPLE.create(targetPlayers.size());
            }
        } else {
            int j = i;
            if (maxCount == 0) {
                if (targetPlayers.size() == 1) {
                    source.sendSuccess(() -> Component.translatable("commands.clear.test.single", j, targetPlayers.iterator().next().getDisplayName()), true);
                } else {
                    source.sendSuccess(() -> Component.translatable("commands.clear.test.multiple", j, targetPlayers.size()), true);
                }
            } else if (targetPlayers.size() == 1) {
                source.sendSuccess(() -> Component.translatable("commands.clear.success.single", j, targetPlayers.iterator().next().getDisplayName()), true);
            } else {
                source.sendSuccess(() -> Component.translatable("commands.clear.success.multiple", j, targetPlayers.size()), true);
            }

            return i;
        }
    }
}
