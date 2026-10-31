package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Collection;
import java.util.function.Function;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ComponentArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.TimeArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundClearTitlesPacket;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;

public class TitleCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(
            Commands.literal("title")
                .requires(source -> source.hasPermission(2))
                .then(
                    Commands.argument("targets", EntityArgument.players())
                        .then(
                            Commands.literal("clear")
                                .executes(contextx -> clearTitle((CommandSourceStack)contextx.getSource(), EntityArgument.getPlayers(contextx, "targets")))
                        )
                        .then(
                            Commands.literal("reset")
                                .executes(contextx -> resetTitle((CommandSourceStack)contextx.getSource(), EntityArgument.getPlayers(contextx, "targets")))
                        )
                        .then(
                            Commands.literal("title")
                                .then(
                                    Commands.argument("title", ComponentArgument.textComponent(context))
                                        .executes(
                                            contextx -> showTitle(
                                                (CommandSourceStack)contextx.getSource(),
                                                EntityArgument.getPlayers(contextx, "targets"),
                                                ComponentArgument.getComponent(contextx, "title"),
                                                "title",
                                                ClientboundSetTitleTextPacket::new
                                            )
                                        )
                                )
                        )
                        .then(
                            Commands.literal("subtitle")
                                .then(
                                    Commands.argument("title", ComponentArgument.textComponent(context))
                                        .executes(
                                            contextx -> showTitle(
                                                (CommandSourceStack)contextx.getSource(),
                                                EntityArgument.getPlayers(contextx, "targets"),
                                                ComponentArgument.getComponent(contextx, "title"),
                                                "subtitle",
                                                ClientboundSetSubtitleTextPacket::new
                                            )
                                        )
                                )
                        )
                        .then(
                            Commands.literal("actionbar")
                                .then(
                                    Commands.argument("title", ComponentArgument.textComponent(context))
                                        .executes(
                                            contextx -> showTitle(
                                                (CommandSourceStack)contextx.getSource(),
                                                EntityArgument.getPlayers(contextx, "targets"),
                                                ComponentArgument.getComponent(contextx, "title"),
                                                "actionbar",
                                                ClientboundSetActionBarTextPacket::new
                                            )
                                        )
                                )
                        )
                        .then(
                            Commands.literal("times")
                                .then(
                                    Commands.argument("fadeIn", TimeArgument.time())
                                        .then(
                                            Commands.argument("stay", TimeArgument.time())
                                                .then(
                                                    Commands.argument("fadeOut", TimeArgument.time())
                                                        .executes(
                                                            contextx -> setTimes(
                                                                (CommandSourceStack)contextx.getSource(),
                                                                EntityArgument.getPlayers(contextx, "targets"),
                                                                IntegerArgumentType.getInteger(contextx, "fadeIn"),
                                                                IntegerArgumentType.getInteger(contextx, "stay"),
                                                                IntegerArgumentType.getInteger(contextx, "fadeOut")
                                                            )
                                                        )
                                                )
                                        )
                                )
                        )
                )
        );
    }

    private static int clearTitle(CommandSourceStack source, Collection<ServerPlayer> targets) {
        ClientboundClearTitlesPacket clientboundClearTitlesPacket = new ClientboundClearTitlesPacket(false);

        for (ServerPlayer serverPlayer : targets) {
            serverPlayer.connection.send(clientboundClearTitlesPacket);
        }

        if (targets.size() == 1) {
            source.sendSuccess(() -> Component.translatable("commands.title.cleared.single", targets.iterator().next().getDisplayName()), true);
        } else {
            source.sendSuccess(() -> Component.translatable("commands.title.cleared.multiple", targets.size()), true);
        }

        return targets.size();
    }

    private static int resetTitle(CommandSourceStack source, Collection<ServerPlayer> targets) {
        ClientboundClearTitlesPacket clientboundClearTitlesPacket = new ClientboundClearTitlesPacket(true);

        for (ServerPlayer serverPlayer : targets) {
            serverPlayer.connection.send(clientboundClearTitlesPacket);
        }

        if (targets.size() == 1) {
            source.sendSuccess(() -> Component.translatable("commands.title.reset.single", targets.iterator().next().getDisplayName()), true);
        } else {
            source.sendSuccess(() -> Component.translatable("commands.title.reset.multiple", targets.size()), true);
        }

        return targets.size();
    }

    private static int showTitle(
        CommandSourceStack source, Collection<ServerPlayer> targets, Component title, String titleType, Function<Component, Packet<?>> packetGetter
    ) throws CommandSyntaxException {
        for (ServerPlayer serverPlayer : targets) {
            serverPlayer.connection.send(packetGetter.apply(ComponentUtils.updateForEntity(source, title, serverPlayer, 0)));
        }

        if (targets.size() == 1) {
            source.sendSuccess(() -> Component.translatable("commands.title.show." + titleType + ".single", targets.iterator().next().getDisplayName()), true);
        } else {
            source.sendSuccess(() -> Component.translatable("commands.title.show." + titleType + ".multiple", targets.size()), true);
        }

        return targets.size();
    }

    private static int setTimes(CommandSourceStack source, Collection<ServerPlayer> target, int fade, int stay, int fadeOut) {
        ClientboundSetTitlesAnimationPacket clientboundSetTitlesAnimationPacket = new ClientboundSetTitlesAnimationPacket(fade, stay, fadeOut);

        for (ServerPlayer serverPlayer : target) {
            serverPlayer.connection.send(clientboundSetTitlesAnimationPacket);
        }

        if (target.size() == 1) {
            source.sendSuccess(() -> Component.translatable("commands.title.times.single", target.iterator().next().getDisplayName()), true);
        } else {
            source.sendSuccess(() -> Component.translatable("commands.title.times.multiple", target.size()), true);
        }

        return target.size();
    }
}
