package net.minecraft.world.entity.ai.behavior;

import java.util.Optional;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;

public final class LongJumpUtil {
    public static Optional<Vec3> calculateJumpVectorForAngle(Mob mob, Vec3 target, float maxJumpVelocity, int angle, boolean requireClearTransition) {
        Vec3 vec3 = mob.position();
        Vec3 vec32 = new Vec3(target.x - vec3.x, 0.0, target.z - vec3.z).normalize().scale(0.5);
        Vec3 vec33 = target.subtract(vec32);
        Vec3 vec34 = vec33.subtract(vec3);
        float f = angle * (float) Math.PI / 180.0F;
        double d = Math.atan2(vec34.z, vec34.x);
        double e = vec34.subtract(0.0, vec34.y, 0.0).lengthSqr();
        double g = Math.sqrt(e);
        double h = vec34.y;
        double i = mob.getGravity();
        double j = Math.sin(2.0F * f);
        double k = Math.pow(Math.cos(f), 2.0);
        double l = Math.sin(f);
        double m = Math.cos(f);
        double n = Math.sin(d);
        double o = Math.cos(d);
        double p = e * i / (g * j - 2.0 * h * k);
        if (p < 0.0) {
            return Optional.empty();
        }

        double q = Math.sqrt(p);
        if (q > maxJumpVelocity) {
            return Optional.empty();
        }

        double r = q * m;
        double s = q * l;
        if (requireClearTransition) {
            int t = Mth.ceil(g / r) * 2;
            double u = 0.0;
            Vec3 vec35 = null;
            EntityDimensions entityDimensions = mob.getDimensions(Pose.LONG_JUMPING);

            for (int v = 0; v < t - 1; v++) {
                u += g / t;
                double w = l / m * u - Math.pow(u, 2.0) * i / (2.0 * p * Math.pow(m, 2.0));
                double x = u * o;
                double y = u * n;
                Vec3 vec36 = new Vec3(vec3.x + x, vec3.y + w, vec3.z + y);
                if (vec35 != null && !isClearTransition(mob, entityDimensions, vec35, vec36)) {
                    return Optional.empty();
                }

                vec35 = vec36;
            }
        }

        return Optional.of(new Vec3(r * o, s, r * n).scale(0.95F));
    }

    private static boolean isClearTransition(Mob mob, EntityDimensions dimensions, Vec3 startPos, Vec3 endPos) {
        Vec3 vec3 = endPos.subtract(startPos);
        double d = Math.min(dimensions.width(), dimensions.height());
        int i = Mth.ceil(vec3.length() / d);
        Vec3 vec32 = vec3.normalize();
        Vec3 vec33 = startPos;

        for (int j = 0; j < i; j++) {
            vec33 = j == i - 1 ? endPos : vec33.add(vec32.scale(d * 0.9F));
            if (!mob.level().noCollision(mob, dimensions.makeBoundingBox(vec33))) {
                return false;
            }
        }

        return true;
    }
}
