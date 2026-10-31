package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameRules;

public class GameRuleCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        final LiteralArgumentBuilder<CommandSourceStack> literalArgumentBuilder = Commands.literal("gamerule").requires(source -> source.hasPermission(2));
        GameRules.visitGameRuleTypes(
            new GameRules.GameRuleTypeVisitor() {
                @Override
                public <T extends GameRules.Value<T>> void visit(GameRules.Key<T> key, GameRules.Type<T> type) {
                    literalArgumentBuilder.then(
                        Commands.literal(key.getId())
                            .executes(context -> GameRuleCommand.queryRule(context.getSource(), key))
                            .then(type.createArgument("value").executes(context -> GameRuleCommand.setRule(context, key)))
                    );
                }
            }
        );
        dispatcher.register(literalArgumentBuilder);
    }

    static <T extends GameRules.Value<T>> int setRule(CommandContext<CommandSourceStack> source, GameRules.Key<T> gameRule) {
        CommandSourceStack commandSourceStack = source.getSource();
        T value = commandSourceStack.getServer().getGameRules().getRule(gameRule);
        value.setFromArgument(source, "value");
        commandSourceStack.sendSuccess(() -> Component.translatable("commands.gamerule.set", gameRule.getId(), value.toString()), true);
        return value.getCommandResult();
    }

    static <T extends GameRules.Value<T>> int queryRule(CommandSourceStack source, GameRules.Key<T> gameRule) {
        T value = source.getServer().getGameRules().getRule(gameRule);
        source.sendSuccess(() -> Component.translatable("commands.gamerule.query", gameRule.getId(), value.toString()), false);
        return value.getCommandResult();
    }
}
