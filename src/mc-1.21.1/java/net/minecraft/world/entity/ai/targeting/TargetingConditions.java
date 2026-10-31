package net.minecraft.world.entity.ai.targeting;

import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

public class TargetingConditions {
    public static final TargetingConditions DEFAULT = forCombat();
    private static final double MIN_VISIBILITY_DISTANCE_FOR_INVISIBLE_TARGET = 2.0;
    private final boolean isCombat;
    private double range = -1.0;
    private boolean checkLineOfSight = true;
    private boolean testInvisible = true;
    @Nullable
    private Predicate<LivingEntity> selector;

    private TargetingConditions(boolean isCombat) {
        this.isCombat = isCombat;
    }

    public static TargetingConditions forCombat() {
        return new TargetingConditions(true);
    }

    public static TargetingConditions forNonCombat() {
        return new TargetingConditions(false);
    }

    public TargetingConditions copy() {
        TargetingConditions targetingConditions = this.isCombat ? forCombat() : forNonCombat();
        targetingConditions.range = this.range;
        targetingConditions.checkLineOfSight = this.checkLineOfSight;
        targetingConditions.testInvisible = this.testInvisible;
        targetingConditions.selector = this.selector;
        return targetingConditions;
    }

    public TargetingConditions range(double distance) {
        this.range = distance;
        return this;
    }

    public TargetingConditions ignoreLineOfSight() {
        this.checkLineOfSight = false;
        return this;
    }

    public TargetingConditions ignoreInvisibilityTesting() {
        this.testInvisible = false;
        return this;
    }

    public TargetingConditions selector(@Nullable Predicate<LivingEntity> customPredicate) {
        this.selector = customPredicate;
        return this;
    }

    public boolean test(@Nullable LivingEntity attacker, LivingEntity target) {
        if (attacker == target) {
            return false;
        }

        if (!target.canBeSeenByAnyone()) {
            return false;
        }

        if (this.selector != null && !this.selector.test(target)) {
            return false;
        }

        if (attacker == null) {
            if (this.isCombat && (!target.canBeSeenAsEnemy() || target.level().getDifficulty() == Difficulty.PEACEFUL)) {
                return false;
            }
        } else {
            if (this.isCombat && (!attacker.canAttack(target) || !attacker.canAttackType(target.getType()) || attacker.isAlliedTo(target))) {
                return false;
            }

            if (this.range > 0.0) {
                double d = this.testInvisible ? target.getVisibilityPercent(attacker) : 1.0;
                double e = Math.max(this.range * d, 2.0);
                double f = attacker.distanceToSqr(target.getX(), target.getY(), target.getZ());
                if (f > e * e) {
                    return false;
                }
            }

            if (this.checkLineOfSight && attacker instanceof Mob mob && !mob.getSensing().hasLineOfSight(target)) {
                return false;
            }
        }

        return true;
    }
}
