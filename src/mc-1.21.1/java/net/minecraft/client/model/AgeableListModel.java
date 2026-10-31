package net.minecraft.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.function.Function;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

public abstract class AgeableListModel<E extends Entity> extends EntityModel<E> {
    private final boolean scaleHead;
    private final float babyYHeadOffset;
    private final float babyZHeadOffset;
    private final float babyHeadScale;
    private final float babyBodyScale;
    private final float bodyYOffset;

    protected AgeableListModel(boolean scaleHead, float babyYHeadOffset, float babyZHeadOffset) {
        this(scaleHead, babyYHeadOffset, babyZHeadOffset, 2.0F, 2.0F, 24.0F);
    }

    protected AgeableListModel(boolean scaleHead, float babyYHeadOffset, float babyZHeadOffset, float babyHeadScale, float babyBodyScale, float bodyYOffset) {
        this(RenderType::entityCutoutNoCull, scaleHead, babyYHeadOffset, babyZHeadOffset, babyHeadScale, babyBodyScale, bodyYOffset);
    }

    protected AgeableListModel(
        Function<ResourceLocation, RenderType> renderType,
        boolean scaleHead,
        float babyYHeadOffset,
        float babyZHeadOffset,
        float babyHeadScale,
        float babyBodyScale,
        float bodyYOffset
    ) {
        super(renderType);
        this.scaleHead = scaleHead;
        this.babyYHeadOffset = babyYHeadOffset;
        this.babyZHeadOffset = babyZHeadOffset;
        this.babyHeadScale = babyHeadScale;
        this.babyBodyScale = babyBodyScale;
        this.bodyYOffset = bodyYOffset;
    }

    protected AgeableListModel() {
        this(false, 5.0F, 2.0F);
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
        if (this.young) {
            poseStack.pushPose();
            if (this.scaleHead) {
                float f = 1.5F / this.babyHeadScale;
                poseStack.scale(f, f, f);
            }

            poseStack.translate(0.0F, this.babyYHeadOffset / 16.0F, this.babyZHeadOffset / 16.0F);
            this.headParts().forEach(modelPart -> modelPart.render(poseStack, buffer, packedLight, packedOverlay, color));
            poseStack.popPose();
            poseStack.pushPose();
            float f = 1.0F / this.babyBodyScale;
            poseStack.scale(f, f, f);
            poseStack.translate(0.0F, this.bodyYOffset / 16.0F, 0.0F);
            this.bodyParts().forEach(modelPart -> modelPart.render(poseStack, buffer, packedLight, packedOverlay, color));
            poseStack.popPose();
        } else {
            this.headParts().forEach(modelPart -> modelPart.render(poseStack, buffer, packedLight, packedOverlay, color));
            this.bodyParts().forEach(modelPart -> modelPart.render(poseStack, buffer, packedLight, packedOverlay, color));
        }
    }

    protected abstract Iterable<ModelPart> headParts();

    protected abstract Iterable<ModelPart> bodyParts();
}
