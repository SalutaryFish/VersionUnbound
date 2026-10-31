package com.mojang.blaze3d.vertex;

import com.mojang.blaze3d.platform.GlStateManager;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import javax.annotation.Nullable;

public record VertexFormatElement(int id, int index, VertexFormatElement.Type type, VertexFormatElement.Usage usage, int count) {
    public static final int MAX_COUNT = 32;
    private static final VertexFormatElement[] BY_ID = new VertexFormatElement[32];
    private static final List<VertexFormatElement> ELEMENTS = new ArrayList<>(32);
    public static final VertexFormatElement POSITION = register(0, 0, VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.POSITION, 3);
    public static final VertexFormatElement COLOR = register(1, 0, VertexFormatElement.Type.UBYTE, VertexFormatElement.Usage.COLOR, 4);
    public static final VertexFormatElement UV0 = register(2, 0, VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.UV, 2);
    public static final VertexFormatElement UV = UV0;
    public static final VertexFormatElement UV1 = register(3, 1, VertexFormatElement.Type.SHORT, VertexFormatElement.Usage.UV, 2);
    public static final VertexFormatElement UV2 = register(4, 2, VertexFormatElement.Type.SHORT, VertexFormatElement.Usage.UV, 2);
    public static final VertexFormatElement NORMAL = register(5, 0, VertexFormatElement.Type.BYTE, VertexFormatElement.Usage.NORMAL, 3);

    public VertexFormatElement {
        if (id < 0 || id >= BY_ID.length) {
            throw new IllegalArgumentException("Element ID must be in range [0; " + BY_ID.length + ")");
        }

        if (!this.supportsUsage(index, usage)) {
            throw new IllegalStateException("Multiple vertex elements of the same type other than UVs are not supported");
        }
    }

    public static VertexFormatElement register(int id, int index, VertexFormatElement.Type type, VertexFormatElement.Usage usage, int count) {
        VertexFormatElement vertexFormatElement = new VertexFormatElement(id, index, type, usage, count);
        if (BY_ID[id] != null) {
            throw new IllegalArgumentException("Duplicate element registration for: " + id);
        }

        BY_ID[id] = vertexFormatElement;
        ELEMENTS.add(vertexFormatElement);
        return vertexFormatElement;
    }

    private boolean supportsUsage(int index, VertexFormatElement.Usage usage) {
        return index == 0 || usage == VertexFormatElement.Usage.UV;
    }

    @Override
    public String toString() {
        return this.count + "," + this.usage + "," + this.type + " (" + this.id + ")";
    }

    public int mask() {
        return 1 << this.id;
    }

    public int byteSize() {
        return this.type.size() * this.count;
    }

    public void setupBufferState(int stateIndex, long offset, int stride) {
        this.usage.setupState.setupBufferState(this.count, this.type.glType(), stride, offset, stateIndex);
    }

    @Nullable
    public static VertexFormatElement byId(int id) {
        return BY_ID[id];
    }

    public static Stream<VertexFormatElement> elementsFromMask(int mask) {
        return ELEMENTS.stream().filter(vertexFormatElement -> vertexFormatElement != null && (mask & vertexFormatElement.mask()) != 0);
    }

    public enum Type {
        FLOAT(4, "Float", 5126),
        UBYTE(1, "Unsigned Byte", 5121),
        BYTE(1, "Byte", 5120),
        USHORT(2, "Unsigned Short", 5123),
        SHORT(2, "Short", 5122),
        UINT(4, "Unsigned Int", 5125),
        INT(4, "Int", 5124);

        private final int size;
        private final String name;
        private final int glType;

        Type(final int size, final String name, final int glType) {
            this.size = size;
            this.name = name;
            this.glType = glType;
        }

        public int size() {
            return this.size;
        }

        public int glType() {
            return this.glType;
        }

        @Override
        public String toString() {
            return this.name;
        }
    }

    public enum Usage {
        POSITION("Position", (size, type, stride, pointer, index) -> GlStateManager._vertexAttribPointer(index, size, type, false, stride, pointer)),
        NORMAL("Normal", (size, type, stride, pointer, index) -> GlStateManager._vertexAttribPointer(index, size, type, true, stride, pointer)),
        COLOR("Vertex Color", (size, type, stride, pointer, index) -> GlStateManager._vertexAttribPointer(index, size, type, true, stride, pointer)),
        UV("UV", (size, type, stride, pointer, index) -> {
            if (type == 5126) {
                GlStateManager._vertexAttribPointer(index, size, type, false, stride, pointer);
            } else {
                GlStateManager._vertexAttribIPointer(index, size, type, stride, pointer);
            }
        }),
        GENERIC("Generic", (size, type, stride, pointer, index) -> GlStateManager._vertexAttribPointer(index, size, type, false, stride, pointer));

        private final String name;
        final VertexFormatElement.Usage.SetupState setupState;

        Usage(final String name, final VertexFormatElement.Usage.SetupState setupState) {
            this.name = name;
            this.setupState = setupState;
        }

        @Override
        public String toString() {
            return this.name;
        }

        @FunctionalInterface
        interface SetupState {
            void setupBufferState(int var1, int var2, int var3, long var4, int var6);
        }
    }
}
