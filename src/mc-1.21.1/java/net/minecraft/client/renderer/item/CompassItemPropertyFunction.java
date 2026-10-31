package net.minecraft.client.renderer.item;

import javax.annotation.Nullable;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public class CompassItemPropertyFunction implements ClampedItemPropertyFunction {
    public static final int DEFAULT_ROTATION = 0;
    private final CompassItemPropertyFunction.CompassWobble wobble = new CompassItemPropertyFunction.CompassWobble();
    private final CompassItemPropertyFunction.CompassWobble wobbleRandom = new CompassItemPropertyFunction.CompassWobble();
    public final CompassItemPropertyFunction.CompassTarget compassTarget;

    public CompassItemPropertyFunction(CompassItemPropertyFunction.CompassTarget compassTarget) {
        this.compassTarget = compassTarget;
    }

    @Override
    public float unclampedCall(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity, int seed) {
        Entity entity2 = entity != null ? entity : stack.getEntityRepresentation();
        if (entity2 == null) {
            return 0.0F;
        }

        level = this.tryFetchLevelIfMissing(entity2, level);
        return level == null ? 0.0F : this.getCompassRotation(stack, level, seed, entity2);
    }

    private float getCompassRotation(ItemStack stack, ClientLevel level, int seed, Entity entity) {
        GlobalPos globalPos = this.compassTarget.getPos(level, stack, entity);
        long l = level.getGameTime();
        return !this.isValidCompassTargetPos(entity, globalPos)
            ? this.getRandomlySpinningRotation(seed, l)
            : this.getRotationTowardsCompassTarget(entity, l, globalPos.pos());
    }

    private float getRandomlySpinningRotation(int seed, long ticks) {
        if (this.wobbleRandom.shouldUpdate(ticks)) {
            this.wobbleRandom.update(ticks, Math.random());
        }

        double d = this.wobbleRandom.rotation + this.hash(seed) / 2.1474836E9F;
        return Mth.positiveModulo((float)d, 1.0F);
    }

    private float getRotationTowardsCompassTarget(Entity entity, long ticks, BlockPos pos) {
        double d = this.getAngleFromEntityToPos(entity, pos);
        double e = this.getWrappedVisualRotationY(entity);
        double f;
        if (entity instanceof Player player && player.isLocalPlayer() && player.level().tickRateManager().runsNormally()) {
            if (this.wobble.shouldUpdate(ticks)) {
                this.wobble.update(ticks, 0.5 - (e - 0.25));
            }

            f = d + this.wobble.rotation;
        } else {
            f = 0.5 - (e - 0.25 - d);
        }

        return Mth.positiveModulo((float)f, 1.0F);
    }

    @Nullable
    private ClientLevel tryFetchLevelIfMissing(Entity entity, @Nullable ClientLevel level) {
        return level == null && entity.level() instanceof ClientLevel ? (ClientLevel)entity.level() : level;
    }

    private boolean isValidCompassTargetPos(Entity entity, @Nullable GlobalPos pos) {
        return pos != null && pos.dimension() == entity.level().dimension() && !(pos.pos().distToCenterSqr(entity.position()) < 1.0E-5F);
    }

    private double getAngleFromEntityToPos(Entity entity, BlockPos pos) {
        Vec3 vec3 = Vec3.atCenterOf(pos);
        return Math.atan2(vec3.z() - entity.getZ(), vec3.x() - entity.getX()) / (float) (Math.PI * 2);
    }

    private double getWrappedVisualRotationY(Entity entity) {
        return Mth.positiveModulo(entity.getVisualRotationYInDegrees() / 360.0F, 1.0);
    }

    private int hash(int value) {
        return value * 1327217883;
    }

    public interface CompassTarget {
        @Nullable
        GlobalPos getPos(ClientLevel var1, ItemStack var2, Entity var3);
    }

    static class CompassWobble {
        double rotation;
        private double deltaRotation;
        private long lastUpdateTick;

        boolean shouldUpdate(long ticks) {
            return this.lastUpdateTick != ticks;
        }

        void update(long ticks, double rotation) {
            this.lastUpdateTick = ticks;
            double d = rotation - this.rotation;
            d = Mth.positiveModulo(d + 0.5, 1.0) - 0.5;
            this.deltaRotation += d * 0.1;
            this.deltaRotation *= 0.8;
            this.rotation = Mth.positiveModulo(this.rotation + this.deltaRotation, 1.0);
        }
    }
}
