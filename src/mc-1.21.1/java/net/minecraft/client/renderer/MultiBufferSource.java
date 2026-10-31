package net.minecraft.client.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.objects.Object2ObjectSortedMaps;
import java.util.HashMap;
import java.util.Map;
import java.util.SequencedMap;
import javax.annotation.Nullable;

public interface MultiBufferSource {
    static MultiBufferSource.BufferSource immediate(ByteBufferBuilder sharedBuffer) {
        return immediateWithBuffers(Object2ObjectSortedMaps.emptyMap(), sharedBuffer);
    }

    static MultiBufferSource.BufferSource immediateWithBuffers(SequencedMap<RenderType, ByteBufferBuilder> fixedBuffers, ByteBufferBuilder sharedBuffer) {
        return new MultiBufferSource.BufferSource(sharedBuffer, fixedBuffers);
    }

    VertexConsumer getBuffer(RenderType var1);

    class BufferSource implements MultiBufferSource {
        protected final ByteBufferBuilder sharedBuffer;
        protected final SequencedMap<RenderType, ByteBufferBuilder> fixedBuffers;
        protected final Map<RenderType, BufferBuilder> startedBuilders = new HashMap<>();
        @Nullable
        protected RenderType lastSharedType;

        protected BufferSource(ByteBufferBuilder sharedBuffer, SequencedMap<RenderType, ByteBufferBuilder> fixedBuffers) {
            this.sharedBuffer = sharedBuffer;
            this.fixedBuffers = fixedBuffers;
        }

        @Override
        public VertexConsumer getBuffer(RenderType renderType) {
            BufferBuilder bufferBuilder = this.startedBuilders.get(renderType);
            if (bufferBuilder != null && !renderType.canConsolidateConsecutiveGeometry()) {
                this.endBatch(renderType, bufferBuilder);
                bufferBuilder = null;
            }

            if (bufferBuilder != null) {
                return bufferBuilder;
            }

            ByteBufferBuilder byteBufferBuilder = this.fixedBuffers.get(renderType);
            if (byteBufferBuilder != null) {
                bufferBuilder = new BufferBuilder(byteBufferBuilder, renderType.mode(), renderType.format());
            } else {
                if (this.lastSharedType != null) {
                    this.endBatch(this.lastSharedType);
                }

                bufferBuilder = new BufferBuilder(this.sharedBuffer, renderType.mode(), renderType.format());
                this.lastSharedType = renderType;
            }

            this.startedBuilders.put(renderType, bufferBuilder);
            return bufferBuilder;
        }

        public void endLastBatch() {
            if (this.lastSharedType != null) {
                this.endBatch(this.lastSharedType);
                this.lastSharedType = null;
            }
        }

        public void endBatch() {
            this.endLastBatch();

            for (RenderType renderType : this.fixedBuffers.keySet()) {
                this.endBatch(renderType);
            }
        }

        public void endBatch(RenderType renderType) {
            BufferBuilder bufferBuilder = this.startedBuilders.remove(renderType);
            if (bufferBuilder != null) {
                this.endBatch(renderType, bufferBuilder);
            }
        }

        private void endBatch(RenderType renderType, BufferBuilder builder) {
            MeshData meshData = builder.build();
            if (meshData != null) {
                if (renderType.sortOnUpload()) {
                    ByteBufferBuilder byteBufferBuilder = this.fixedBuffers.getOrDefault(renderType, this.sharedBuffer);
                    meshData.sortQuads(byteBufferBuilder, RenderSystem.getVertexSorting());
                }

                renderType.draw(meshData);
            }

            if (renderType.equals(this.lastSharedType)) {
                this.lastSharedType = null;
            }
        }
    }
}
