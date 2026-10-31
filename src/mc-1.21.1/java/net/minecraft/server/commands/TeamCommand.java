package net.minecraft.server.commands;

import com.google.common.collect.Lists;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Collection;
import java.util.Collections;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ColorArgument;
import net.minecraft.commands.arguments.ComponentArgument;
import net.minecraft.commands.arguments.ScoreHolderArgument;
import net.minecraft.commands.arguments.TeamArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;

public class TeamCommand {
    private static final SimpleCommandExceptionType ERROR_TEAM_ALREADY_EXISTS = new SimpleCommandExceptionType(
        Component.translatable("commands.team.add.duplicate")
    );
    private static final SimpleCommandExceptionType ERROR_TEAM_ALREADY_EMPTY = new SimpleCommandExceptionType(
        Component.translatable("commands.team.empty.unchanged")
    );
    private static final SimpleCommandExceptionType ERROR_TEAM_ALREADY_NAME = new SimpleCommandExceptionType(
        Component.translatable("commands.team.option.name.unchanged")
    );
    private static final SimpleCommandExceptionType ERROR_TEAM_ALREADY_COLOR = new SimpleCommandExceptionType(
        Component.translatable("commands.team.option.color.unchanged")
    );
    private static final SimpleCommandExceptionType ERROR_TEAM_ALREADY_FRIENDLYFIRE_ENABLED = new SimpleCommandExceptionType(
        Component.translatable("commands.team.option.friendlyfire.alreadyEnabled")
    );
    private static final SimpleCommandExceptionType ERROR_TEAM_ALREADY_FRIENDLYFIRE_DISABLED = new SimpleCommandExceptionType(
        Component.translatable("commands.team.option.friendlyfire.alreadyDisabled")
    );
    private static final SimpleCommandExceptionType ERROR_TEAM_ALREADY_FRIENDLYINVISIBLES_ENABLED = new SimpleCommandExceptionType(
        Component.translatable("commands.team.option.seeFriendlyInvisibles.alreadyEnabled")
    );
    private static final SimpleCommandExceptionType ERROR_TEAM_ALREADY_FRIENDLYINVISIBLES_DISABLED = new SimpleCommandExceptionType(
        Component.translatable("commands.team.option.seeFriendlyInvisibles.alreadyDisabled")
    );
    private static final SimpleCommandExceptionType ERROR_TEAM_NAMETAG_VISIBLITY_UNCHANGED = new SimpleCommandExceptionType(
        Component.translatable("commands.team.option.nametagVisibility.unchanged")
    );
    private static final SimpleCommandExceptionType ERROR_TEAM_DEATH_MESSAGE_VISIBLITY_UNCHANGED = new SimpleCommandExceptionType(
        Component.translatable("commands.team.option.deathMessageVisibility.unchanged")
    );
    private static final SimpleCommandExceptionType ERROR_TEAM_COLLISION_UNCHANGED = new SimpleCommandExceptionType(
        Component.translatable("commands.team.option.collisionRule.unchanged")
    );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(
            Commands.literal("team")
                .requires(source -> source.hasPermission(2))
                .then(
                    Commands.literal("list")
                        .executes(contextx -> listTeams((CommandSourceStack)contextx.getSource()))
                        .then(
                            Commands.argument("team", TeamArgument.team())
                                .executes(contextx -> listMembers((CommandSourceStack)contextx.getSource(), TeamArgument.getTeam(contextx, "team")))
                        )
                )
                .then(
                    Commands.literal("add")
                        .then(
                            Commands.argument("team", StringArgumentType.word())
                                .executes(contextx -> createTeam((CommandSourceStack)contextx.getSource(), StringArgumentType.getString(contextx, "team")))
                                .then(
                                    Commands.argument("displayName", ComponentArgument.textComponent(context))
                                        .executes(
                                            contextx -> createTeam(
                                                (CommandSourceStack)contextx.getSource(),
                                                StringArgumentType.getString(contextx, "team"),
                                                ComponentArgument.getComponent(contextx, "displayName")
                                            )
                                        )
                                )
                        )
                )
                .then(
                    Commands.literal("remove")
                        .then(
                            Commands.argument("team", TeamArgument.team())
                                .executes(contextx -> deleteTeam((CommandSourceStack)contextx.getSource(), TeamArgument.getTeam(contextx, "team")))
                        )
                )
                .then(
                    Commands.literal("empty")
                        .then(
                            Commands.argument("team", TeamArgument.team())
                                .executes(contextx -> emptyTeam((CommandSourceStack)contextx.getSource(), TeamArgument.getTeam(contextx, "team")))
                        )
                )
                .then(
                    Commands.literal("join")
                        .then(
                            Commands.argument("team", TeamArgument.team())
                                .executes(
                                    contextx -> joinTeam(
                                        (CommandSourceStack)contextx.getSource(),
                                        TeamArgument.getTeam(contextx, "team"),
                                        Collections.singleton(((CommandSourceStack)contextx.getSource()).getEntityOrException())
                                    )
                                )
                                .then(
                                    Commands.argument("members", ScoreHolderArgument.scoreHolders())
                                        .suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                                        .executes(
                                            contextx -> joinTeam(
                                                (CommandSourceStack)contextx.getSource(),
                                                TeamArgument.getTeam(contextx, "team"),
                                                ScoreHolderArgument.getNamesWithDefaultWildcard(contextx, "members")
                                            )
                                        )
                                )
                        )
                )
                .then(
                    Commands.literal("leave")
                        .then(
                            Commands.argument("members", ScoreHolderArgument.scoreHolders())
                                .suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                                .executes(
                                    contextx -> leaveTeam(
                                        (CommandSourceStack)contextx.getSource(), ScoreHolderArgument.getNamesWithDefaultWildcard(contextx, "members")
                                    )
                                )
                        )
                )
                .then(
                    Commands.literal("modify")
                        .then(
                            Commands.argument("team", TeamArgument.team())
                                .then(
                                    Commands.literal("displayName")
                                        .then(
                                            Commands.argument("displayName", ComponentArgument.textComponent(context))
                                                .executes(
                                                    contextx -> setDisplayName(
                                                        (CommandSourceStack)contextx.getSource(),
                                                        TeamArgument.getTeam(contextx, "team"),
                                                        ComponentArgument.getComponent(contextx, "displayName")
                                                    )
                                                )
                                        )
                                )
                                .then(
                                    Commands.literal("color")
                                        .then(
                                            Commands.argument("value", ColorArgument.color())
                                                .executes(
                                                    contextx -> setColor(
                                                        (CommandSourceStack)contextx.getSource(),
                                                        TeamArgument.getTeam(contextx, "team"),
                                                        ColorArgument.getColor(contextx, "value")
                                                    )
                                                )
                                        )
                                )
                                .then(
                                    Commands.literal("friendlyFire")
                                        .then(
                                            Commands.argument("allowed", BoolArgumentType.bool())
                                                .executes(
                                                    contextx -> setFriendlyFire(
                                                        (CommandSourceStack)contextx.getSource(),
                                                        TeamArgument.getTeam(contextx, "team"),
                                                        BoolArgumentType.getBool(contextx, "allowed")
                                                    )
                                                )
                                        )
                                )
                                .then(
                                    Commands.literal("seeFriendlyInvisibles")
                                        .then(
                                            Commands.argument("allowed", BoolArgumentType.bool())
                                                .executes(
                                                    contextx -> setFriendlySight(
                                                        (CommandSourceStack)contextx.getSource(),
                                                        TeamArgument.getTeam(contextx, "team"),
                                                        BoolArgumentType.getBool(contextx, "allowed")
                                                    )
                                                )
                                        )
                                )
                                .then(
                                    Commands.literal("nametagVisibility")
                                        .then(
                                            Commands.literal("never")
                                                .executes(
                                                    contextx -> setNametagVisibility(
                                                        (CommandSourceStack)contextx.getSource(), TeamArgument.getTeam(contextx, "team"), Team.Visibility.NEVER
                                                    )
                                                )
                                        )
                                        .then(
                                            Commands.literal("hideForOtherTeams")
                                                .executes(
                                                    contextx -> setNametagVisibility(
                                                        (CommandSourceStack)contextx.getSource(),
                                                        TeamArgument.getTeam(contextx, "team"),
                                                        Team.Visibility.HIDE_FOR_OTHER_TEAMS
                                                    )
                                                )
                                        )
                                        .then(
                                            Commands.literal("hideForOwnTeam")
                                                .executes(
                                                    contextx -> setNametagVisibility(
                                                        (CommandSourceStack)contextx.getSource(),
                                                        TeamArgument.getTeam(contextx, "team"),
                                                        Team.Visibility.HIDE_FOR_OWN_TEAM
                                                    )
                                                )
                                        )
                                        .then(
                                            Commands.literal("always")
                                                .executes(
                                                    contextx -> setNametagVisibility(
                                                        (CommandSourceStack)contextx.getSource(),
                                                        TeamArgument.getTeam(contextx, "team"),
                                                        Team.Visibility.ALWAYS
                                                    )
                                                )
                                        )
                                )
                                .then(
                                    Commands.literal("deathMessageVisibility")
                                        .then(
                                            Commands.literal("never")
                                                .executes(
                                                    contextx -> setDeathMessageVisibility(
                                                        (CommandSourceStack)contextx.getSource(), TeamArgument.getTeam(contextx, "team"), Team.Visibility.NEVER
                                                    )
                                                )
                                        )
                                        .then(
                                            Commands.literal("hideForOtherTeams")
                                                .executes(
                                                    contextx -> setDeathMessageVisibility(
                                                        (CommandSourceStack)contextx.getSource(),
                                                        TeamArgument.getTeam(contextx, "team"),
                                                        Team.Visibility.HIDE_FOR_OTHER_TEAMS
                                                    )
                                                )
                                        )
                                        .then(
                                            Commands.literal("hideForOwnTeam")
                                                .executes(
                                                    contextx -> setDeathMessageVisibility(
                                                        (CommandSourceStack)contextx.getSource(),
                                                        TeamArgument.getTeam(contextx, "team"),
                                                        Team.Visibility.HIDE_FOR_OWN_TEAM
                                                    )
                                                )
                                        )
                                        .then(
                                            Commands.literal("always")
                                                .executes(
                                                    contextx -> setDeathMessageVisibility(
                                                        (CommandSourceStack)contextx.getSource(),
                                                        TeamArgument.getTeam(contextx, "team"),
                                                        Team.Visibility.ALWAYS
                                                    )
                                                )
                                        )
                                )
                                .then(
                                    Commands.literal("collisionRule")
                                        .then(
                                            Commands.literal("never")
                                                .executes(
                                                    contextx -> setCollision(
                                                        (CommandSourceStack)contextx.getSource(),
                                                        TeamArgument.getTeam(contextx, "team"),
                                                        Team.CollisionRule.NEVER
                                                    )
                                                )
                                        )
                                        .then(
                                            Commands.literal("pushOwnTeam")
                                                .executes(
                                                    contextx -> setCollision(
                                                        (CommandSourceStack)contextx.getSource(),
                                                        TeamArgument.getTeam(contextx, "team"),
                                                        Team.CollisionRule.PUSH_OWN_TEAM
                                                    )
                                                )
                                        )
                                        .then(
                                            Commands.literal("pushOtherTeams")
                                                .executes(
                                                    contextx -> setCollision(
                                                        (CommandSourceStack)contextx.getSource(),
                                                        TeamArgument.getTeam(contextx, "team"),
                                                        Team.CollisionRule.PUSH_OTHER_TEAMS
                                                    )
                                                )
                                        )
                                        .then(
                                            Commands.literal("always")
                                                .executes(
                                                    contextx -> setCollision(
                                                        (CommandSourceStack)contextx.getSource(),
                                                        TeamArgument.getTeam(contextx, "team"),
                                                        Team.CollisionRule.ALWAYS
                                                    )
                                                )
                                        )
                                )
                                .then(
                                    Commands.literal("prefix")
                                        .then(
                                            Commands.argument("prefix", ComponentArgument.textComponent(context))
                                                .executes(
                                                    contextx -> setPrefix(
                                                        (CommandSourceStack)contextx.getSource(),
                                                        TeamArgument.getTeam(contextx, "team"),
                                                        ComponentArgument.getComponent(contextx, "prefix")
                                                    )
                                                )
                                        )
                                )
                                .then(
                                    Commands.literal("suffix")
                                        .then(
                                            Commands.argument("suffix", ComponentArgument.textComponent(context))
                                                .executes(
                                                    contextx -> setSuffix(
                                                        (CommandSourceStack)contextx.getSource(),
                                                        TeamArgument.getTeam(contextx, "team"),
                                                        ComponentArgument.getComponent(contextx, "suffix")
                                                    )
                                                )
                                        )
                                )
                        )
                )
        );
    }

    private static Component getFirstMemberName(Collection<ScoreHolder> scores) {
        return scores.iterator().next().getFeedbackDisplayName();
    }

    /**
     * Removes the listed players from their teams.
     */
    private static int leaveTeam(CommandSourceStack source, Collection<ScoreHolder> players) {
        Scoreboard scoreboard = source.getServer().getScoreboard();

        for (ScoreHolder scoreHolder : players) {
            scoreboard.removePlayerFromTeam(scoreHolder.getScoreboardName());
        }

        if (players.size() == 1) {
            source.sendSuccess(() -> Component.translatable("commands.team.leave.success.single", getFirstMemberName(players)), true);
        } else {
            source.sendSuccess(() -> Component.translatable("commands.team.leave.success.multiple", players.size()), true);
        }

        return players.size();
    }

    private static int joinTeam(CommandSourceStack source, PlayerTeam team, Collection<ScoreHolder> players) {
        Scoreboard scoreboard = source.getServer().getScoreboard();

        for (ScoreHolder scoreHolder : players) {
            scoreboard.addPlayerToTeam(scoreHolder.getScoreboardName(), team);
        }

        if (players.size() == 1) {
            source.sendSuccess(
                () -> Component.translatable("commands.team.join.success.single", getFirstMemberName(players), team.getFormattedDisplayName()), true
            );
        } else {
            source.sendSuccess(() -> Component.translatable("commands.team.join.success.multiple", players.size(), team.getFormattedDisplayName()), true);
        }

        return players.size();
    }

    private static int setNametagVisibility(CommandSourceStack source, PlayerTeam team, Team.Visibility visibility) throws CommandSyntaxException {
        if (team.getNameTagVisibility() == visibility) {
            throw ERROR_TEAM_NAMETAG_VISIBLITY_UNCHANGED.create();
        }

        team.setNameTagVisibility(visibility);
        source.sendSuccess(
            () -> Component.translatable("commands.team.option.nametagVisibility.success", team.getFormattedDisplayName(), visibility.getDisplayName()), true
        );
        return 0;
    }

    private static int setDeathMessageVisibility(CommandSourceStack source, PlayerTeam team, Team.Visibility visibility) throws CommandSyntaxException {
        if (team.getDeathMessageVisibility() == visibility) {
            throw ERROR_TEAM_DEATH_MESSAGE_VISIBLITY_UNCHANGED.create();
        }

        team.setDeathMessageVisibility(visibility);
        source.sendSuccess(
            () -> Component.translatable("commands.team.option.deathMessageVisibility.success", team.getFormattedDisplayName(), visibility.getDisplayName()),
            true
        );
        return 0;
    }

    private static int setCollision(CommandSourceStack source, PlayerTeam team, Team.CollisionRule rule) throws CommandSyntaxException {
        if (team.getCollisionRule() == rule) {
            throw ERROR_TEAM_COLLISION_UNCHANGED.create();
        }

        team.setCollisionRule(rule);
        source.sendSuccess(
            () -> Component.translatable("commands.team.option.collisionRule.success", team.getFormattedDisplayName(), rule.getDisplayName()), true
        );
        return 0;
    }

    private static int setFriendlySight(CommandSourceStack source, PlayerTeam team, boolean value) throws CommandSyntaxException {
        if (team.canSeeFriendlyInvisibles() == value) {
            if (value) {
                throw ERROR_TEAM_ALREADY_FRIENDLYINVISIBLES_ENABLED.create();
            } else {
                throw ERROR_TEAM_ALREADY_FRIENDLYINVISIBLES_DISABLED.create();
            }
        } else {
            team.setSeeFriendlyInvisibles(value);
            source.sendSuccess(
                () -> Component.translatable("commands.team.option.seeFriendlyInvisibles." + (value ? "enabled" : "disabled"), team.getFormattedDisplayName()),
                true
            );
            return 0;
        }
    }

    private static int setFriendlyFire(CommandSourceStack source, PlayerTeam team, boolean value) throws CommandSyntaxException {
        if (team.isAllowFriendlyFire() == value) {
            if (value) {
                throw ERROR_TEAM_ALREADY_FRIENDLYFIRE_ENABLED.create();
            } else {
                throw ERROR_TEAM_ALREADY_FRIENDLYFIRE_DISABLED.create();
            }
        } else {
            team.setAllowFriendlyFire(value);
            source.sendSuccess(
                () -> Component.translatable("commands.team.option.friendlyfire." + (value ? "enabled" : "disabled"), team.getFormattedDisplayName()), true
            );
            return 0;
        }
    }

    private static int setDisplayName(CommandSourceStack source, PlayerTeam team, Component value) throws CommandSyntaxException {
        if (team.getDisplayName().equals(value)) {
            throw ERROR_TEAM_ALREADY_NAME.create();
        }

        team.setDisplayName(value);
        source.sendSuccess(() -> Component.translatable("commands.team.option.name.success", team.getFormattedDisplayName()), true);
        return 0;
    }

    private static int setColor(CommandSourceStack source, PlayerTeam team, ChatFormatting value) throws CommandSyntaxException {
        if (team.getColor() == value) {
            throw ERROR_TEAM_ALREADY_COLOR.create();
        }

        team.setColor(value);
        source.sendSuccess(() -> Component.translatable("commands.team.option.color.success", team.getFormattedDisplayName(), value.getName()), true);
        return 0;
    }

    private static int emptyTeam(CommandSourceStack source, PlayerTeam team) throws CommandSyntaxException {
        Scoreboard scoreboard = source.getServer().getScoreboard();
        Collection<String> collection = Lists.newArrayList(team.getPlayers());
        if (collection.isEmpty()) {
            throw ERROR_TEAM_ALREADY_EMPTY.create();
        }

        for (String string : collection) {
            scoreboard.removePlayerFromTeam(string, team);
        }

        source.sendSuccess(() -> Component.translatable("commands.team.empty.success", collection.size(), team.getFormattedDisplayName()), true);
        return collection.size();
    }

    private static int deleteTeam(CommandSourceStack source, PlayerTeam team) {
        Scoreboard scoreboard = source.getServer().getScoreboard();
        scoreboard.removePlayerTeam(team);
        source.sendSuccess(() -> Component.translatable("commands.team.remove.success", team.getFormattedDisplayName()), true);
        return scoreboard.getPlayerTeams().size();
    }

    private static int createTeam(CommandSourceStack source, String name) throws CommandSyntaxException {
        return createTeam(source, name, Component.literal(name));
    }

    private static int createTeam(CommandSourceStack source, String name, Component displayName) throws CommandSyntaxException {
        Scoreboard scoreboard = source.getServer().getScoreboard();
        if (scoreboard.getPlayerTeam(name) != null) {
            throw ERROR_TEAM_ALREADY_EXISTS.create();
        }

        PlayerTeam playerTeam = scoreboard.addPlayerTeam(name);
        playerTeam.setDisplayName(displayName);
        source.sendSuccess(() -> Component.translatable("commands.team.add.success", playerTeam.getFormattedDisplayName()), true);
        return scoreboard.getPlayerTeams().size();
    }

    private static int listMembers(CommandSourceStack source, PlayerTeam team) {
        Collection<String> collection = team.getPlayers();
        if (collection.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.team.list.members.empty", team.getFormattedDisplayName()), false);
        } else {
            source.sendSuccess(
                () -> Component.translatable(
                    "commands.team.list.members.success", team.getFormattedDisplayName(), collection.size(), ComponentUtils.formatList(collection)
                ),
                false
            );
        }

        return collection.size();
    }

    private static int listTeams(CommandSourceStack source) {
        Collection<PlayerTeam> collection = source.getServer().getScoreboard().getPlayerTeams();
        if (collection.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.team.list.teams.empty"), false);
        } else {
            source.sendSuccess(
                () -> Component.translatable(
                    "commands.team.list.teams.success", collection.size(), ComponentUtils.formatList(collection, PlayerTeam::getFormattedDisplayName)
                ),
                false
            );
        }

        return collection.size();
    }

    private static int setPrefix(CommandSourceStack source, PlayerTeam team, Component prefix) {
        team.setPlayerPrefix(prefix);
        source.sendSuccess(() -> Component.translatable("commands.team.option.prefix.success", prefix), false);
        return 1;
    }

    private static int setSuffix(CommandSourceStack source, PlayerTeam team, Component suffix) {
        team.setPlayerSuffix(suffix);
        source.sendSuccess(() -> Component.translatable("commands.team.option.suffix.success", suffix), false);
        return 1;
    }
}
