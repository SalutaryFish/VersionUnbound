package net.minecraft.world.entity.ai.goal;

import com.google.common.annotations.VisibleForTesting;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.util.profiling.ProfilerFiller;

public class GoalSelector {
    private static final WrappedGoal NO_GOAL = new WrappedGoal(Integer.MAX_VALUE, new Goal() {
        @Override
        public boolean canUse() {
            return false;
        }
    }) {
        @Override
        public boolean isRunning() {
            return false;
        }
    };
    /**
     * Goals currently using a particular flag
     */
    private final Map<Goal.Flag, WrappedGoal> lockedFlags = new EnumMap<>(Goal.Flag.class);
    private final Set<WrappedGoal> availableGoals = new ObjectLinkedOpenHashSet<>();
    private final Supplier<ProfilerFiller> profiler;
    private final EnumSet<Goal.Flag> disabledFlags = EnumSet.noneOf(Goal.Flag.class);

    public GoalSelector(Supplier<ProfilerFiller> profiler) {
        this.profiler = profiler;
    }

    /**
     * Add a goal to the GoalSelector with a certain priority. Lower numbers are higher priority.
     */
    public void addGoal(int priority, Goal goal) {
        this.availableGoals.add(new WrappedGoal(priority, goal));
    }

    @VisibleForTesting
    public void removeAllGoals(Predicate<Goal> filter) {
        this.availableGoals.removeIf(wrappedGoal -> filter.test(wrappedGoal.getGoal()));
    }

    /**
     * Remove the goal from the GoalSelector. This must be the same object as the goal you are trying to remove, which may not always be accessible.
     */
    public void removeGoal(Goal goal) {
        for (WrappedGoal wrappedGoal : this.availableGoals) {
            if (wrappedGoal.getGoal() == goal && wrappedGoal.isRunning()) {
                wrappedGoal.stop();
            }
        }

        this.availableGoals.removeIf(wrappedGoalx -> wrappedGoalx.getGoal() == goal);
    }

    private static boolean goalContainsAnyFlags(WrappedGoal goal, EnumSet<Goal.Flag> flag) {
        for (Goal.Flag flag2 : goal.getFlags()) {
            if (flag.contains(flag2)) {
                return true;
            }
        }

        return false;
    }

    private static boolean goalCanBeReplacedForAllFlags(WrappedGoal goal, Map<Goal.Flag, WrappedGoal> flag) {
        for (Goal.Flag flag2 : goal.getFlags()) {
            if (!flag.getOrDefault(flag2, NO_GOAL).canBeReplacedBy(goal)) {
                return false;
            }
        }

        return true;
    }

    public void tick() {
        ProfilerFiller profilerFiller = this.profiler.get();
        profilerFiller.push("goalCleanup");

        for (WrappedGoal wrappedGoal : this.availableGoals) {
            if (wrappedGoal.isRunning() && (goalContainsAnyFlags(wrappedGoal, this.disabledFlags) || !wrappedGoal.canContinueToUse())) {
                wrappedGoal.stop();
            }
        }

        this.lockedFlags.entrySet().removeIf(entry -> !entry.getValue().isRunning());
        profilerFiller.pop();
        profilerFiller.push("goalUpdate");

        for (WrappedGoal wrappedGoal : this.availableGoals) {
            if (!wrappedGoal.isRunning()
                && !goalContainsAnyFlags(wrappedGoal, this.disabledFlags)
                && goalCanBeReplacedForAllFlags(wrappedGoal, this.lockedFlags)
                && wrappedGoal.canUse()) {
                for (Goal.Flag flag : wrappedGoal.getFlags()) {
                    WrappedGoal wrappedGoal2 = this.lockedFlags.getOrDefault(flag, NO_GOAL);
                    wrappedGoal2.stop();
                    this.lockedFlags.put(flag, wrappedGoal);
                }

                wrappedGoal.start();
            }
        }

        profilerFiller.pop();
        this.tickRunningGoals(true);
    }

    public void tickRunningGoals(boolean tickAllRunning) {
        ProfilerFiller profilerFiller = this.profiler.get();
        profilerFiller.push("goalTick");

        for (WrappedGoal wrappedGoal : this.availableGoals) {
            if (wrappedGoal.isRunning() && (tickAllRunning || wrappedGoal.requiresUpdateEveryTick())) {
                wrappedGoal.tick();
            }
        }

        profilerFiller.pop();
    }

    public Set<WrappedGoal> getAvailableGoals() {
        return this.availableGoals;
    }

    public void disableControlFlag(Goal.Flag flag) {
        this.disabledFlags.add(flag);
    }

    public void enableControlFlag(Goal.Flag flag) {
        this.disabledFlags.remove(flag);
    }

    public void setControlFlag(Goal.Flag flag, boolean enabled) {
        if (enabled) {
            this.enableControlFlag(flag);
        } else {
            this.disableControlFlag(flag);
        }
    }
}
