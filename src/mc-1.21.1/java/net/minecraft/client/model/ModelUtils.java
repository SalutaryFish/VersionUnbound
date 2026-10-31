package net.minecraft.client.model;

public class ModelUtils {
    public static float rotlerpRad(float min, float max, float delta) {
        float f = max - min;

        while (f < (float) -Math.PI) {
            f += (float) (Math.PI * 2);
        }

        while (f >= (float) Math.PI) {
            f -= (float) (Math.PI * 2);
        }

        return min + delta * f;
    }
}
