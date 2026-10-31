package net.minecraft.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.function.Function;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

public abstract class Model {
    protected final Function<ResourceLocation, RenderType> renderType;

    public Model(Function<ResourceLocation, RenderType> renderType) {
        this.renderType = renderType;
    }

    public final RenderType renderType(ResourceLocation location) {
        return this.renderType.apply(location);
    }

    public abstract void renderToBuffer(PoseStack var1, VertexConsumer var2, int var3, int var4, int var5);

    public final void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay) {
        this.renderToBuffer(poseStack, vertexConsumer, packedLight, packedOverlay, -1);
    }
}
