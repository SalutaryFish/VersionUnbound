package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.Collection;
import java.util.Collections;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ComponentArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.bossevents.CustomBossEvent;
import net.minecraft.server.bossevents.CustomBossEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.player.Player;

public class BossBarCommands {
    private static final DynamicCommandExceptionType ERROR_ALREADY_EXISTS = new DynamicCommandExceptionType(
        bossBarId -> Component.translatableEscape("commands.bossbar.create.failed", bossBarId)
    );
    private static final DynamicCommandExceptionType ERROR_DOESNT_EXIST = new DynamicCommandExceptionType(
        bossBarId -> Component.translatableEscape("commands.bossbar.unknown", bossBarId)
    );
    private static final SimpleCommandExceptionType ERROR_NO_PLAYER_CHANGE = new SimpleCommandExceptionType(
        Component.translatable("commands.bossbar.set.players.unchanged")
    );
    private static final SimpleCommandExceptionType ERROR_NO_NAME_CHANGE = new SimpleCommandExceptionType(
        Component.translatable("commands.bossbar.set.name.unchanged")
    );
    private static final SimpleCommandExceptionType ERROR_NO_COLOR_CHANGE = new SimpleCommandExceptionType(
        Component.translatable("commands.bossbar.set.color.unchanged")
    );
    private static final SimpleCommandExceptionType ERROR_NO_STYLE_CHANGE = new SimpleCommandExceptionType(
        Component.translatable("commands.bossbar.set.style.unchanged")
    );
    private static final SimpleCommandExceptionType ERROR_NO_VALUE_CHANGE = new SimpleCommandExceptionType(
        Component.translatable("commands.bossbar.set.value.unchanged")
    );
    private static final SimpleCommandExceptionType ERROR_NO_MAX_CHANGE = new SimpleCommandExceptionType(
        Component.translatable("commands.bossbar.set.max.unchanged")
    );
    private static final SimpleCommandExceptionType ERROR_ALREADY_HIDDEN = new SimpleCommandExceptionType(
        Component.translatable("commands.bossbar.set.visibility.unchanged.hidden")
    );
    private static final SimpleCommandExceptionType ERROR_ALREADY_VISIBLE = new SimpleCommandExceptionType(
        Component.translatable("commands.bossbar.set.visibility.unchanged.visible")
    );
    public static final SuggestionProvider<CommandSourceStack> SUGGEST_BOSS_BAR = (context, builder) -> SharedSuggestionProvider.suggestResource(
        context.getSource().getServer().getCustomBossEvents().getIds(), builder
    );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(
            Commands.literal("bossbar")
                .requires(source -> source.hasPermission(2))
                .then(
                    Commands.literal("add")
                        .then(
                            Commands.argument("id", ResourceLocationArgument.id())
                                .then(
                                    Commands.argument("name", ComponentArgument.textComponent(context))
                                        .executes(
                                            contextx -> createBar(
                                                (CommandSourceStack)contextx.getSource(),
                                                ResourceLocationArgument.getId(contextx, "id"),
                                                ComponentArgument.getComponent(contextx, "name")
                                            )
                                        )
                                )
                        )
                )
                .then(
                    Commands.literal("remove")
                        .then(
                            Commands.argument("id", ResourceLocationArgument.id())
                                .suggests(SUGGEST_BOSS_BAR)
                                .executes(contextx -> removeBar((CommandSourceStack)contextx.getSource(), getBossBar(contextx)))
                        )
                )
                .then(Commands.literal("list").executes(contextx -> listBars((CommandSourceStack)contextx.getSource())))
                .then(
                    Commands.literal("set")
                        .then(
                            Commands.argument("id", ResourceLocationArgument.id())
                                .suggests(SUGGEST_BOSS_BAR)
                                .then(
                                    Commands.literal("name")
                                        .then(
                                            Commands.argument("name", ComponentArgument.textComponent(context))
                                                .executes(
                                                    contextx -> setName(
                                                        (CommandSourceStack)contextx.getSource(),
                                                        getBossBar(contextx),
                                                        ComponentArgument.getComponent(contextx, "name")
                                                    )
                                                )
                                        )
                                )
                                .then(
                                    Commands.literal("color")
                                        .then(
                                            Commands.literal("pink")
                                                .executes(
                                                    contextx -> setColor(
                                                        (CommandSourceStack)contextx.getSource(), getBossBar(contextx), BossEvent.BossBarColor.PINK
                                                    )
                                                )
                                        )
                                        .then(
                                            Commands.literal("blue")
                                                .executes(
                                                    contextx -> setColor(
                                                        (CommandSourceStack)contextx.getSource(), getBossBar(contextx), BossEvent.BossBarColor.BLUE
                                                    )
                                                )
                                        )
                                        .then(
                                            Commands.literal("red")
                                                .executes(
                                                    contextx -> setColor(
                                                        (CommandSourceStack)contextx.getSource(), getBossBar(contextx), BossEvent.BossBarColor.RED
                                                    )
                                                )
                                        )
                                        .then(
                                            Commands.literal("green")
                                                .executes(
                                                    contextx -> setColor(
                                                        (CommandSourceStack)contextx.getSource(), getBossBar(contextx), BossEvent.BossBarColor.GREEN
                                                    )
                                                )
                                        )
                                        .then(
                                            Commands.literal("yellow")
                                                .executes(
                                                    contextx -> setColor(
                                                        (CommandSourceStack)contextx.getSource(), getBossBar(contextx), BossEvent.BossBarColor.YELLOW
                                                    )
                                                )
                                        )
                                        .then(
                                            Commands.literal("purple")
                                                .executes(
                                                    contextx -> setColor(
                                                        (CommandSourceStack)contextx.getSource(), getBossBar(contextx), BossEvent.BossBarColor.PURPLE
                                                    )
                                                )
                                        )
                                        .then(
                                            Commands.literal("white")
                                                .executes(
                                                    contextx -> setColor(
                                                        (CommandSourceStack)contextx.getSource(), getBossBar(contextx), BossEvent.BossBarColor.WHITE
                                                    )
                                                )
                                        )
                                )
                                .then(
                                    Commands.literal("style")
                                        .then(
                                            Commands.literal("progress")
                                                .executes(
                                                    contextx -> setStyle(
                                                        (CommandSourceStack)contextx.getSource(), getBossBar(contextx), BossEvent.BossBarOverlay.PROGRESS
                                                    )
                                                )
                                        )
                                        .then(
                                            Commands.literal("notched_6")
                                                .executes(
                                                    contextx -> setStyle(
                                                        (CommandSourceStack)contextx.getSource(), getBossBar(contextx), BossEvent.BossBarOverlay.NOTCHED_6
                                                    )
                                                )
                                        )
                                        .then(
                                            Commands.literal("notched_10")
                                                .executes(
                                                    contextx -> setStyle(
                                                        (CommandSourceStack)contextx.getSource(), getBossBar(contextx), BossEvent.BossBarOverlay.NOTCHED_10
                                                    )
                                                )
                                        )
                                        .then(
                                            Commands.literal("notched_12")
                                                .executes(
                                                    contextx -> setStyle(
                                                        (CommandSourceStack)contextx.getSource(), getBossBar(contextx), BossEvent.BossBarOverlay.NOTCHED_12
                                                    )
                                                )
                                        )
                                        .then(
                                            Commands.literal("notched_20")
                                                .executes(
                                                    contextx -> setStyle(
                                                        (CommandSourceStack)contextx.getSource(), getBossBar(contextx), BossEvent.BossBarOverlay.NOTCHED_20
                                                    )
                                                )
                                        )
                                )
                                .then(
                                    Commands.literal("value")
                                        .then(
                                            Commands.argument("value", IntegerArgumentType.integer(0))
                                                .executes(
                                                    contextx -> setValue(
                                                        (CommandSourceStack)contextx.getSource(),
                                                        getBossBar(contextx),
                                                        IntegerArgumentType.getInteger(contextx, "value")
                                                    )
                                                )
                                        )
                                )
                                .then(
                                    Commands.literal("max")
                                        .then(
                                            Commands.argument("max", IntegerArgumentType.integer(1))
                                                .executes(
                                                    contextx -> setMax(
                                                        (CommandSourceStack)contextx.getSource(),
                                                        getBossBar(contextx),
                                                        IntegerArgumentType.getInteger(contextx, "max")
                                                    )
                                                )
                                        )
                                )
                                .then(
                                    Commands.literal("visible")
                                        .then(
                                            Commands.argument("visible", BoolArgumentType.bool())
                                                .executes(
                                                    contextx -> setVisible(
                                                        (CommandSourceStack)contextx.getSource(),
                                                        getBossBar(contextx),
                                                        BoolArgumentType.getBool(contextx, "visible")
                                                    )
                                                )
                                        )
                                )
                                .then(
                                    Commands.literal("players")
                                        .executes(
                                            contextx -> setPlayers((CommandSourceStack)contextx.getSource(), getBossBar(contextx), Collections.emptyList())
                                        )
                                        .then(
                                            Commands.argument("targets", EntityArgument.players())
                                                .executes(
                                                    contextx -> setPlayers(
                                                        (CommandSourceStack)contextx.getSource(),
                                                        getBossBar(contextx),
                                                        EntityArgument.getOptionalPlayers(contextx, "targets")
                                                    )
                                                )
                                        )
                                )
                        )
                )
                .then(
                    Commands.literal("get")
                        .then(
                            Commands.argument("id", ResourceLocationArgument.id())
                                .suggests(SUGGEST_BOSS_BAR)
                                .then(Commands.literal("value").executes(contextx -> getValue((CommandSourceStack)contextx.getSource(), getBossBar(contextx))))
                                .then(Commands.literal("max").executes(contextx -> getMax((CommandSourceStack)contextx.getSource(), getBossBar(contextx))))
                                .then(
                                    Commands.literal("visible")
                                        .executes(contextx -> getVisible((CommandSourceStack)contextx.getSource(), getBossBar(contextx)))
                                )
                                .then(
                                    Commands.literal("players")
                                        .executes(contextx -> getPlayers((CommandSourceStack)contextx.getSource(), getBossBar(contextx)))
                                )
                        )
                )
        );
    }

    private static int getValue(CommandSourceStack source, CustomBossEvent bossbar) {
        source.sendSuccess(() -> Component.translatable("commands.bossbar.get.value", bossbar.getDisplayName(), bossbar.getValue()), true);
        return bossbar.getValue();
    }

    private static int getMax(CommandSourceStack source, CustomBossEvent bossbar) {
        source.sendSuccess(() -> Component.translatable("commands.bossbar.get.max", bossbar.getDisplayName(), bossbar.getMax()), true);
        return bossbar.getMax();
    }

    private static int getVisible(CommandSourceStack source, CustomBossEvent bossbar) {
        if (bossbar.isVisible()) {
            source.sendSuccess(() -> Component.translatable("commands.bossbar.get.visible.visible", bossbar.getDisplayName()), true);
            return 1;
        } else {
            source.sendSuccess(() -> Component.translatable("commands.bossbar.get.visible.hidden", bossbar.getDisplayName()), true);
            return 0;
        }
    }

    private static int getPlayers(CommandSourceStack source, CustomBossEvent bossbar) {
        if (bossbar.getPlayers().isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.bossbar.get.players.none", bossbar.getDisplayName()), true);
        } else {
            source.sendSuccess(
                () -> Component.translatable(
                    "commands.bossbar.get.players.some",
                    bossbar.getDisplayName(),
                    bossbar.getPlayers().size(),
                    ComponentUtils.formatList(bossbar.getPlayers(), Player::getDisplayName)
                ),
                true
            );
        }

        return bossbar.getPlayers().size();
    }

    private static int setVisible(CommandSourceStack source, CustomBossEvent bossbar, boolean visible) throws CommandSyntaxException {
        if (bossbar.isVisible() == visible) {
            if (visible) {
                throw ERROR_ALREADY_VISIBLE.create();
            } else {
                throw ERROR_ALREADY_HIDDEN.create();
            }
        } else {
            bossbar.setVisible(visible);
            if (visible) {
                source.sendSuccess(() -> Component.translatable("commands.bossbar.set.visible.success.visible", bossbar.getDisplayName()), true);
            } else {
                source.sendSuccess(() -> Component.translatable("commands.bossbar.set.visible.success.hidden", bossbar.getDisplayName()), true);
            }

            return 0;
        }
    }

    private static int setValue(CommandSourceStack source, CustomBossEvent bossbar, int value) throws CommandSyntaxException {
        if (bossbar.getValue() == value) {
            throw ERROR_NO_VALUE_CHANGE.create();
        }

        bossbar.setValue(value);
        source.sendSuccess(() -> Component.translatable("commands.bossbar.set.value.success", bossbar.getDisplayName(), value), true);
        return value;
    }

    private static int setMax(CommandSourceStack source, CustomBossEvent bossbar, int max) throws CommandSyntaxException {
        if (bossbar.getMax() == max) {
            throw ERROR_NO_MAX_CHANGE.create();
        }

        bossbar.setMax(max);
        source.sendSuccess(() -> Component.translatable("commands.bossbar.set.max.success", bossbar.getDisplayName(), max), true);
        return max;
    }

    private static int setColor(CommandSourceStack source, CustomBossEvent bossbar, BossEvent.BossBarColor color) throws CommandSyntaxException {
        if (bossbar.getColor().equals(color)) {
            throw ERROR_NO_COLOR_CHANGE.create();
        }

        bossbar.setColor(color);
        source.sendSuccess(() -> Component.translatable("commands.bossbar.set.color.success", bossbar.getDisplayName()), true);
        return 0;
    }

    private static int setStyle(CommandSourceStack source, CustomBossEvent bossbar, BossEvent.BossBarOverlay style) throws CommandSyntaxException {
        if (bossbar.getOverlay().equals(style)) {
            throw ERROR_NO_STYLE_CHANGE.create();
        }

        bossbar.setOverlay(style);
        source.sendSuccess(() -> Component.translatable("commands.bossbar.set.style.success", bossbar.getDisplayName()), true);
        return 0;
    }

    private static int setName(CommandSourceStack source, CustomBossEvent bossbar, Component name) throws CommandSyntaxException {
        Component component = ComponentUtils.updateForEntity(source, name, null, 0);
        if (bossbar.getName().equals(component)) {
            throw ERROR_NO_NAME_CHANGE.create();
        }

        bossbar.setName(component);
        source.sendSuccess(() -> Component.translatable("commands.bossbar.set.name.success", bossbar.getDisplayName()), true);
        return 0;
    }

    private static int setPlayers(CommandSourceStack source, CustomBossEvent bossbar, Collection<ServerPlayer> players) throws CommandSyntaxException {
        boolean bl = bossbar.setPlayers(players);
        if (!bl) {
            throw ERROR_NO_PLAYER_CHANGE.create();
        }

        if (bossbar.getPlayers().isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.bossbar.set.players.success.none", bossbar.getDisplayName()), true);
        } else {
            source.sendSuccess(
                () -> Component.translatable(
                    "commands.bossbar.set.players.success.some",
                    bossbar.getDisplayName(),
                    players.size(),
                    ComponentUtils.formatList(players, Player::getDisplayName)
                ),
                true
            );
        }

        return bossbar.getPlayers().size();
    }

    private static int listBars(CommandSourceStack source) {
        Collection<CustomBossEvent> collection = source.getServer().getCustomBossEvents().getEvents();
        if (collection.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.bossbar.list.bars.none"), false);
        } else {
            source.sendSuccess(
                () -> Component.translatable(
                    "commands.bossbar.list.bars.some", collection.size(), ComponentUtils.formatList(collection, CustomBossEvent::getDisplayName)
                ),
                false
            );
        }

        return collection.size();
    }

    private static int createBar(CommandSourceStack source, ResourceLocation id, Component displayName) throws CommandSyntaxException {
        CustomBossEvents customBossEvents = source.getServer().getCustomBossEvents();
        if (customBossEvents.get(id) != null) {
            throw ERROR_ALREADY_EXISTS.create(id.toString());
        }

        CustomBossEvent customBossEvent = customBossEvents.create(id, ComponentUtils.updateForEntity(source, displayName, null, 0));
        source.sendSuccess(() -> Component.translatable("commands.bossbar.create.success", customBossEvent.getDisplayName()), true);
        return customBossEvents.getEvents().size();
    }

    private static int removeBar(CommandSourceStack source, CustomBossEvent bossbar) {
        CustomBossEvents customBossEvents = source.getServer().getCustomBossEvents();
        bossbar.removeAllPlayers();
        customBossEvents.remove(bossbar);
        source.sendSuccess(() -> Component.translatable("commands.bossbar.remove.success", bossbar.getDisplayName()), true);
        return customBossEvents.getEvents().size();
    }

    public static CustomBossEvent getBossBar(CommandContext<CommandSourceStack> source) throws CommandSyntaxException {
        ResourceLocation resourceLocation = ResourceLocationArgument.getId(source, "id");
        CustomBossEvent customBossEvent = source.getSource().getServer().getCustomBossEvents().get(resourceLocation);
        if (customBossEvent == null) {
            throw ERROR_DOESNT_EXIST.create(resourceLocation.toString());
        } else {
            return customBossEvent;
        }
    }
}
