package net.minecraft.server.advancements;

import it.unimi.dsi.fastutil.Stack;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.DisplayInfo;

public class AdvancementVisibilityEvaluator {
    private static final int VISIBILITY_DEPTH = 2;

    private static AdvancementVisibilityEvaluator.VisibilityRule evaluateVisibilityRule(Advancement advancement, boolean alwaysShow) {
        Optional<DisplayInfo> optional = advancement.display();
        if (optional.isEmpty()) {
            return AdvancementVisibilityEvaluator.VisibilityRule.HIDE;
        } else if (alwaysShow) {
            return AdvancementVisibilityEvaluator.VisibilityRule.SHOW;
        } else {
            return optional.get().isHidden() ? AdvancementVisibilityEvaluator.VisibilityRule.HIDE : AdvancementVisibilityEvaluator.VisibilityRule.NO_CHANGE;
        }
    }

    private static boolean evaluateVisiblityForUnfinishedNode(Stack<AdvancementVisibilityEvaluator.VisibilityRule> visibilityRules) {
        for (int i = 0; i <= 2; i++) {
            AdvancementVisibilityEvaluator.VisibilityRule visibilityRule = visibilityRules.peek(i);
            if (visibilityRule == AdvancementVisibilityEvaluator.VisibilityRule.SHOW) {
                return true;
            }

            if (visibilityRule == AdvancementVisibilityEvaluator.VisibilityRule.HIDE) {
                return false;
            }
        }

        return false;
    }

    private static boolean evaluateVisibility(
        AdvancementNode advancement,
        Stack<AdvancementVisibilityEvaluator.VisibilityRule> visibilityRules,
        Predicate<AdvancementNode> predicate,
        AdvancementVisibilityEvaluator.Output output
    ) {
        boolean bl = predicate.test(advancement);
        AdvancementVisibilityEvaluator.VisibilityRule visibilityRule = evaluateVisibilityRule(advancement.advancement(), bl);
        boolean bl2 = bl;
        visibilityRules.push(visibilityRule);

        for (AdvancementNode advancementNode : advancement.children()) {
            bl2 |= evaluateVisibility(advancementNode, visibilityRules, predicate, output);
        }

        boolean bl3 = bl2 || evaluateVisiblityForUnfinishedNode(visibilityRules);
        visibilityRules.pop();
        output.accept(advancement, bl3);
        return bl2;
    }

    public static void evaluateVisibility(AdvancementNode advancement, Predicate<AdvancementNode> predicate, AdvancementVisibilityEvaluator.Output output) {
        AdvancementNode advancementNode = advancement.root();
        Stack<AdvancementVisibilityEvaluator.VisibilityRule> stack = new ObjectArrayList<>();

        for (int i = 0; i <= 2; i++) {
            stack.push(AdvancementVisibilityEvaluator.VisibilityRule.NO_CHANGE);
        }

        evaluateVisibility(advancementNode, stack, predicate, output);
    }

    @FunctionalInterface
    public interface Output {
        void accept(AdvancementNode var1, boolean var2);
    }

    enum VisibilityRule {
        SHOW,
        HIDE,
        NO_CHANGE;
    }
}
