package com.mojang.blaze3d.vertex;

import com.google.common.primitives.Floats;
import it.unimi.dsi.fastutil.ints.IntArrays;
import org.joml.Vector3f;

public interface VertexSorting {
    VertexSorting DISTANCE_TO_ORIGIN = byDistance(0.0F, 0.0F, 0.0F);
    VertexSorting ORTHOGRAPHIC_Z = byDistance(vector3f -> -vector3f.z());

    static VertexSorting byDistance(float x, float y, float z) {
        return byDistance(new Vector3f(x, y, z));
    }

    static VertexSorting byDistance(Vector3f vector) {
        return byDistance(vector::distanceSquared);
    }

    static VertexSorting byDistance(VertexSorting.DistanceFunction distanceFunction) {
        return vector3fs -> {
            float[] fs = new float[vector3fs.length];
            int[] is = new int[vector3fs.length];

            for (int i = 0; i < vector3fs.length; is[i] = i++) {
                fs[i] = distanceFunction.apply(vector3fs[i]);
            }

            IntArrays.mergeSort(is, (ix, j) -> Floats.compare(fs[j], fs[ix]));
            return is;
        };
    }

    int[] sort(Vector3f[] var1);

    interface DistanceFunction {
        float apply(Vector3f var1);
    }
}
