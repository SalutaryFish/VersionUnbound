package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.AdvancementTree;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class AdvancementCommands {
    private static final DynamicCommandExceptionType ERROR_NO_ACTION_PERFORMED = new DynamicCommandExceptionType(object -> (Component)object);
    private static final Dynamic2CommandExceptionType ERROR_CRITERION_NOT_FOUND = new Dynamic2CommandExceptionType(
        (criteriaName, criterion) -> Component.translatableEscape("commands.advancement.criterionNotFound", criteriaName, criterion)
    );
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_ADVANCEMENTS = (context, suggestions) -> {
        Collection<AdvancementHolder> collection = context.getSource().getServer().getAdvancements().getAllAdvancements();
        return SharedSuggestionProvider.suggestResource(collection.stream().map(AdvancementHolder::id), suggestions);
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("advancement")
                .requires(stack -> stack.hasPermission(2))
                .then(
                    Commands.literal("grant")
                        .then(
                            Commands.argument("targets", EntityArgument.players())
                                .then(
                                    Commands.literal("only")
                                        .then(
                                            Commands.argument("advancement", ResourceLocationArgument.id())
                                                .suggests(SUGGEST_ADVANCEMENTS)
                                                .executes(
                                                    context -> perform(
                                                        context.getSource(),
                                                        EntityArgument.getPlayers(context, "targets"),
                                                        AdvancementCommands.Action.GRANT,
                                                        getAdvancements(
                                                            context,
                                                            ResourceLocationArgument.getAdvancement(context, "advancement"),
                                                            AdvancementCommands.Mode.ONLY
                                                        )
                                                    )
                                                )
                                                .then(
                                                    Commands.argument("criterion", StringArgumentType.greedyString())
                                                        .suggests(
                                                            (commandContext, suggestionsBuilder) -> SharedSuggestionProvider.suggest(
                                                                ResourceLocationArgument.getAdvancement(commandContext, "advancement")
                                                                    .value()
                                                                    .criteria()
                                                                    .keySet(),
                                                                suggestionsBuilder
                                                            )
                                                        )
                                                        .executes(
                                                            context -> performCriterion(
                                                                context.getSource(),
                                                                EntityArgument.getPlayers(context, "targets"),
                                                                AdvancementCommands.Action.GRANT,
                                                                ResourceLocationArgument.getAdvancement(context, "advancement"),
                                                                StringArgumentType.getString(context, "criterion")
                                                            )
                                                        )
                                                )
                                        )
                                )
                                .then(
                                    Commands.literal("from")
                                        .then(
                                            Commands.argument("advancement", ResourceLocationArgument.id())
                                                .suggests(SUGGEST_ADVANCEMENTS)
                                                .executes(
                                                    context -> perform(
                                                        context.getSource(),
                                                        EntityArgument.getPlayers(context, "targets"),
                                                        AdvancementCommands.Action.GRANT,
                                                        getAdvancements(
                                                            context,
                                                            ResourceLocationArgument.getAdvancement(context, "advancement"),
                                                            AdvancementCommands.Mode.FROM
                                                        )
                                                    )
                                                )
                                        )
                                )
                                .then(
                                    Commands.literal("until")
                                        .then(
                                            Commands.argument("advancement", ResourceLocationArgument.id())
                                                .suggests(SUGGEST_ADVANCEMENTS)
                                                .executes(
                                                    context -> perform(
                                                        context.getSource(),
                                                        EntityArgument.getPlayers(context, "targets"),
                                                        AdvancementCommands.Action.GRANT,
                                                        getAdvancements(
                                                            context,
                                                            ResourceLocationArgument.getAdvancement(context, "advancement"),
                                                            AdvancementCommands.Mode.UNTIL
                                                        )
                                                    )
                                                )
                                        )
                                )
                                .then(
                                    Commands.literal("through")
                                        .then(
                                            Commands.argument("advancement", ResourceLocationArgument.id())
                                                .suggests(SUGGEST_ADVANCEMENTS)
                                                .executes(
                                                    context -> perform(
                                                        context.getSource(),
                                                        EntityArgument.getPlayers(context, "targets"),
                                                        AdvancementCommands.Action.GRANT,
                                                        getAdvancements(
                                                            context,
                                                            ResourceLocationArgument.getAdvancement(context, "advancement"),
                                                            AdvancementCommands.Mode.THROUGH
                                                        )
                                                    )
                                                )
                                        )
                                )
                                .then(
                                    Commands.literal("everything")
                                        .executes(
                                            context -> perform(
                                                context.getSource(),
                                                EntityArgument.getPlayers(context, "targets"),
                                                AdvancementCommands.Action.GRANT,
                                                context.getSource().getServer().getAdvancements().getAllAdvancements()
                                            )
                                        )
                                )
                        )
                )
                .then(
                    Commands.literal("revoke")
                        .then(
                            Commands.argument("targets", EntityArgument.players())
                                .then(
                                    Commands.literal("only")
                                        .then(
                                            Commands.argument("advancement", ResourceLocationArgument.id())
                                                .suggests(SUGGEST_ADVANCEMENTS)
                                                .executes(
                                                    context -> perform(
                                                        context.getSource(),
                                                        EntityArgument.getPlayers(context, "targets"),
                                                        AdvancementCommands.Action.REVOKE,
                                                        getAdvancements(
                                                            context,
                                                            ResourceLocationArgument.getAdvancement(context, "advancement"),
                                                            AdvancementCommands.Mode.ONLY
                                                        )
                                                    )
                                                )
                                                .then(
                                                    Commands.argument("criterion", StringArgumentType.greedyString())
                                                        .suggests(
                                                            (context, builder) -> SharedSuggestionProvider.suggest(
                                                                ResourceLocationArgument.getAdvancement(context, "advancement").value().criteria().keySet(),
                                                                builder
                                                            )
                                                        )
                                                        .executes(
                                                            context -> performCriterion(
                                                                context.getSource(),
                                                                EntityArgument.getPlayers(context, "targets"),
                                                                AdvancementCommands.Action.REVOKE,
                                                                ResourceLocationArgument.getAdvancement(context, "advancement"),
                                                                StringArgumentType.getString(context, "criterion")
                                                            )
                                                        )
                                                )
                                        )
                                )
                                .then(
                                    Commands.literal("from")
                                        .then(
                                            Commands.argument("advancement", ResourceLocationArgument.id())
                                                .suggests(SUGGEST_ADVANCEMENTS)
                                                .executes(
                                                    context -> perform(
                                                        context.getSource(),
                                                        EntityArgument.getPlayers(context, "targets"),
                                                        AdvancementCommands.Action.REVOKE,
                                                        getAdvancements(
                                                            context,
                                                            ResourceLocationArgument.getAdvancement(context, "advancement"),
                                                            AdvancementCommands.Mode.FROM
                                                        )
                                                    )
                                                )
                                        )
                                )
                                .then(
                                    Commands.literal("until")
                                        .then(
                                            Commands.argument("advancement", ResourceLocationArgument.id())
                                                .suggests(SUGGEST_ADVANCEMENTS)
                                                .executes(
                                                    context -> perform(
                                                        context.getSource(),
                                                        EntityArgument.getPlayers(context, "targets"),
                                                        AdvancementCommands.Action.REVOKE,
                                                        getAdvancements(
                                                            context,
                                                            ResourceLocationArgument.getAdvancement(context, "advancement"),
                                                            AdvancementCommands.Mode.UNTIL
                                                        )
                                                    )
                                                )
                                        )
                                )
                                .then(
                                    Commands.literal("through")
                                        .then(
                                            Commands.argument("advancement", ResourceLocationArgument.id())
                                                .suggests(SUGGEST_ADVANCEMENTS)
                                                .executes(
                                                    context -> perform(
                                                        context.getSource(),
                                                        EntityArgument.getPlayers(context, "targets"),
                                                        AdvancementCommands.Action.REVOKE,
                                                        getAdvancements(
                                                            context,
                                                            ResourceLocationArgument.getAdvancement(context, "advancement"),
                                                            AdvancementCommands.Mode.THROUGH
                                                        )
                                                    )
                                                )
                                        )
                                )
                                .then(
                                    Commands.literal("everything")
                                        .executes(
                                            context -> perform(
                                                context.getSource(),
                                                EntityArgument.getPlayers(context, "targets"),
                                                AdvancementCommands.Action.REVOKE,
                                                context.getSource().getServer().getAdvancements().getAllAdvancements()
                                            )
                                        )
                                )
                        )
                )
        );
    }

    /**
     * Performs the given action on each advancement in the list, for each player.
     *
     * @return The number of affected advancements across all players.
     */
    private static int perform(
        CommandSourceStack source, Collection<ServerPlayer> targets, AdvancementCommands.Action action, Collection<AdvancementHolder> advancements
    ) throws CommandSyntaxException {
        int i = 0;

        for (ServerPlayer serverPlayer : targets) {
            i += action.perform(serverPlayer, advancements);
        }

        if (i == 0) {
            if (advancements.size() == 1) {
                if (targets.size() == 1) {
                    throw ERROR_NO_ACTION_PERFORMED.create(
                        Component.translatable(
                            action.getKey() + ".one.to.one.failure",
                            Advancement.name(advancements.iterator().next()),
                            targets.iterator().next().getDisplayName()
                        )
                    );
                } else {
                    throw ERROR_NO_ACTION_PERFORMED.create(
                        Component.translatable(action.getKey() + ".one.to.many.failure", Advancement.name(advancements.iterator().next()), targets.size())
                    );
                }
            } else if (targets.size() == 1) {
                throw ERROR_NO_ACTION_PERFORMED.create(
                    Component.translatable(action.getKey() + ".many.to.one.failure", advancements.size(), targets.iterator().next().getDisplayName())
                );
            } else {
                throw ERROR_NO_ACTION_PERFORMED.create(Component.translatable(action.getKey() + ".many.to.many.failure", advancements.size(), targets.size()));
            }
        } else {
            if (advancements.size() == 1) {
                if (targets.size() == 1) {
                    source.sendSuccess(
                        () -> Component.translatable(
                            action.getKey() + ".one.to.one.success",
                            Advancement.name(advancements.iterator().next()),
                            targets.iterator().next().getDisplayName()
                        ),
                        true
                    );
                } else {
                    source.sendSuccess(
                        () -> Component.translatable(action.getKey() + ".one.to.many.success", Advancement.name(advancements.iterator().next()), targets.size()),
                        true
                    );
                }
            } else if (targets.size() == 1) {
                source.sendSuccess(
                    () -> Component.translatable(action.getKey() + ".many.to.one.success", advancements.size(), targets.iterator().next().getDisplayName()),
                    true
                );
            } else {
                source.sendSuccess(() -> Component.translatable(action.getKey() + ".many.to.many.success", advancements.size(), targets.size()), true);
            }

            return i;
        }
    }

    private static int performCriterion(
        CommandSourceStack source, Collection<ServerPlayer> targets, AdvancementCommands.Action action, AdvancementHolder advancement, String criterionName
    ) throws CommandSyntaxException {
        int i = 0;
        Advancement advancement2 = advancement.value();
        if (!advancement2.criteria().containsKey(criterionName)) {
            throw ERROR_CRITERION_NOT_FOUND.create(Advancement.name(advancement), criterionName);
        }

        for (ServerPlayer serverPlayer : targets) {
            if (action.performCriterion(serverPlayer, advancement, criterionName)) {
                i++;
            }
        }

        if (i == 0) {
            if (targets.size() == 1) {
                throw ERROR_NO_ACTION_PERFORMED.create(
                    Component.translatable(
                        action.getKey() + ".criterion.to.one.failure", criterionName, Advancement.name(advancement), targets.iterator().next().getDisplayName()
                    )
                );
            } else {
                throw ERROR_NO_ACTION_PERFORMED.create(
                    Component.translatable(action.getKey() + ".criterion.to.many.failure", criterionName, Advancement.name(advancement), targets.size())
                );
            }
        } else {
            if (targets.size() == 1) {
                source.sendSuccess(
                    () -> Component.translatable(
                        action.getKey() + ".criterion.to.one.success", criterionName, Advancement.name(advancement), targets.iterator().next().getDisplayName()
                    ),
                    true
                );
            } else {
                source.sendSuccess(
                    () -> Component.translatable(action.getKey() + ".criterion.to.many.success", criterionName, Advancement.name(advancement), targets.size()),
                    true
                );
            }

            return i;
        }
    }

    private static List<AdvancementHolder> getAdvancements(
        CommandContext<CommandSourceStack> context, AdvancementHolder advancement, AdvancementCommands.Mode mode
    ) {
        AdvancementTree advancementTree = context.getSource().getServer().getAdvancements().tree();
        AdvancementNode advancementNode = advancementTree.get(advancement);
        if (advancementNode == null) {
            return List.of(advancement);
        }

        List<AdvancementHolder> list = new ArrayList<>();
        if (mode.parents) {
            for (AdvancementNode advancementNode2 = advancementNode.parent(); advancementNode2 != null; advancementNode2 = advancementNode2.parent()) {
                list.add(advancementNode2.holder());
            }
        }

        list.add(advancement);
        if (mode.children) {
            addChildren(advancementNode, list);
        }

        return list;
    }

    private static void addChildren(AdvancementNode node, List<AdvancementHolder> output) {
        for (AdvancementNode advancementNode : node.children()) {
            output.add(advancementNode.holder());
            addChildren(advancementNode, output);
        }
    }

    enum Action {
        GRANT("grant") {
            @Override
            protected boolean perform(ServerPlayer serverPlayer, AdvancementHolder advancementHolder) {
                AdvancementProgress advancementProgress = serverPlayer.getAdvancements().getOrStartProgress(advancementHolder);
                if (advancementProgress.isDone()) {
                    return false;
                }

                for (String string : advancementProgress.getRemainingCriteria()) {
                    serverPlayer.getAdvancements().award(advancementHolder, string);
                }

                return true;
            }

            @Override
            protected boolean performCriterion(ServerPlayer serverPlayer, AdvancementHolder advancementHolder, String string) {
                return serverPlayer.getAdvancements().award(advancementHolder, string);
            }
        },
        REVOKE("revoke") {
            @Override
            protected boolean perform(ServerPlayer serverPlayer, AdvancementHolder advancementHolder) {
                AdvancementProgress advancementProgress = serverPlayer.getAdvancements().getOrStartProgress(advancementHolder);
                if (!advancementProgress.hasProgress()) {
                    return false;
                }

                for (String string : advancementProgress.getCompletedCriteria()) {
                    serverPlayer.getAdvancements().revoke(advancementHolder, string);
                }

                return true;
            }

            @Override
            protected boolean performCriterion(ServerPlayer serverPlayer, AdvancementHolder advancementHolder, String string) {
                return serverPlayer.getAdvancements().revoke(advancementHolder, string);
            }
        };

        private final String key;

        Action(final String key) {
            this.key = "commands.advancement." + key;
        }

        /**
         * Applies this action to all the given advancements.
         *
         * @return The number of players affected.
         */
        public int perform(ServerPlayer player, Iterable<AdvancementHolder> advancements) {
            int i = 0;

            for (AdvancementHolder advancementHolder : advancements) {
                if (this.perform(player, advancementHolder)) {
                    i++;
                }
            }

            return i;
        }

        protected abstract boolean perform(ServerPlayer var1, AdvancementHolder var2);

        protected abstract boolean performCriterion(ServerPlayer var1, AdvancementHolder var2, String var3);

        protected String getKey() {
            return this.key;
        }
    }

    enum Mode {
        ONLY(false, false),
        THROUGH(true, true),
        FROM(false, true),
        UNTIL(true, false),
        EVERYTHING(true, true);

        final boolean parents;
        final boolean children;

        Mode(final boolean parents, final boolean children) {
            this.parents = parents;
            this.children = children;
        }
    }
}
