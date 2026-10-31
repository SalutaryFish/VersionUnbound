package net.minecraft.client.renderer;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import java.util.SequencedMap;
import net.minecraft.Util;
import net.minecraft.client.resources.model.ModelBakery;

public class RenderBuffers {
    private final SectionBufferBuilderPack fixedBufferPack = new SectionBufferBuilderPack();
    private final SectionBufferBuilderPool sectionBufferPool;
    private final MultiBufferSource.BufferSource bufferSource;
    private final MultiBufferSource.BufferSource crumblingBufferSource;
    private final OutlineBufferSource outlineBufferSource;

    public RenderBuffers(int bufferCount) {
        this.sectionBufferPool = SectionBufferBuilderPool.allocate(bufferCount);
        SequencedMap<RenderType, ByteBufferBuilder> sequencedMap = Util.make(new Object2ObjectLinkedOpenHashMap<>(), fixedBuffersMap -> {
            fixedBuffersMap.put(Sheets.solidBlockSheet(), this.fixedBufferPack.buffer(RenderType.solid()));
            fixedBuffersMap.put(Sheets.cutoutBlockSheet(), this.fixedBufferPack.buffer(RenderType.cutout()));
            fixedBuffersMap.put(Sheets.bannerSheet(), this.fixedBufferPack.buffer(RenderType.cutoutMipped()));
            fixedBuffersMap.put(Sheets.translucentCullBlockSheet(), this.fixedBufferPack.buffer(RenderType.translucent()));
            put((Object2ObjectLinkedOpenHashMap<RenderType, ByteBufferBuilder>)fixedBuffersMap, Sheets.shieldSheet());
            put((Object2ObjectLinkedOpenHashMap<RenderType, ByteBufferBuilder>)fixedBuffersMap, Sheets.bedSheet());
            put((Object2ObjectLinkedOpenHashMap<RenderType, ByteBufferBuilder>)fixedBuffersMap, Sheets.shulkerBoxSheet());
            put((Object2ObjectLinkedOpenHashMap<RenderType, ByteBufferBuilder>)fixedBuffersMap, Sheets.signSheet());
            put((Object2ObjectLinkedOpenHashMap<RenderType, ByteBufferBuilder>)fixedBuffersMap, Sheets.hangingSignSheet());
            fixedBuffersMap.put(Sheets.chestSheet(), new ByteBufferBuilder(786432));
            put((Object2ObjectLinkedOpenHashMap<RenderType, ByteBufferBuilder>)fixedBuffersMap, RenderType.armorEntityGlint());
            put((Object2ObjectLinkedOpenHashMap<RenderType, ByteBufferBuilder>)fixedBuffersMap, RenderType.glint());
            put((Object2ObjectLinkedOpenHashMap<RenderType, ByteBufferBuilder>)fixedBuffersMap, RenderType.glintTranslucent());
            put((Object2ObjectLinkedOpenHashMap<RenderType, ByteBufferBuilder>)fixedBuffersMap, RenderType.entityGlint());
            put((Object2ObjectLinkedOpenHashMap<RenderType, ByteBufferBuilder>)fixedBuffersMap, RenderType.entityGlintDirect());
            put((Object2ObjectLinkedOpenHashMap<RenderType, ByteBufferBuilder>)fixedBuffersMap, RenderType.waterMask());
            ModelBakery.DESTROY_TYPES.forEach(renderType -> put(fixedBuffersMap, renderType));
        });
        this.crumblingBufferSource = MultiBufferSource.immediate(new ByteBufferBuilder(1536));
        this.bufferSource = MultiBufferSource.immediateWithBuffers(sequencedMap, new ByteBufferBuilder(786432));
        this.outlineBufferSource = new OutlineBufferSource(this.bufferSource);
    }

    private static void put(Object2ObjectLinkedOpenHashMap<RenderType, ByteBufferBuilder> mapBuilders, RenderType renderType) {
        mapBuilders.put(renderType, new ByteBufferBuilder(renderType.bufferSize()));
    }

    public SectionBufferBuilderPack fixedBufferPack() {
        return this.fixedBufferPack;
    }

    public SectionBufferBuilderPool sectionBufferPool() {
        return this.sectionBufferPool;
    }

    public MultiBufferSource.BufferSource bufferSource() {
        return this.bufferSource;
    }

    public MultiBufferSource.BufferSource crumblingBufferSource() {
        return this.crumblingBufferSource;
    }

    public OutlineBufferSource outlineBufferSource() {
        return this.outlineBufferSource;
    }
}
