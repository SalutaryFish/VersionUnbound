package net.minecraft.server.commands;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ContextChain;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.datafixers.util.Pair;
import java.util.Collection;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandResultCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.ExecutionCommandSource;
import net.minecraft.commands.FunctionInstantiationException;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.CompoundTagArgument;
import net.minecraft.commands.arguments.NbtPathArgument;
import net.minecraft.commands.arguments.item.FunctionArgument;
import net.minecraft.commands.execution.ChainModifiers;
import net.minecraft.commands.execution.CustomCommandExecutor;
import net.minecraft.commands.execution.ExecutionControl;
import net.minecraft.commands.execution.tasks.CallFunction;
import net.minecraft.commands.execution.tasks.FallthroughTask;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.commands.functions.InstantiatedFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.ServerFunctionManager;
import net.minecraft.server.commands.data.DataAccessor;
import net.minecraft.server.commands.data.DataCommands;

public class FunctionCommand {
    private static final DynamicCommandExceptionType ERROR_ARGUMENT_NOT_COMPOUND = new DynamicCommandExceptionType(
        contents -> Component.translatableEscape("commands.function.error.argument_not_compound", contents)
    );
    static final DynamicCommandExceptionType ERROR_NO_FUNCTIONS = new DynamicCommandExceptionType(
        contents -> Component.translatableEscape("commands.function.scheduled.no_functions", contents)
    );
    @VisibleForTesting
    public static final Dynamic2CommandExceptionType ERROR_FUNCTION_INSTANTATION_FAILURE = new Dynamic2CommandExceptionType(
        (functionId, message) -> Component.translatableEscape("commands.function.instantiationFailure", functionId, message)
    );
    public static final SuggestionProvider<CommandSourceStack> SUGGEST_FUNCTION = (context, suggestions) -> {
        ServerFunctionManager serverFunctionManager = context.getSource().getServer().getFunctions();
        SharedSuggestionProvider.suggestResource(serverFunctionManager.getTagNames(), suggestions, "#");
        return SharedSuggestionProvider.suggestResource(serverFunctionManager.getFunctionNames(), suggestions);
    };
    static final FunctionCommand.Callbacks<CommandSourceStack> FULL_CONTEXT_CALLBACKS = new FunctionCommand.Callbacks<CommandSourceStack>() {
        public void signalResult(CommandSourceStack commandSourceStack, ResourceLocation resourceLocation, int i) {
            commandSourceStack.sendSuccess(() -> Component.translatable("commands.function.result", Component.translationArg(resourceLocation), i), true);
        }
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> literalArgumentBuilder = Commands.literal("with");

        for (DataCommands.DataProvider dataProvider : DataCommands.SOURCE_PROVIDERS) {
            dataProvider.wrap(literalArgumentBuilder, argumentBuilder -> argumentBuilder.executes(new FunctionCommand.FunctionCustomExecutor() {
                @Override
                protected CompoundTag arguments(CommandContext<CommandSourceStack> commandContext) throws CommandSyntaxException {
                    return dataProvider.access(commandContext).getData();
                }
            }).then(Commands.argument("path", NbtPathArgument.nbtPath()).executes(new FunctionCommand.FunctionCustomExecutor() {
                @Override
                protected CompoundTag arguments(CommandContext<CommandSourceStack> commandContext) throws CommandSyntaxException {
                    return FunctionCommand.getArgumentTag(NbtPathArgument.getPath(commandContext, "path"), dataProvider.access(commandContext));
                }
            })));
        }

        dispatcher.register(
            Commands.literal("function")
                .requires(source -> source.hasPermission(2))
                .then(
                    Commands.argument("name", FunctionArgument.functions()).suggests(SUGGEST_FUNCTION).executes(new FunctionCommand.FunctionCustomExecutor() {
                        @Nullable
                        @Override
                        protected CompoundTag arguments(CommandContext<CommandSourceStack> commandContext) {
                            return null;
                        }
                    }).then(Commands.argument("arguments", CompoundTagArgument.compoundTag()).executes(new FunctionCommand.FunctionCustomExecutor() {
                        @Override
                        protected CompoundTag arguments(CommandContext<CommandSourceStack> commandContext) {
                            return CompoundTagArgument.getCompoundTag(commandContext, "arguments");
                        }
                    })).then(literalArgumentBuilder)
                )
        );
    }

