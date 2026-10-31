package net.minecraft.client.renderer;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexMultiConsumer;
import java.util.Optional;
import net.minecraft.util.FastColor;

public class OutlineBufferSource implements MultiBufferSource {
    private final MultiBufferSource.BufferSource bufferSource;
    private final MultiBufferSource.BufferSource outlineBufferSource = MultiBufferSource.immediate(new ByteBufferBuilder(1536));
    private int teamR = 255;
    private int teamG = 255;
    private int teamB = 255;
    private int teamA = 255;

    public OutlineBufferSource(MultiBufferSource.BufferSource bufferSource) {
        this.bufferSource = bufferSource;
    }

    @Override
    public VertexConsumer getBuffer(RenderType renderType) {
        if (renderType.isOutline()) {
            VertexConsumer vertexConsumer = this.outlineBufferSource.getBuffer(renderType);
            return new OutlineBufferSource.EntityOutlineGenerator(vertexConsumer, this.teamR, this.teamG, this.teamB, this.teamA);
        } else {
            VertexConsumer vertexConsumer = this.bufferSource.getBuffer(renderType);
            Optional<RenderType> optional = renderType.outline();
            if (optional.isPresent()) {
                VertexConsumer vertexConsumer2 = this.outlineBufferSource.getBuffer(optional.get());
                OutlineBufferSource.EntityOutlineGenerator entityOutlineGenerator = new OutlineBufferSource.EntityOutlineGenerator(
                    vertexConsumer2, this.teamR, this.teamG, this.teamB, this.teamA
                );
                return VertexMultiConsumer.create(entityOutlineGenerator, vertexConsumer);
            } else {
                return vertexConsumer;
            }
        }
    }

    public void setColor(int red, int green, int blue, int alpha) {
        this.teamR = red;
        this.teamG = green;
        this.teamB = blue;
        this.teamA = alpha;
    }

    public void endOutlineBatch() {
        this.outlineBufferSource.endBatch();
    }

    record EntityOutlineGenerator(VertexConsumer delegate, int color) implements VertexConsumer {
        public EntityOutlineGenerator(VertexConsumer delegate, int defaultR, int defaultG, int defaultB, int defaultA) {
            this(delegate, FastColor.ARGB32.color(defaultA, defaultR, defaultG, defaultB));
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            this.delegate.addVertex(x, y, z).setColor(this.color);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            this.delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float normalX, float normalY, float normalZ) {
            return this;
        }
    }
}
