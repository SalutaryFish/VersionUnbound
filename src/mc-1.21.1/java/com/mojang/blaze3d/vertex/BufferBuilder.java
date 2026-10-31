package com.mojang.blaze3d.vertex;

import java.nio.ByteOrder;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import org.lwjgl.system.MemoryUtil;

public class BufferBuilder implements VertexConsumer {
    private static final long NOT_BUILDING = -1L;
    private static final long UNKNOWN_ELEMENT = -1L;
    private static final boolean IS_LITTLE_ENDIAN = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN;
    private final ByteBufferBuilder buffer;
    private long vertexPointer = -1L;
    private int vertices;
    private final VertexFormat format;
    private final VertexFormat.Mode mode;
    private final boolean fastFormat;
    private final boolean fullFormat;
    private final int vertexSize;
    private final int initialElementsToFill;
    private final int[] offsetsByElement;
    private int elementsToFill;
    private boolean building = true;

    public BufferBuilder(ByteBufferBuilder buffer, VertexFormat.Mode mode, VertexFormat format) {
        if (!format.contains(VertexFormatElement.POSITION)) {
            throw new IllegalArgumentException("Cannot build mesh with no position element");
        }

        this.buffer = buffer;
        this.mode = mode;
        this.format = format;
        this.vertexSize = format.getVertexSize();
        this.initialElementsToFill = format.getElementsMask() & ~VertexFormatElement.POSITION.mask();
        this.offsetsByElement = format.getOffsetsByElement();
        boolean bl = format == DefaultVertexFormat.NEW_ENTITY;
        boolean bl2 = format == DefaultVertexFormat.BLOCK;
        this.fastFormat = bl || bl2;
        this.fullFormat = bl;
    }

    @Nullable
    public MeshData build() {
        this.ensureBuilding();
        this.endLastVertex();
        MeshData meshData = this.storeMesh();
        this.building = false;
        this.vertexPointer = -1L;
        return meshData;
    }

    public MeshData buildOrThrow() {
        MeshData meshData = this.build();
        if (meshData == null) {
            throw new IllegalStateException("BufferBuilder was empty");
        } else {
            return meshData;
        }
    }

    private void ensureBuilding() {
        if (!this.building) {
            throw new IllegalStateException("Not building!");
        }
    }

    @Nullable
    private MeshData storeMesh() {
        if (this.vertices == 0) {
            return null;
        }

        ByteBufferBuilder.Result result = this.buffer.build();
        if (result == null) {
            return null;
        }

        int i = this.mode.indexCount(this.vertices);
        VertexFormat.IndexType indexType = VertexFormat.IndexType.least(this.vertices);
        return new MeshData(result, new MeshData.DrawState(this.format, this.vertices, i, this.mode, indexType));
    }

    private long beginVertex() {
        this.ensureBuilding();
        this.endLastVertex();
        this.vertices++;
        long l = this.buffer.reserve(this.vertexSize);
        this.vertexPointer = l;
        return l;
    }

    private long beginElement(VertexFormatElement element) {
        int i = this.elementsToFill;
        int j = i & ~element.mask();
        if (j == i) {
            return -1L;
        } else {
            this.elementsToFill = j;
            long l = this.vertexPointer;
            if (l == -1L) {
                throw new IllegalArgumentException("Not currently building vertex");
            } else {
                return l + this.offsetsByElement[element.id()];
            }
        }
    }

    private void endLastVertex() {
        if (this.vertices != 0) {
            if (this.elementsToFill != 0) {
                String string = VertexFormatElement.elementsFromMask(this.elementsToFill).map(this.format::getElementName).collect(Collectors.joining(", "));
                throw new IllegalStateException("Missing elements in vertex: " + string);
            }

            if (this.mode == VertexFormat.Mode.LINES || this.mode == VertexFormat.Mode.LINE_STRIP) {
                long l = this.buffer.reserve(this.vertexSize);
                MemoryUtil.memCopy(l - this.vertexSize, l, this.vertexSize);
                this.vertices++;
            }
        }
    }

    private static void putRgba(long pointer, int color) {
        int i = FastColor.ABGR32.fromArgb32(color);
        MemoryUtil.memPutInt(pointer, IS_LITTLE_ENDIAN ? i : Integer.reverseBytes(i));
    }

