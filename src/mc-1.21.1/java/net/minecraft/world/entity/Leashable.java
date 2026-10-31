package net.minecraft.world.entity;

import com.mojang.datafixers.util.Either;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.game.ClientboundSetEntityLinkPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.item.Items;

public interface Leashable {
    String LEASH_TAG = "leash";
    double LEASH_TOO_FAR_DIST = 10.0;
    double LEASH_ELASTIC_DIST = 6.0;

    @Nullable
    Leashable.LeashData getLeashData();

    void setLeashData(@Nullable Leashable.LeashData var1);

    default boolean isLeashed() {
        return this.getLeashData() != null && this.getLeashData().leashHolder != null;
    }

    default boolean mayBeLeashed() {
        return this.getLeashData() != null;
    }

    default boolean canHaveALeashAttachedToIt() {
        return this.canBeLeashed() && !this.isLeashed();
    }

    default boolean canBeLeashed() {
        return true;
    }

    default void setDelayedLeashHolderId(int delayedLeashHolderId) {
        this.setLeashData(new Leashable.LeashData(delayedLeashHolderId));
        dropLeash((Entity & Leashable)this, false, false);
    }

    @Nullable
    default Leashable.LeashData readLeashData(CompoundTag tag) {
        if (tag.contains("leash", 10)) {
            return new Leashable.LeashData(Either.left(tag.getCompound("leash").getUUID("UUID")));
        }

        if (tag.contains("leash", 11)) {
            Either<UUID, BlockPos> either = NbtUtils.readBlockPos(tag, "leash").map(Either::right).orElse(null);
            if (either != null) {
                return new Leashable.LeashData(either);
            }
        }

        return null;
    }

    default void writeLeashData(CompoundTag tag, @Nullable Leashable.LeashData leashData) {
        if (leashData != null) {
            Either<UUID, BlockPos> either = leashData.delayedLeashInfo;
            if (leashData.leashHolder instanceof LeashFenceKnotEntity leashFenceKnotEntity) {
                either = Either.right(leashFenceKnotEntity.getPos());
            } else if (leashData.leashHolder != null) {
                either = Either.left(leashData.leashHolder.getUUID());
            }

            if (either != null) {
                tag.put("leash", either.map(uUID -> {
                    CompoundTag compoundTag = new CompoundTag();
                    compoundTag.putUUID("UUID", uUID);
                    return compoundTag;
                }, NbtUtils::writeBlockPos));
            }
        }
    }

    private static <E extends Entity & Leashable> void restoreLeashFromSave(E entity, Leashable.LeashData leashData) {
        if (leashData.delayedLeashInfo != null && entity.level() instanceof ServerLevel serverLevel) {
            Optional<UUID> optional = leashData.delayedLeashInfo.left();
            Optional<BlockPos> optional2 = leashData.delayedLeashInfo.right();
            if (optional.isPresent()) {
                Entity entity2 = serverLevel.getEntity(optional.get());
                if (entity2 != null) {
                    setLeashedTo(entity, entity2, true);
                    return;
                }
            } else if (optional2.isPresent()) {
                setLeashedTo(entity, LeashFenceKnotEntity.getOrCreateKnot(serverLevel, optional2.get()), true);
                return;
            }

            if (entity.tickCount > 100) {
                entity.spawnAtLocation(Items.LEAD);
                entity.setLeashData(null);
            }
        }
    }

    default void dropLeash(boolean broadcastPacket, boolean dropItem) {
        dropLeash((Entity & Leashable)this, broadcastPacket, dropItem);
    }

    private static <E extends Entity & Leashable> void dropLeash(E entity, boolean broadcastPacket, boolean dropItem) {
        Leashable.LeashData leashData = entity.getLeashData();
        if (leashData != null && leashData.leashHolder != null) {
            entity.setLeashData(null);
            if (!entity.level().isClientSide && dropItem) {
                entity.spawnAtLocation(Items.LEAD);
            }

            if (broadcastPacket && entity.level() instanceof ServerLevel serverLevel) {
                serverLevel.getChunkSource().broadcast(entity, new ClientboundSetEntityLinkPacket(entity, null));
            }
        }
    }

