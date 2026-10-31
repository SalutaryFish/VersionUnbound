package net.minecraft.commands.execution;

@FunctionalInterface
public interface UnboundEntryAction<T> {
    void execute(T var1, ExecutionContext<T> var2, Frame var3);

    default EntryAction<T> bind(T source) {
        return (context, frame) -> this.execute(source, context, frame);
    }
}
