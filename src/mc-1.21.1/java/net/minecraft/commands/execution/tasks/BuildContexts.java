package net.minecraft.commands.execution.tasks;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.brigadier.RedirectModifier;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ContextChain;
import com.mojang.brigadier.context.ContextChain.Stage;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.commands.CommandResultCallback;
import net.minecraft.commands.ExecutionCommandSource;
import net.minecraft.commands.execution.ChainModifiers;
import net.minecraft.commands.execution.CommandQueueEntry;
import net.minecraft.commands.execution.CustomCommandExecutor;
import net.minecraft.commands.execution.CustomModifierExecutor;
import net.minecraft.commands.execution.EntryAction;
import net.minecraft.commands.execution.ExecutionContext;
import net.minecraft.commands.execution.ExecutionControl;
import net.minecraft.commands.execution.Frame;
import net.minecraft.commands.execution.TraceCallbacks;
import net.minecraft.commands.execution.UnboundEntryAction;
import net.minecraft.network.chat.Component;

public class BuildContexts<T extends ExecutionCommandSource<T>> {
    @VisibleForTesting
    public static final DynamicCommandExceptionType ERROR_FORK_LIMIT_REACHED = new DynamicCommandExceptionType(
        forkLimit -> Component.translatableEscape("command.forkLimit", forkLimit)
    );
    private final String commandInput;
    private final ContextChain<T> command;

    public BuildContexts(String commandInput, ContextChain<T> command) {
        this.commandInput = commandInput;
        this.command = command;
    }

    protected void execute(T originalSource, List<T> sources, ExecutionContext<T> context, Frame frame, ChainModifiers chainModifiers) {
        ContextChain<T> contextChain = this.command;
        ChainModifiers chainModifiers2 = chainModifiers;
        List<T> list = sources;
        if (contextChain.getStage() != Stage.EXECUTE) {
            context.profiler().push(() -> "prepare " + this.commandInput);

            try {
                int i = context.forkLimit();

                while (contextChain.getStage() != Stage.EXECUTE) {
                    CommandContext<T> commandContext = contextChain.getTopContext();
                    if (commandContext.isForked()) {
                        chainModifiers2 = chainModifiers2.setForked();
                    }

                    RedirectModifier<T> redirectModifier = commandContext.getRedirectModifier();
                    if (redirectModifier instanceof CustomModifierExecutor<T> customModifierExecutor) {
                        customModifierExecutor.apply(originalSource, list, contextChain, chainModifiers2, ExecutionControl.create(context, frame));
                        return;
                    }

                    if (redirectModifier != null) {
                        context.incrementCost();
                        boolean bl = chainModifiers2.isForked();
                        List<T> list2 = new ObjectArrayList<>();

                        for (T executionCommandSource : list) {
                            try {
                                Collection<T> collection = ContextChain.runModifier(
                                    commandContext, executionCommandSource, (commandContextx, blx, ix) -> {}, bl
                                );
                                if (list2.size() + collection.size() >= i) {
                                    originalSource.handleError(ERROR_FORK_LIMIT_REACHED.create(i), bl, context.tracer());
                                    return;
                                }

                                list2.addAll(collection);
                            } catch (CommandSyntaxException commandSyntaxException) {
                                executionCommandSource.handleError(commandSyntaxException, bl, context.tracer());
                                if (!bl) {
                                    return;
                                }
                            }
                        }

                        list = list2;
                    }

                    contextChain = contextChain.nextStage();
                }
            } finally {
                context.profiler().pop();
            }
        }

        if (list.isEmpty()) {
            if (chainModifiers2.isReturn()) {
                context.queueNext(new CommandQueueEntry<>(frame, FallthroughTask.instance()));
            }
        } else {
            CommandContext<T> commandContext2 = contextChain.getTopContext();
            if (commandContext2.getCommand() instanceof CustomCommandExecutor<T> customCommandExecutor) {
                ExecutionControl<T> executionControl = ExecutionControl.create(context, frame);

                for (T executionCommandSource2 : list) {
                    customCommandExecutor.run(executionCommandSource2, contextChain, chainModifiers2, executionControl);
                }
            } else {
                if (chainModifiers2.isReturn()) {
                    T executionCommandSource3 = list.get(0);
                    executionCommandSource3 = executionCommandSource3.withCallback(
                        CommandResultCallback.chain(executionCommandSource3.callback(), frame.returnValueConsumer())
                    );
                    list = List.of(executionCommandSource3);
                }

                ExecuteCommand<T> executeCommand = new ExecuteCommand<>(this.commandInput, chainModifiers2, commandContext2);
                ContinuationTask.schedule(context, frame, list, (framex, argument) -> new CommandQueueEntry<>(framex, executeCommand.bind(argument)));
            }
        }
    }

    protected void traceCommandStart(ExecutionContext<T> executionContext, Frame frame) {
        TraceCallbacks traceCallbacks = executionContext.tracer();
        if (traceCallbacks != null) {
            traceCallbacks.onCommand(frame.depth(), this.commandInput);
        }
    }

    @Override
    public String toString() {
        return this.commandInput;
    }

    public static class Continuation<T extends ExecutionCommandSource<T>> extends BuildContexts<T> implements EntryAction<T> {
        private final ChainModifiers modifiers;
        private final T originalSource;
        private final List<T> sources;

        public Continuation(String commandInput, ContextChain<T> command, ChainModifiers modifiers, T originalSource, List<T> sources) {
            super(commandInput, command);
            this.originalSource = originalSource;
            this.sources = sources;
            this.modifiers = modifiers;
        }

        @Override
        public void execute(ExecutionContext<T> context, Frame frame) {
            this.execute(this.originalSource, this.sources, context, frame, this.modifiers);
        }
    }

    public static class TopLevel<T extends ExecutionCommandSource<T>> extends BuildContexts<T> implements EntryAction<T> {
        private final T source;

        public TopLevel(String commandInput, ContextChain<T> command, T source) {
            super(commandInput, command);
            this.source = source;
        }

        @Override
        public void execute(ExecutionContext<T> context, Frame frame) {
            this.traceCommandStart(context, frame);
            this.execute(this.source, List.of(this.source), context, frame, ChainModifiers.DEFAULT);
        }
    }

    public static class Unbound<T extends ExecutionCommandSource<T>> extends BuildContexts<T> implements UnboundEntryAction<T> {
        public Unbound(String string, ContextChain<T> contextChain) {
            super(string, contextChain);
        }

        public void execute(T executionCommandSource, ExecutionContext<T> executionContext, Frame frame) {
            this.traceCommandStart(executionContext, frame);
            this.execute(executionCommandSource, List.of(executionCommandSource), executionContext, frame, ChainModifiers.DEFAULT);
        }
    }
}
