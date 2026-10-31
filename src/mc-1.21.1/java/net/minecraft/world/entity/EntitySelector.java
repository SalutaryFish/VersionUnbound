package net.minecraft.world.entity;

import com.google.common.base.Predicates;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.scores.Team;

public final class EntitySelector {
    /**
     * Selects only entities which are alive
     */
    public static final Predicate<Entity> ENTITY_STILL_ALIVE = Entity::isAlive;
    /**
     * Selects only entities which are LivingEntities and alive
     */
    public static final Predicate<Entity> LIVING_ENTITY_STILL_ALIVE = entity -> entity.isAlive() && entity instanceof LivingEntity;
    /**
     * Selects only entities which are neither ridden by anything nor ride on anything
     */
    public static final Predicate<Entity> ENTITY_NOT_BEING_RIDDEN = entity -> entity.isAlive() && !entity.isVehicle() && !entity.isPassenger();
    /**
     * Selects only entities which are container entities
     */
    public static final Predicate<Entity> CONTAINER_ENTITY_SELECTOR = entity -> entity instanceof Container && entity.isAlive();
    /**
     * Selects entities which are neither creative-mode players nor spectator-players
     */
    public static final Predicate<Entity> NO_CREATIVE_OR_SPECTATOR = entity -> !(entity instanceof Player)
        || !entity.isSpectator() && !((Player)entity).isCreative();
    /**
     * Selects entities which are either not players or players that are not spectating
     */
    public static final Predicate<Entity> NO_SPECTATORS = entity -> !entity.isSpectator();
    /**
     * Selects entities which are collidable with and aren't spectators
     */
    public static final Predicate<Entity> CAN_BE_COLLIDED_WITH = NO_SPECTATORS.and(Entity::canBeCollidedWith);

    private EntitySelector() {
    }

    public static Predicate<Entity> withinDistance(double x, double y, double z, double range) {
        double d = range * range;
        return entity -> entity != null && entity.distanceToSqr(x, y, z) <= d;
    }

    public static Predicate<Entity> pushableBy(Entity entity) {
        Team team = entity.getTeam();
        Team.CollisionRule collisionRule = team == null ? Team.CollisionRule.ALWAYS : team.getCollisionRule();
        return collisionRule == Team.CollisionRule.NEVER
            ? Predicates.alwaysFalse()
            : NO_SPECTATORS.and(
                pushedEntity -> {
                    if (!pushedEntity.isPushable()) {
                        return false;
                    }

                    if (!entity.level().isClientSide || pushedEntity instanceof Player && ((Player)pushedEntity).isLocalPlayer()) {
                        Team team2 = pushedEntity.getTeam();
                        Team.CollisionRule collisionRule2 = team2 == null ? Team.CollisionRule.ALWAYS : team2.getCollisionRule();
                        if (collisionRule2 == Team.CollisionRule.NEVER) {
                            return false;
                        }

                        boolean bl = team != null && team.isAlliedTo(team2);
                        return (collisionRule == Team.CollisionRule.PUSH_OWN_TEAM || collisionRule2 == Team.CollisionRule.PUSH_OWN_TEAM) && bl
                            ? false
                            : collisionRule != Team.CollisionRule.PUSH_OTHER_TEAMS && collisionRule2 != Team.CollisionRule.PUSH_OTHER_TEAMS || bl;
                    } else {
                        return false;
                    }
                }
            );
    }

    public static Predicate<Entity> notRiding(Entity entity) {
        return passenger -> {
            while (passenger.isPassenger()) {
                passenger = passenger.getVehicle();
                if (passenger == entity) {
                    return false;
                }
            }

            return true;
        };
    }

    public static class MobCanWearArmorEntitySelector implements Predicate<Entity> {
        private final ItemStack itemStack;

        public MobCanWearArmorEntitySelector(ItemStack stack) {
            this.itemStack = stack;
        }

        public boolean test(@Nullable Entity entity) {
            if (!entity.isAlive()) {
                return false;
            } else {
                return !(entity instanceof LivingEntity livingEntity) ? false : livingEntity.canTakeItem(this.itemStack);
            }
        }
    }
}