    static <E extends Entity & Leashable> void tickLeash(E entity) {
        Leashable.LeashData leashData = entity.getLeashData();
        if (leashData != null && leashData.delayedLeashInfo != null) {
            restoreLeashFromSave(entity, leashData);
        }

        if (leashData != null && leashData.leashHolder != null) {
            if (!entity.isAlive() || !leashData.leashHolder.isAlive()) {
                dropLeash(entity, true, true);
            }

            Entity entity2 = entity.getLeashHolder();
            if (entity2 != null && entity2.level() == entity.level()) {
                float f = entity.distanceTo(entity2);
                if (!entity.handleLeashAtDistance(entity2, f)) {
                    return;
                }

                if (f > 10.0) {
                    entity.leashTooFarBehaviour();
                } else if (f > 6.0) {
                    entity.elasticRangeLeashBehaviour(entity2, f);
                    entity.checkSlowFallDistance();
                } else {
                    entity.closeRangeLeashBehaviour(entity2);
                }
            }
        }
    }

    default boolean handleLeashAtDistance(Entity leashHolder, float distance) {
        return true;
    }

    default void leashTooFarBehaviour() {
        this.dropLeash(true, true);
    }

    default void closeRangeLeashBehaviour(Entity entity) {
    }

    default void elasticRangeLeashBehaviour(Entity leashHolder, float distance) {
        legacyElasticRangeLeashBehaviour((Entity & Leashable)this, leashHolder, distance);
    }

    private static <E extends Entity & Leashable> void legacyElasticRangeLeashBehaviour(E entity, Entity leashHolder, float distance) {
        double d = (leashHolder.getX() - entity.getX()) / distance;
        double e = (leashHolder.getY() - entity.getY()) / distance;
        double f = (leashHolder.getZ() - entity.getZ()) / distance;
        entity.setDeltaMovement(entity.getDeltaMovement().add(Math.copySign(d * d * 0.4, d), Math.copySign(e * e * 0.4, e), Math.copySign(f * f * 0.4, f)));
    }

    default void setLeashedTo(Entity leashHolder, boolean broadcastPacket) {
        setLeashedTo((Entity & Leashable)this, leashHolder, broadcastPacket);
    }

    private static <E extends Entity & Leashable> void setLeashedTo(E entity, Entity leashHolder, boolean broadcastPacket) {
        Leashable.LeashData leashData = entity.getLeashData();
        if (leashData == null) {
            leashData = new Leashable.LeashData(leashHolder);
            entity.setLeashData(leashData);
        } else {
            leashData.setLeashHolder(leashHolder);
        }

        if (broadcastPacket && entity.level() instanceof ServerLevel serverLevel) {
            serverLevel.getChunkSource().broadcast(entity, new ClientboundSetEntityLinkPacket(entity, leashHolder));
        }

        if (entity.isPassenger()) {
            entity.stopRiding();
        }
    }

    @Nullable
    default Entity getLeashHolder() {
        return getLeashHolder((Entity & Leashable)this);
    }

    @Nullable
    private static <E extends Entity & Leashable> Entity getLeashHolder(E entity) {
        Leashable.LeashData leashData = entity.getLeashData();
        if (leashData == null) {
            return null;
        }

        if (leashData.delayedLeashHolderId != 0
            && entity.level().isClientSide
            && entity.level().getEntity(leashData.delayedLeashHolderId) instanceof Entity entity2) {
            leashData.setLeashHolder(entity2);
        }

        return leashData.leashHolder;
    }

    final class LeashData {
        int delayedLeashHolderId;
        @Nullable
        public Entity leashHolder;
        @Nullable
        public Either<UUID, BlockPos> delayedLeashInfo;

        LeashData(Either<UUID, BlockPos> delayedLeashInfo) {
            this.delayedLeashInfo = delayedLeashInfo;
        }

        LeashData(Entity leashHolder) {
            this.leashHolder = leashHolder;
        }

        LeashData(int delayedLeashInfoId) {
            this.delayedLeashHolderId = delayedLeashInfoId;
        }

        public void setLeashHolder(Entity leashHolder) {
            this.leashHolder = leashHolder;
            this.delayedLeashInfo = null;
            this.delayedLeashHolderId = 0;
        }
    }
}
