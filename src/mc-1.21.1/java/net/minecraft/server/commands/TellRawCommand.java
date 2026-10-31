package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ComponentArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.server.level.ServerPlayer;

public class TellRawCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(
            Commands.literal("tellraw")
                .requires(source -> source.hasPermission(2))
                .then(
                    Commands.argument("targets", EntityArgument.players())
                        .then(
                            Commands.argument("message", ComponentArgument.textComponent(context))
                                .executes(
                                    contextx -> {
                                        int i = 0;

                                        for (ServerPlayer serverPlayer : EntityArgument.getPlayers(contextx, "targets")) {
                                            serverPlayer.sendSystemMessage(
                                                ComponentUtils.updateForEntity(
                                                    (CommandSourceStack)contextx.getSource(),
                                                    ComponentArgument.getComponent(contextx, "message"),
                                                    serverPlayer,
                                                    0
                                                ),
                                                false
                                            );
                                            i++;
                                        }

                                        return i;
                                    }
                                )
                        )
                )
        );
    }
}