    static CompoundTag getArgumentTag(NbtPathArgument.NbtPath nbtPath, DataAccessor dataAccessor) throws CommandSyntaxException {
        Tag tag = DataCommands.getSingleTag(nbtPath, dataAccessor);
        if (tag instanceof CompoundTag compoundTag) {
            return compoundTag;
        } else {
            throw ERROR_ARGUMENT_NOT_COMPOUND.create(tag.getType().getName());
        }
    }

    public static CommandSourceStack modifySenderForExecution(CommandSourceStack source) {
        return source.withSuppressedOutput().withMaximumPermission(2);
    }

    public static <T extends ExecutionCommandSource<T>> void queueFunctions(
        Collection<CommandFunction<T>> functions,
        @Nullable CompoundTag arguments,
        T originalSource,
        T source,
        ExecutionControl<T> executionControl,
        FunctionCommand.Callbacks<T> callbacks,
        ChainModifiers chainModifiers
    ) throws CommandSyntaxException {
        if (chainModifiers.isReturn()) {
            queueFunctionsAsReturn(functions, arguments, originalSource, source, executionControl, callbacks);
        } else {
            queueFunctionsNoReturn(functions, arguments, originalSource, source, executionControl, callbacks);
        }
    }

    private static <T extends ExecutionCommandSource<T>> void instantiateAndQueueFunctions(
        @Nullable CompoundTag arguments,
        ExecutionControl<T> executionControl,
        CommandDispatcher<T> dispatcher,
        T source,
        CommandFunction<T> function,
        ResourceLocation functionId,
        CommandResultCallback resultCallback,
        boolean returnParentFrame
    ) throws CommandSyntaxException {
        try {
            InstantiatedFunction<T> instantiatedFunction = function.instantiate(arguments, dispatcher);
            executionControl.queueNext(new CallFunction<>(instantiatedFunction, resultCallback, returnParentFrame).bind(source));
        } catch (FunctionInstantiationException functionInstantiationException) {
            throw ERROR_FUNCTION_INSTANTATION_FAILURE.create(functionId, functionInstantiationException.messageComponent());
        }
    }

    private static <T extends ExecutionCommandSource<T>> CommandResultCallback decorateOutputIfNeeded(
        T source, FunctionCommand.Callbacks<T> callbacks, ResourceLocation function, CommandResultCallback resultCallback
    ) {
        return source.isSilent() ? resultCallback : (success, commands) -> {
            callbacks.signalResult(source, function, commands);
            resultCallback.onResult(success, commands);
        };
    }

    private static <T extends ExecutionCommandSource<T>> void queueFunctionsAsReturn(
        Collection<CommandFunction<T>> functions,
        @Nullable CompoundTag arguments,
        T originalSource,
        T source,
        ExecutionControl<T> exectutionControl,
        FunctionCommand.Callbacks<T> callbacks
    ) throws CommandSyntaxException {
        CommandDispatcher<T> commandDispatcher = originalSource.dispatcher();
        T executionCommandSource = source.clearCallbacks();
        CommandResultCallback commandResultCallback = CommandResultCallback.chain(
            originalSource.callback(), exectutionControl.currentFrame().returnValueConsumer()
        );

        for (CommandFunction<T> commandFunction : functions) {
            ResourceLocation resourceLocation = commandFunction.id();
            CommandResultCallback commandResultCallback2 = decorateOutputIfNeeded(originalSource, callbacks, resourceLocation, commandResultCallback);
            instantiateAndQueueFunctions(
                arguments, exectutionControl, commandDispatcher, executionCommandSource, commandFunction, resourceLocation, commandResultCallback2, true
            );
        }

        exectutionControl.queueNext(FallthroughTask.instance());
    }

