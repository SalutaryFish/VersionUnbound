package com.mojang.math;

import java.util.Arrays;
import net.minecraft.Util;
import org.joml.Matrix3f;

/**
 * The symmetric group S3, also known as all the permutation orders of three elements.
 */
public enum SymmetricGroup3 {
    P123(0, 1, 2),
    P213(1, 0, 2),
    P132(0, 2, 1),
    P231(1, 2, 0),
    P312(2, 0, 1),
    P321(2, 1, 0);

    private final int[] permutation;
    private final Matrix3f transformation;
    private static final int ORDER = 3;
    private static final SymmetricGroup3[][] cayleyTable = Util.make(new SymmetricGroup3[values().length][values().length], groups -> {
        for (SymmetricGroup3 symmetricGroup3 : values()) {
            for (SymmetricGroup3 symmetricGroup32 : values()) {
                int[] is = new int[3];

                for (int i = 0; i < 3; i++) {
                    is[i] = symmetricGroup3.permutation[symmetricGroup32.permutation[i]];
                }

                SymmetricGroup3 symmetricGroup33 = Arrays.stream(values()).filter(group -> Arrays.equals(group.permutation, is)).findFirst().get();
                groups[symmetricGroup3.ordinal()][symmetricGroup32.ordinal()] = symmetricGroup33;
            }
        }
    });

    SymmetricGroup3(final int first, final int second, final int third) {
        this.permutation = new int[]{first, second, third};
        this.transformation = new Matrix3f();
        this.transformation.set(this.permutation(0), 0, 1.0F);
        this.transformation.set(this.permutation(1), 1, 1.0F);
        this.transformation.set(this.permutation(2), 2, 1.0F);
    }

    public SymmetricGroup3 compose(SymmetricGroup3 other) {
        return cayleyTable[this.ordinal()][other.ordinal()];
    }

    public int permutation(int element) {
        return this.permutation[element];
    }

    public Matrix3f transformation() {
        return this.transformation;
    }
}
