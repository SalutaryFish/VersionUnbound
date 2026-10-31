package com.mojang.blaze3d.shaders;

public enum FogShape {
    SPHERE(0),
    CYLINDER(1);

    private final int index;

    FogShape(final int index) {
        this.index = index;
    }

    public int getIndex() {
        return this.index;
    }
}