    private static <T extends ExecutionCommandSource<T>> void queueFunctionsNoReturn(
        Collection<CommandFunction<T>> functions,
        @Nullable CompoundTag arguments,
        T originalSource,
        T source,
        ExecutionControl<T> executionControl,
        FunctionCommand.Callbacks<T> callbacks
    ) throws CommandSyntaxException {
        CommandDispatcher<T> commandDispatcher = originalSource.dispatcher();
        T executionCommandSource = source.clearCallbacks();
        CommandResultCallback commandResultCallback = originalSource.callback();
        if (!functions.isEmpty()) {
            if (functions.size() == 1) {
                CommandFunction<T> commandFunction = functions.iterator().next();
                ResourceLocation resourceLocation = commandFunction.id();
                CommandResultCallback commandResultCallback2 = decorateOutputIfNeeded(originalSource, callbacks, resourceLocation, commandResultCallback);
                instantiateAndQueueFunctions(
                    arguments, executionControl, commandDispatcher, executionCommandSource, commandFunction, resourceLocation, commandResultCallback2, false
                );
            } else if (commandResultCallback == CommandResultCallback.EMPTY) {
                for (CommandFunction<T> commandFunction2 : functions) {
                    ResourceLocation resourceLocation2 = commandFunction2.id();
                    CommandResultCallback commandResultCallback3 = decorateOutputIfNeeded(originalSource, callbacks, resourceLocation2, commandResultCallback);
                    instantiateAndQueueFunctions(
                        arguments,
                        executionControl,
                        commandDispatcher,
                        executionCommandSource,
                        commandFunction2,
                        resourceLocation2,
                        commandResultCallback3,
                        false
                    );
                }
            } else {
                class Accumulator {
                    boolean anyResult;
                    int sum;

                    public void add(int result) {
                        this.anyResult = true;
                        this.sum += result;
                    }
                }

                Accumulator lv = new Accumulator();
                CommandResultCallback commandResultCallback4 = (success, result) -> lv.add(result);

                for (CommandFunction<T> commandFunction3 : functions) {
                    ResourceLocation resourceLocation3 = commandFunction3.id();
                    CommandResultCallback commandResultCallback5 = decorateOutputIfNeeded(originalSource, callbacks, resourceLocation3, commandResultCallback4);
                    instantiateAndQueueFunctions(
                        arguments,
                        executionControl,
                        commandDispatcher,
                        executionCommandSource,
                        commandFunction3,
                        resourceLocation3,
                        commandResultCallback5,
                        false
                    );
                }

                executionControl.queueNext((executionContext, frame) -> {
                    if (lv.anyResult) {
                        commandResultCallback.onSuccess(lv.sum);
                    }
                });
            }
        }
    }

    public interface Callbacks<T> {
        void signalResult(T var1, ResourceLocation var2, int var3);
    }

    abstract static class FunctionCustomExecutor
        extends CustomCommandExecutor.WithErrorHandling<CommandSourceStack>
        implements CustomCommandExecutor.CommandAdapter<CommandSourceStack> {
        @Nullable
        protected abstract CompoundTag arguments(CommandContext<CommandSourceStack> var1) throws CommandSyntaxException;

        public void runGuarded(
            CommandSourceStack source,
            ContextChain<CommandSourceStack> contextChain,
            ChainModifiers chainModifiers,
            ExecutionControl<CommandSourceStack> executionControl
        ) throws CommandSyntaxException {
            CommandContext<CommandSourceStack> commandContext = contextChain.getTopContext().copyFor(source);
            Pair<ResourceLocation, Collection<CommandFunction<CommandSourceStack>>> pair = FunctionArgument.getFunctionCollection(commandContext, "name");
            Collection<CommandFunction<CommandSourceStack>> collection = pair.getSecond();
            if (collection.isEmpty()) {
                throw FunctionCommand.ERROR_NO_FUNCTIONS.create(Component.translationArg(pair.getFirst()));
            }

            CompoundTag compoundTag = this.arguments(commandContext);
            CommandSourceStack commandSourceStack = FunctionCommand.modifySenderForExecution(source);
            if (collection.size() == 1) {
                source.sendSuccess(
                    () -> Component.translatable("commands.function.scheduled.single", Component.translationArg(collection.iterator().next().id())), true
                );
            } else {
                source.sendSuccess(
                    () -> Component.translatable(
                        "commands.function.scheduled.multiple",
                        ComponentUtils.formatList(collection.stream().map(CommandFunction::id).toList(), Component::translationArg)
                    ),
                    true
                );
            }

            FunctionCommand.queueFunctions(
                collection, compoundTag, source, commandSourceStack, executionControl, FunctionCommand.FULL_CONTEXT_CALLBACKS, chainModifiers
            );
        }
    }
}
