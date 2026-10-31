package net.minecraft.commands.functions;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.commands.ExecutionCommandSource;
import net.minecraft.commands.execution.UnboundEntryAction;
import net.minecraft.resources.ResourceLocation;

class FunctionBuilder<T extends ExecutionCommandSource<T>> {
    @Nullable
    private List<UnboundEntryAction<T>> plainEntries = new ArrayList<>();
    @Nullable
    private List<MacroFunction.Entry<T>> macroEntries;
    private final List<String> macroArguments = new ArrayList<>();

    public void addCommand(UnboundEntryAction<T> command) {
        if (this.macroEntries != null) {
            this.macroEntries.add(new MacroFunction.PlainTextEntry<>(command));
        } else {
            this.plainEntries.add(command);
        }
    }

    private int getArgumentIndex(String argument) {
        int i = this.macroArguments.indexOf(argument);
        if (i == -1) {
            i = this.macroArguments.size();
            this.macroArguments.add(argument);
        }

        return i;
    }

    private IntList convertToIndices(List<String> arguments) {
        IntArrayList intArrayList = new IntArrayList(arguments.size());

        for (String string : arguments) {
            intArrayList.add(this.getArgumentIndex(string));
        }

        return intArrayList;
    }

    public void addMacro(String name, int lineNumber, T compilationContext) {
        StringTemplate stringTemplate = StringTemplate.fromString(name, lineNumber);
        if (this.plainEntries != null) {
            this.macroEntries = new ArrayList<>(this.plainEntries.size() + 1);

            for (UnboundEntryAction<T> unboundEntryAction : this.plainEntries) {
                this.macroEntries.add(new MacroFunction.PlainTextEntry<>(unboundEntryAction));
            }

            this.plainEntries = null;
        }

        this.macroEntries.add(new MacroFunction.MacroEntry<>(stringTemplate, this.convertToIndices(stringTemplate.variables()), compilationContext));
    }

    public CommandFunction<T> build(ResourceLocation id) {
        return this.macroEntries != null ? new MacroFunction<>(id, this.macroEntries, this.macroArguments) : new PlainTextFunction<>(id, this.plainEntries);
    }
}
