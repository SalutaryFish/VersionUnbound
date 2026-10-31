package net.minecraft.client.animation;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

public record AnimationChannel(AnimationChannel.Target target, Keyframe... keyframes) {
    public interface Interpolation {
        Vector3f apply(Vector3f var1, float var2, Keyframe[] var3, int var4, int var5, float var6);
    }

    public static class Interpolations {
        public static final AnimationChannel.Interpolation LINEAR = (animationVecCache, keyframeDelta, keyframes, currentKeyframeIdx, nextKeyframeIdx, scale) -> {
            Vector3f vector3f = keyframes[currentKeyframeIdx].target();
            Vector3f vector3f2 = keyframes[nextKeyframeIdx].target();
            return vector3f.lerp(vector3f2, keyframeDelta, animationVecCache).mul(scale);
        };
        public static final AnimationChannel.Interpolation CATMULLROM = (animationVecCache, keyframeDelta, keyframes, currentKeyframeIdx, nextKeyframeIdx, scale) -> {
            Vector3f vector3f = keyframes[Math.max(0, currentKeyframeIdx - 1)].target();
            Vector3f vector3f2 = keyframes[currentKeyframeIdx].target();
            Vector3f vector3f3 = keyframes[nextKeyframeIdx].target();
            Vector3f vector3f4 = keyframes[Math.min(keyframes.length - 1, nextKeyframeIdx + 1)].target();
            animationVecCache.set(
                Mth.catmullrom(keyframeDelta, vector3f.x(), vector3f2.x(), vector3f3.x(), vector3f4.x()) * scale,
                Mth.catmullrom(keyframeDelta, vector3f.y(), vector3f2.y(), vector3f3.y(), vector3f4.y()) * scale,
                Mth.catmullrom(keyframeDelta, vector3f.z(), vector3f2.z(), vector3f3.z(), vector3f4.z()) * scale
            );
            return animationVecCache;
        };
    }

    public interface Target {
        void apply(ModelPart var1, Vector3f var2);
    }

    public static class Targets {
        public static final AnimationChannel.Target POSITION = ModelPart::offsetPos;
        public static final AnimationChannel.Target ROTATION = ModelPart::offsetRotation;
        public static final AnimationChannel.Target SCALE = ModelPart::offsetScale;
    }
}
