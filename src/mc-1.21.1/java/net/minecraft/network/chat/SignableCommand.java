package net.minecraft.network.chat;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.context.ParsedArgument;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.commands.arguments.SignedArgument;

public record SignableCommand<S>(List<SignableCommand.Argument<S>> arguments) {
    public static <S> boolean hasSignableArguments(ParseResults<S> parseResults) {
        return !of(parseResults).arguments().isEmpty();
    }

    public static <S> SignableCommand<S> of(ParseResults<S> results) {
        String string = results.getReader().getString();
        CommandContextBuilder<S> commandContextBuilder = results.getContext();
        CommandContextBuilder<S> commandContextBuilder2 = commandContextBuilder;
        List<SignableCommand.Argument<S>> list = collectArguments(string, commandContextBuilder2);

        CommandContextBuilder<S> commandContextBuilder3;
        while (
            (commandContextBuilder3 = commandContextBuilder2.getChild()) != null && commandContextBuilder3.getRootNode() != commandContextBuilder.getRootNode()
        ) {
            list.addAll(collectArguments(string, commandContextBuilder3));
            commandContextBuilder2 = commandContextBuilder3;
        }

        return new SignableCommand<>(list);
    }

    private static <S> List<SignableCommand.Argument<S>> collectArguments(String key, CommandContextBuilder<S> contextBuilder) {
        List<SignableCommand.Argument<S>> list = new ArrayList<>();

        for (ParsedCommandNode<S> parsedCommandNode : contextBuilder.getNodes()) {
            if (parsedCommandNode.getNode() instanceof ArgumentCommandNode<S, ?> argumentCommandNode && argumentCommandNode.getType() instanceof SignedArgument
                )
             {
                ParsedArgument<S, ?> parsedArgument = contextBuilder.getArguments().get(argumentCommandNode.getName());
                if (parsedArgument != null) {
                    String string = parsedArgument.getRange().get(key);
                    list.add(new SignableCommand.Argument<>(argumentCommandNode, string));
                }
            }
        }

        return list;
    }

    @Nullable
    public SignableCommand.Argument<S> getArgument(String argument) {
        for (SignableCommand.Argument<S> argument2 : this.arguments) {
            if (argument.equals(argument2.name())) {
                return argument2;
            }
        }

        return null;
    }

    public record Argument<S>(ArgumentCommandNode<S, ?> node, String value) {
        public String name() {
            return this.node.getName();
        }
    }
}
