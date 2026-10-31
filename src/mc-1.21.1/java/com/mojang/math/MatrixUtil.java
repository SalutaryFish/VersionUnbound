package com.mojang.math;

import org.apache.commons.lang3.tuple.Triple;
import org.joml.Math;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class MatrixUtil {
    private static final float G = 3.0F + 2.0F * Math.sqrt(2.0F);
    private static final GivensParameters PI_4 = GivensParameters.fromPositiveAngle((float) (java.lang.Math.PI / 4));

    private MatrixUtil() {
    }

    public static Matrix4f mulComponentWise(Matrix4f matrix, float scalar) {
        return matrix.set(
            matrix.m00() * scalar,
            matrix.m01() * scalar,
            matrix.m02() * scalar,
            matrix.m03() * scalar,
            matrix.m10() * scalar,
            matrix.m11() * scalar,
            matrix.m12() * scalar,
            matrix.m13() * scalar,
            matrix.m20() * scalar,
            matrix.m21() * scalar,
            matrix.m22() * scalar,
            matrix.m23() * scalar,
            matrix.m30() * scalar,
            matrix.m31() * scalar,
            matrix.m32() * scalar,
            matrix.m33() * scalar
        );
    }

    private static GivensParameters approxGivensQuat(float f, float g, float h) {
        float i = 2.0F * (f - h);
        float j = g;
        return G * j * j < i * i ? GivensParameters.fromUnnormalized(j, i) : PI_4;
    }

    private static GivensParameters qrGivensQuat(float input1, float input2) {
        float f = (float)java.lang.Math.hypot(input1, input2);
        float g = f > 1.0E-6F ? input2 : 0.0F;
        float h = Math.abs(input1) + Math.max(f, 1.0E-6F);
        if (input1 < 0.0F) {
            float i = g;
            g = h;
            h = i;
        }

        return GivensParameters.fromUnnormalized(g, h);
    }

    private static void similarityTransform(Matrix3f input, Matrix3f tempStorage) {
        input.mul(tempStorage);
        tempStorage.transpose();
        tempStorage.mul(input);
        input.set(tempStorage);
    }

    private static void stepJacobi(Matrix3f input, Matrix3f tempStorage, Quaternionf resultEigenvector, Quaternionf resultEigenvalue) {
        if (input.m01 * input.m01 + input.m10 * input.m10 > 1.0E-6F) {
            GivensParameters givensParameters = approxGivensQuat(input.m00, 0.5F * (input.m01 + input.m10), input.m11);
            Quaternionf quaternionf = givensParameters.aroundZ(resultEigenvector);
            resultEigenvalue.mul(quaternionf);
            givensParameters.aroundZ(tempStorage);
            similarityTransform(input, tempStorage);
        }

        if (input.m02 * input.m02 + input.m20 * input.m20 > 1.0E-6F) {
            GivensParameters givensParameters = approxGivensQuat(input.m00, 0.5F * (input.m02 + input.m20), input.m22).inverse();
            Quaternionf quaternionf = givensParameters.aroundY(resultEigenvector);
            resultEigenvalue.mul(quaternionf);
            givensParameters.aroundY(tempStorage);
            similarityTransform(input, tempStorage);
        }

        if (input.m12 * input.m12 + input.m21 * input.m21 > 1.0E-6F) {
            GivensParameters givensParameters = approxGivensQuat(input.m11, 0.5F * (input.m12 + input.m21), input.m22);
            Quaternionf quaternionf = givensParameters.aroundX(resultEigenvector);
            resultEigenvalue.mul(quaternionf);
            givensParameters.aroundX(tempStorage);
            similarityTransform(input, tempStorage);
        }
    }

    public static Quaternionf eigenvalueJacobi(Matrix3f input, int iterations) {
        Quaternionf quaternionf = new Quaternionf();
        Matrix3f matrix3f = new Matrix3f();
        Quaternionf quaternionf2 = new Quaternionf();

        for (int i = 0; i < iterations; i++) {
            stepJacobi(input, matrix3f, quaternionf2, quaternionf);
        }

        quaternionf.normalize();
        return quaternionf;
    }

    public static Triple<Quaternionf, Vector3f, Quaternionf> svdDecompose(Matrix3f matrix) {
        Matrix3f matrix3f = new Matrix3f(matrix);
        matrix3f.transpose();
        matrix3f.mul(matrix);
        Quaternionf quaternionf = eigenvalueJacobi(matrix3f, 5);
        float f = matrix3f.m00;
        float g = matrix3f.m11;
        boolean bl = f < 1.0E-6;
        boolean bl2 = g < 1.0E-6;
        Matrix3f matrix3f2 = matrix3f;
        Matrix3f matrix3f3 = matrix.rotate(quaternionf);
        Quaternionf quaternionf2 = new Quaternionf();
        Quaternionf quaternionf3 = new Quaternionf();
        GivensParameters givensParameters;
        if (bl) {
            givensParameters = qrGivensQuat(matrix3f3.m11, -matrix3f3.m10);
        } else {
            givensParameters = qrGivensQuat(matrix3f3.m00, matrix3f3.m01);
        }

        Quaternionf quaternionf4 = givensParameters.aroundZ(quaternionf3);
        Matrix3f matrix3f4 = givensParameters.aroundZ(matrix3f2);
        quaternionf2.mul(quaternionf4);
        matrix3f4.transpose().mul(matrix3f3);
        matrix3f2 = matrix3f3;
        if (bl) {
            givensParameters = qrGivensQuat(matrix3f4.m22, -matrix3f4.m20);
        } else {
            givensParameters = qrGivensQuat(matrix3f4.m00, matrix3f4.m02);
        }

        givensParameters = givensParameters.inverse();
        Quaternionf quaternionf5 = givensParameters.aroundY(quaternionf3);
        Matrix3f matrix3f5 = givensParameters.aroundY(matrix3f2);
        quaternionf2.mul(quaternionf5);
        matrix3f5.transpose().mul(matrix3f4);
        matrix3f2 = matrix3f4;
        if (bl2) {
            givensParameters = qrGivensQuat(matrix3f5.m22, -matrix3f5.m21);
        } else {
            givensParameters = qrGivensQuat(matrix3f5.m11, matrix3f5.m12);
        }

        Quaternionf quaternionf6 = givensParameters.aroundX(quaternionf3);
        Matrix3f matrix3f6 = givensParameters.aroundX(matrix3f2);
        quaternionf2.mul(quaternionf6);
        matrix3f6.transpose().mul(matrix3f5);
        Vector3f vector3f = new Vector3f(matrix3f6.m00, matrix3f6.m11, matrix3f6.m22);
        return Triple.of(quaternionf2, vector3f, quaternionf.conjugate());
    }

    public static boolean isPureTranslation(Matrix4f matrix) {
        return (matrix.properties() & 8) != 0;
    }

    public static boolean isOrthonormal(Matrix4f matrix) {
        return (matrix.properties() & 16) != 0;
    }
}
