package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;

public class DifficultyCommand {
    private static final DynamicCommandExceptionType ERROR_ALREADY_DIFFICULT = new DynamicCommandExceptionType(
        difficulty -> Component.translatableEscape("commands.difficulty.failure", difficulty)
    );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> literalArgumentBuilder = Commands.literal("difficulty");

        for (Difficulty difficulty : Difficulty.values()) {
            literalArgumentBuilder.then(Commands.literal(difficulty.getKey()).executes(context -> setDifficulty(context.getSource(), difficulty)));
        }

        dispatcher.register(literalArgumentBuilder.requires(source -> source.hasPermission(2)).executes(context -> {
            Difficulty difficultyx = context.getSource().getLevel().getDifficulty();
            context.getSource().sendSuccess(() -> Component.translatable("commands.difficulty.query", difficultyx.getDisplayName()), false);
            return difficultyx.getId();
        }));
    }

    public static int setDifficulty(CommandSourceStack source, Difficulty difficulty) throws CommandSyntaxException {
        MinecraftServer minecraftServer = source.getServer();
        if (minecraftServer.getWorldData().getDifficulty() == difficulty) {
            throw ERROR_ALREADY_DIFFICULT.create(difficulty.getKey());
        }

        minecraftServer.setDifficulty(difficulty, true);
        source.sendSuccess(() -> Component.translatable("commands.difficulty.success", difficulty.getDisplayName()), true);
        return 0;
    }
}
