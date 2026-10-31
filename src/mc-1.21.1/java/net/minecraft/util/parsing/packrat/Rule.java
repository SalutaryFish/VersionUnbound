package net.minecraft.util.parsing.packrat;

import java.util.Optional;

public interface Rule<S, T> {
    Optional<T> parse(ParseState<S> var1);

    static <S, T> Rule<S, T> fromTerm(Term<S> child, Rule.RuleAction<S, T> action) {
        return new Rule.WrappedTerm<>(action, child);
    }

    static <S, T> Rule<S, T> fromTerm(Term<S> child, Rule.SimpleRuleAction<T> action) {
        return new Rule.WrappedTerm<>((parseState, scope) -> Optional.of(action.run(scope)), child);
    }

    @FunctionalInterface
    interface RuleAction<S, T> {
        Optional<T> run(ParseState<S> var1, Scope var2);
    }

    @FunctionalInterface
    interface SimpleRuleAction<T> {
        T run(Scope var1);
    }

    record WrappedTerm<S, T>(Rule.RuleAction<S, T> action, Term<S> child) implements Rule<S, T> {
        @Override
        public Optional<T> parse(ParseState<S> parseState) {
            Scope scope = new Scope();
            return this.child.parse(parseState, scope, Control.UNBOUND) ? this.action.run(parseState, scope) : Optional.empty();
        }
    }
}
