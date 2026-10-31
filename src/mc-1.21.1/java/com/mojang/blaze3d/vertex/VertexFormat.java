package com.mojang.blaze3d.vertex;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nullable;

public class VertexFormat {
    public static final int UNKNOWN_ELEMENT = -1;
    private final List<VertexFormatElement> elements;
    private final List<String> names;
    private final int vertexSize;
    private final int elementsMask;
    private final int[] offsetsByElement = new int[32];
    @Nullable
    private VertexBuffer immediateDrawVertexBuffer;

    VertexFormat(List<VertexFormatElement> elements, List<String> names, IntList offsets, int vertexSize) {
        this.elements = elements;
        this.names = names;
        this.vertexSize = vertexSize;
        this.elementsMask = elements.stream().mapToInt(VertexFormatElement::mask).reduce(0, (ix, jx) -> ix | jx);

        for (int i = 0; i < this.offsetsByElement.length; i++) {
            VertexFormatElement vertexFormatElement = VertexFormatElement.byId(i);
            int j = vertexFormatElement != null ? elements.indexOf(vertexFormatElement) : -1;
            this.offsetsByElement[i] = j != -1 ? offsets.getInt(j) : -1;
        }
    }

    public static VertexFormat.Builder builder() {
        return new VertexFormat.Builder();
    }

    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder("Vertex format (").append(this.vertexSize).append(" bytes):\n");

        for (int i = 0; i < this.elements.size(); i++) {
            VertexFormatElement vertexFormatElement = this.elements.get(i);
            stringBuilder.append(i)
                .append(". ")
                .append(this.names.get(i))
                .append(": ")
                .append(vertexFormatElement)
                .append(" @ ")
                .append(this.getOffset(vertexFormatElement))
                .append('\n');
        }

        return stringBuilder.toString();
    }

    public int getVertexSize() {
        return this.vertexSize;
    }

    public List<VertexFormatElement> getElements() {
        return this.elements;
    }

    public List<String> getElementAttributeNames() {
        return this.names;
    }

    public int[] getOffsetsByElement() {
        return this.offsetsByElement;
    }

    public int getOffset(VertexFormatElement element) {
        return this.offsetsByElement[element.id()];
    }

    public boolean contains(VertexFormatElement element) {
        return (this.elementsMask & element.mask()) != 0;
    }

    public int getElementsMask() {
        return this.elementsMask;
    }

    public String getElementName(VertexFormatElement element) {
        int i = this.elements.indexOf(element);
        if (i == -1) {
            throw new IllegalArgumentException(element + " is not contained in format");
        } else {
            return this.names.get(i);
        }
    }

    @Override
    public boolean equals(Object other) {
        return this == other
            ? true
            : other instanceof VertexFormat vertexFormat
                && this.elementsMask == vertexFormat.elementsMask
                && this.vertexSize == vertexFormat.vertexSize
                && this.names.equals(vertexFormat.names)
                && Arrays.equals(this.offsetsByElement, vertexFormat.offsetsByElement);
    }

    @Override
    public int hashCode() {
        return this.elementsMask * 31 + Arrays.hashCode(this.offsetsByElement);
    }

    public void setupBufferState() {
        if (!RenderSystem.isOnRenderThread()) {
            RenderSystem.recordRenderCall(this::_setupBufferState);
        } else {
            this._setupBufferState();
        }
    }

    private void _setupBufferState() {
        int i = this.getVertexSize();

        for (int j = 0; j < this.elements.size(); j++) {
            GlStateManager._enableVertexAttribArray(j);
            VertexFormatElement vertexFormatElement = this.elements.get(j);
            vertexFormatElement.setupBufferState(j, this.getOffset(vertexFormatElement), i);
        }
    }

    public void clearBufferState() {
        if (!RenderSystem.isOnRenderThread()) {
            RenderSystem.recordRenderCall(this::_clearBufferState);
        } else {
            this._clearBufferState();
        }
    }

    private void _clearBufferState() {
        for (int i = 0; i < this.elements.size(); i++) {
            GlStateManager._disableVertexAttribArray(i);
        }
    }

    public VertexBuffer getImmediateDrawVertexBuffer() {
        VertexBuffer vertexBuffer = this.immediateDrawVertexBuffer;
        if (vertexBuffer == null) {
            this.immediateDrawVertexBuffer = vertexBuffer = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
        }

        return vertexBuffer;
    }

    public static class Builder {
        private final ImmutableMap.Builder<String, VertexFormatElement> elements = ImmutableMap.builder();
        private final IntList offsets = new IntArrayList();
        private int offset;

        Builder() {
        }

        public VertexFormat.Builder add(String name, VertexFormatElement element) {
            this.elements.put(name, element);
            this.offsets.add(this.offset);
            this.offset = this.offset + element.byteSize();
            return this;
        }

        public VertexFormat.Builder padding(int padding) {
            this.offset += padding;
            return this;
        }

        public VertexFormat build() {
            ImmutableMap<String, VertexFormatElement> immutableMap = this.elements.buildOrThrow();
            ImmutableList<VertexFormatElement> immutableList = immutableMap.values().asList();
            ImmutableList<String> immutableList2 = immutableMap.keySet().asList();
            return new VertexFormat(immutableList, immutableList2, this.offsets, this.offset);
        }
    }

    public enum IndexType {
        SHORT(5123, 2),
        INT(5125, 4);

        public final int asGLType;
        public final int bytes;

        IndexType(final int asGLType, final int bytes) {
            this.asGLType = asGLType;
            this.bytes = bytes;
        }

        public static VertexFormat.IndexType least(int indexCount) {
            return (indexCount & -65536) != 0 ? INT : SHORT;
        }
    }

    public enum Mode {
        LINES(4, 2, 2, false),
        LINE_STRIP(5, 2, 1, true),
        DEBUG_LINES(1, 2, 2, false),
        DEBUG_LINE_STRIP(3, 2, 1, true),
        TRIANGLES(4, 3, 3, false),
        TRIANGLE_STRIP(5, 3, 1, true),
        TRIANGLE_FAN(6, 3, 1, true),
        QUADS(4, 4, 4, false);

        public final int asGLMode;
        public final int primitiveLength;
        public final int primitiveStride;
        public final boolean connectedPrimitives;

        Mode(final int asGLMode, final int primitiveLength, final int primitiveStride, final boolean connectedPrimitives) {
            this.asGLMode = asGLMode;
            this.primitiveLength = primitiveLength;
            this.primitiveStride = primitiveStride;
            this.connectedPrimitives = connectedPrimitives;
        }

        public int indexCount(int vertices) {
            return switch (this) {
                case LINES, QUADS -> vertices / 4 * 6;
                case LINE_STRIP, DEBUG_LINES, DEBUG_LINE_STRIP, TRIANGLES, TRIANGLE_STRIP, TRIANGLE_FAN -> vertices;
                default -> 0;
            };
        }
    }
}
