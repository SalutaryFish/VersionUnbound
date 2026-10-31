package net.minecraft.world.entity.ai.control;

import java.util.Optional;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

public class LookControl implements Control {
    protected final Mob mob;
    protected float yMaxRotSpeed;
    protected float xMaxRotAngle;
    protected int lookAtCooldown;
    protected double wantedX;
    protected double wantedY;
    protected double wantedZ;

    public LookControl(Mob mob) {
        this.mob = mob;
    }

    /**
     * Sets the mob's look vector
     */
    public void setLookAt(Vec3 lookVector) {
        this.setLookAt(lookVector.x, lookVector.y, lookVector.z);
    }

    /**
     * Sets the controlling mob's look vector to the provided entity's location
     */
    public void setLookAt(Entity entity) {
        this.setLookAt(entity.getX(), getWantedY(entity), entity.getZ());
    }

    /**
     * Sets position to look at using entity
     */
    public void setLookAt(Entity entity, float deltaYaw, float deltaPitch) {
        this.setLookAt(entity.getX(), getWantedY(entity), entity.getZ(), deltaYaw, deltaPitch);
    }

    public void setLookAt(double x, double y, double z) {
        this.setLookAt(x, y, z, this.mob.getHeadRotSpeed(), this.mob.getMaxHeadXRot());
    }

    /**
     * Sets position to look at
     */
    public void setLookAt(double x, double y, double z, float deltaYaw, float deltaPitch) {
        this.wantedX = x;
        this.wantedY = y;
        this.wantedZ = z;
        this.yMaxRotSpeed = deltaYaw;
        this.xMaxRotAngle = deltaPitch;
        this.lookAtCooldown = 2;
    }

    public void tick() {
        if (this.resetXRotOnTick()) {
            this.mob.setXRot(0.0F);
        }

        if (this.lookAtCooldown > 0) {
            this.lookAtCooldown--;
            this.getYRotD().ifPresent(rotationWanted -> this.mob.yHeadRot = this.rotateTowards(this.mob.yHeadRot, rotationWanted, this.yMaxRotSpeed));
            this.getXRotD().ifPresent(float_ -> this.mob.setXRot(this.rotateTowards(this.mob.getXRot(), float_, this.xMaxRotAngle)));
        } else {
            this.mob.yHeadRot = this.rotateTowards(this.mob.yHeadRot, this.mob.yBodyRot, 10.0F);
        }

        this.clampHeadRotationToBody();
    }

    protected void clampHeadRotationToBody() {
        if (!this.mob.getNavigation().isDone()) {
            this.mob.yHeadRot = Mth.rotateIfNecessary(this.mob.yHeadRot, this.mob.yBodyRot, this.mob.getMaxHeadYRot());
        }
    }

    protected boolean resetXRotOnTick() {
        return true;
    }

    public boolean isLookingAtTarget() {
        return this.lookAtCooldown > 0;
    }

    public double getWantedX() {
        return this.wantedX;
    }

    public double getWantedY() {
        return this.wantedY;
    }

    public double getWantedZ() {
        return this.wantedZ;
    }

    protected Optional<Float> getXRotD() {
        double d = this.wantedX - this.mob.getX();
        double e = this.wantedY - this.mob.getEyeY();
        double f = this.wantedZ - this.mob.getZ();
        double g = Math.sqrt(d * d + f * f);
        return !(Math.abs(e) > 1.0E-5F) && !(Math.abs(g) > 1.0E-5F) ? Optional.empty() : Optional.of((float)(-(Mth.atan2(e, g) * 180.0F / (float)Math.PI)));
    }

    protected Optional<Float> getYRotD() {
        double d = this.wantedX - this.mob.getX();
        double e = this.wantedZ - this.mob.getZ();
        return !(Math.abs(e) > 1.0E-5F) && !(Math.abs(d) > 1.0E-5F)
            ? Optional.empty()
            : Optional.of((float)(Mth.atan2(e, d) * 180.0F / (float)Math.PI) - 90.0F);
    }

    /**
     * Rotate as much as possible from {@code from} to {@code to} within the bounds of {@code maxDelta}
     */
    protected float rotateTowards(float from, float to, float maxDelta) {
        float f = Mth.degreesDifference(from, to);
        float g = Mth.clamp(f, -maxDelta, maxDelta);
        return from + g;
    }

    private static double getWantedY(Entity entity) {
        return entity instanceof LivingEntity ? entity.getEyeY() : (entity.getBoundingBox().minY + entity.getBoundingBox().maxY) / 2.0;
    }
}