    private static void putPackedUv(long pointer, int packedUv) {
        if (IS_LITTLE_ENDIAN) {
            MemoryUtil.memPutInt(pointer, packedUv);
        } else {
            MemoryUtil.memPutShort(pointer, (short)(packedUv & 65535));
            MemoryUtil.memPutShort(pointer + 2L, (short)(packedUv >> 16 & 65535));
        }
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        long l = this.beginVertex() + this.offsetsByElement[VertexFormatElement.POSITION.id()];
        this.elementsToFill = this.initialElementsToFill;
        MemoryUtil.memPutFloat(l, x);
        MemoryUtil.memPutFloat(l + 4L, y);
        MemoryUtil.memPutFloat(l + 8L, z);
        return this;
    }

    @Override
    public VertexConsumer setColor(int red, int green, int blue, int alpha) {
        long l = this.beginElement(VertexFormatElement.COLOR);
        if (l != -1L) {
            MemoryUtil.memPutByte(l, (byte)red);
            MemoryUtil.memPutByte(l + 1L, (byte)green);
            MemoryUtil.memPutByte(l + 2L, (byte)blue);
            MemoryUtil.memPutByte(l + 3L, (byte)alpha);
        }

        return this;
    }

    @Override
    public VertexConsumer setColor(int color) {
        long l = this.beginElement(VertexFormatElement.COLOR);
        if (l != -1L) {
            putRgba(l, color);
        }

        return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        long l = this.beginElement(VertexFormatElement.UV0);
        if (l != -1L) {
            MemoryUtil.memPutFloat(l, u);
            MemoryUtil.memPutFloat(l + 4L, v);
        }

        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        return this.uvShort((short)u, (short)v, VertexFormatElement.UV1);
    }

    @Override
    public VertexConsumer setOverlay(int packedOverlay) {
        long l = this.beginElement(VertexFormatElement.UV1);
        if (l != -1L) {
            putPackedUv(l, packedOverlay);
        }

        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        return this.uvShort((short)u, (short)v, VertexFormatElement.UV2);
    }

    @Override
    public VertexConsumer setLight(int packedLight) {
        long l = this.beginElement(VertexFormatElement.UV2);
        if (l != -1L) {
            putPackedUv(l, packedLight);
        }

        return this;
    }

    private VertexConsumer uvShort(short u, short v, VertexFormatElement element) {
        long l = this.beginElement(element);
        if (l != -1L) {
            MemoryUtil.memPutShort(l, u);
            MemoryUtil.memPutShort(l + 2L, v);
        }

        return this;
    }

    @Override
    public VertexConsumer setNormal(float normalX, float normalY, float normalZ) {
        long l = this.beginElement(VertexFormatElement.NORMAL);
        if (l != -1L) {
            MemoryUtil.memPutByte(l, normalIntValue(normalX));
            MemoryUtil.memPutByte(l + 1L, normalIntValue(normalY));
            MemoryUtil.memPutByte(l + 2L, normalIntValue(normalZ));
        }

        return this;
    }

    private static byte normalIntValue(float value) {
        return (byte)((int)(Mth.clamp(value, -1.0F, 1.0F) * 127.0F) & 0xFF);
    }

    @Override
    public void addVertex(
        float x, float y, float z, int color, float u, float v, int packedOverlay, int packedLight, float normalX, float normalY, float normalZ
    ) {
        if (this.fastFormat) {
            long l = this.beginVertex();
            MemoryUtil.memPutFloat(l + 0L, x);
            MemoryUtil.memPutFloat(l + 4L, y);
            MemoryUtil.memPutFloat(l + 8L, z);
            putRgba(l + 12L, color);
            MemoryUtil.memPutFloat(l + 16L, u);
            MemoryUtil.memPutFloat(l + 20L, v);
            long m;
            if (this.fullFormat) {
                putPackedUv(l + 24L, packedOverlay);
                m = l + 28L;
            } else {
                m = l + 24L;
            }

            putPackedUv(m + 0L, packedLight);
            MemoryUtil.memPutByte(m + 4L, normalIntValue(normalX));
            MemoryUtil.memPutByte(m + 5L, normalIntValue(normalY));
            MemoryUtil.memPutByte(m + 6L, normalIntValue(normalZ));
        } else {
            VertexConsumer.super.addVertex(x, y, z, color, u, v, packedOverlay, packedLight, normalX, normalY, normalZ);
        }
    }
}
