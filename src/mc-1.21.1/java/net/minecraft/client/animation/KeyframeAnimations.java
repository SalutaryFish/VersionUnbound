package net.minecraft.client.animation;

import java.util.List;
import java.util.Optional;
import java.util.Map.Entry;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

public class KeyframeAnimations {
    public static void animate(
        HierarchicalModel<?> model, AnimationDefinition animationDefinition, long accumulatedTime, float scale, Vector3f animationVecCache
    ) {
        float f = getElapsedSeconds(animationDefinition, accumulatedTime);

        for (Entry<String, List<AnimationChannel>> entry : animationDefinition.boneAnimations().entrySet()) {
            Optional<ModelPart> optional = model.getAnyDescendantWithName(entry.getKey());
            List<AnimationChannel> list = entry.getValue();
            optional.ifPresent(modelPart -> list.forEach(animationChannel -> {
                Keyframe[] keyframes = animationChannel.keyframes();
                int i = Math.max(0, Mth.binarySearch(0, keyframes.length, index -> f <= keyframes[index].timestamp()) - 1);
                int j = Math.min(keyframes.length - 1, i + 1);
                Keyframe keyframe = keyframes[i];
                Keyframe keyframe2 = keyframes[j];
                float h = f - keyframe.timestamp();
                float k;
                if (j != i) {
                    k = Mth.clamp(h / (keyframe2.timestamp() - keyframe.timestamp()), 0.0F, 1.0F);
                } else {
                    k = 0.0F;
                }

                keyframe2.interpolation().apply(animationVecCache, k, keyframes, i, j, scale);
                animationChannel.target().apply(modelPart, animationVecCache);
            }));
        }
    }

    private static float getElapsedSeconds(AnimationDefinition animationDefinition, long accumulatedTime) {
        float f = (float)accumulatedTime / 1000.0F;
        return animationDefinition.looping() ? f % animationDefinition.lengthInSeconds() : f;
    }

    public static Vector3f posVec(float x, float y, float z) {
        return new Vector3f(x, -y, z);
    }

    public static Vector3f degreeVec(float xDegrees, float yDegrees, float zDegrees) {
        return new Vector3f(xDegrees * (float) (Math.PI / 180.0), yDegrees * (float) (Math.PI / 180.0), zDegrees * (float) (Math.PI / 180.0));
    }

    public static Vector3f scaleVec(double xScale, double yScale, double zScale) {
        return new Vector3f((float)(xScale - 1.0), (float)(yScale - 1.0), (float)(zScale - 1.0));
    }
}
