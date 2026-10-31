package net.minecraft.world.entity;

import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;

public interface NeutralMob {
    String TAG_ANGER_TIME = "AngerTime";
    String TAG_ANGRY_AT = "AngryAt";

    int getRemainingPersistentAngerTime();

    void setRemainingPersistentAngerTime(int var1);

    @Nullable
    UUID getPersistentAngerTarget();

    void setPersistentAngerTarget(@Nullable UUID var1);

    void startPersistentAngerTimer();

    default void addPersistentAngerSaveData(CompoundTag nbt) {
        nbt.putInt("AngerTime", this.getRemainingPersistentAngerTime());
        if (this.getPersistentAngerTarget() != null) {
            nbt.putUUID("AngryAt", this.getPersistentAngerTarget());
        }
    }

    default void readPersistentAngerSaveData(Level level, CompoundTag tag) {
        this.setRemainingPersistentAngerTime(tag.getInt("AngerTime"));
        if (level instanceof ServerLevel) {
            if (!tag.hasUUID("AngryAt")) {
                this.setPersistentAngerTarget(null);
            } else {
                UUID uUID = tag.getUUID("AngryAt");
                this.setPersistentAngerTarget(uUID);
                Entity entity = ((ServerLevel)level).getEntity(uUID);
                if (entity != null) {
                    if (entity instanceof Mob mob) {
                        this.setTarget(mob);
                        this.setLastHurtByMob(mob);
                    }

                    if (entity instanceof Player player) {
                        this.setTarget(player);
                        this.setLastHurtByPlayer(player);
                    }
                }
            }
        }
    }

    default void updatePersistentAnger(ServerLevel serverLevel, boolean updateAnger) {
        LivingEntity livingEntity = this.getTarget();
        UUID uUID = this.getPersistentAngerTarget();
        if ((livingEntity == null || livingEntity.isDeadOrDying()) && uUID != null && serverLevel.getEntity(uUID) instanceof Mob) {
            this.stopBeingAngry();
        } else {
            if (livingEntity != null && !Objects.equals(uUID, livingEntity.getUUID())) {
                this.setPersistentAngerTarget(livingEntity.getUUID());
                this.startPersistentAngerTimer();
            }

            if (this.getRemainingPersistentAngerTime() > 0 && (livingEntity == null || livingEntity.getType() != EntityType.PLAYER || !updateAnger)) {
                this.setRemainingPersistentAngerTime(this.getRemainingPersistentAngerTime() - 1);
                if (this.getRemainingPersistentAngerTime() == 0) {
                    this.stopBeingAngry();
                }
            }
        }
    }

    default boolean isAngryAt(LivingEntity target) {
        if (!this.canAttack(target)) {
            return false;
        } else {
            return target.getType() == EntityType.PLAYER && this.isAngryAtAllPlayers(target.level())
                ? true
                : target.getUUID().equals(this.getPersistentAngerTarget());
        }
    }

    default boolean isAngryAtAllPlayers(Level level) {
        return level.getGameRules().getBoolean(GameRules.RULE_UNIVERSAL_ANGER) && this.isAngry() && this.getPersistentAngerTarget() == null;
    }

    default boolean isAngry() {
        return this.getRemainingPersistentAngerTime() > 0;
    }

    default void playerDied(Player player) {
        if (player.level().getGameRules().getBoolean(GameRules.RULE_FORGIVE_DEAD_PLAYERS)) {
            if (player.getUUID().equals(this.getPersistentAngerTarget())) {
                this.stopBeingAngry();
            }
        }
    }

    default void forgetCurrentTargetAndRefreshUniversalAnger() {
        this.stopBeingAngry();
        this.startPersistentAngerTimer();
    }

    default void stopBeingAngry() {
        this.setLastHurtByMob(null);
        this.setPersistentAngerTarget(null);
        this.setTarget(null);
        this.setRemainingPersistentAngerTime(0);
    }

    @Nullable
    LivingEntity getLastHurtByMob();

    /**
     * Hint to AI tasks that we were attacked by the passed EntityLivingBase and should retaliate. Is not guaranteed to change our actual active target (for example if we are currently busy attacking someone else)
     */
    void setLastHurtByMob(@Nullable LivingEntity var1);

    void setLastHurtByPlayer(@Nullable Player var1);

    /**
     * Sets the active target the Task system uses for tracking
     */
    void setTarget(@Nullable LivingEntity var1);

    boolean canAttack(LivingEntity var1);

    @Nullable
    LivingEntity getTarget();
}
