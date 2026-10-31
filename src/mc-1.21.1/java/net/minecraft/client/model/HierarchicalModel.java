package net.minecraft.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.KeyframeAnimations;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import org.joml.Vector3f;

public abstract class HierarchicalModel<E extends Entity> extends EntityModel<E> {
    private static final Vector3f ANIMATION_VECTOR_CACHE = new Vector3f();

    public HierarchicalModel() {
        this(RenderType::entityCutoutNoCull);
    }

    public HierarchicalModel(Function<ResourceLocation, RenderType> renderType) {
        super(renderType);
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
        this.root().render(poseStack, buffer, packedLight, packedOverlay, color);
    }

    public abstract ModelPart root();

    public Optional<ModelPart> getAnyDescendantWithName(String name) {
        return name.equals("root")
            ? Optional.of(this.root())
            : this.root().getAllParts().filter(modelPart -> modelPart.hasChild(name)).findFirst().map(modelPart -> modelPart.getChild(name));
    }

    protected void animate(AnimationState animationState, AnimationDefinition animationDefinition, float ageInTicks) {
        this.animate(animationState, animationDefinition, ageInTicks, 1.0F);
    }

    protected void animateWalk(
        AnimationDefinition animationDefinition, float limbSwing, float limbSwingAmount, float maxAnimationSpeed, float animationScaleFactor
    ) {
        long l = (long)(limbSwing * 50.0F * maxAnimationSpeed);
        float f = Math.min(limbSwingAmount * animationScaleFactor, 1.0F);
        KeyframeAnimations.animate(this, animationDefinition, l, f, ANIMATION_VECTOR_CACHE);
    }

    protected void animate(AnimationState animationState, AnimationDefinition animationDefinition, float ageInTicks, float speed) {
        animationState.updateTime(ageInTicks, speed);
        animationState.ifStarted(
            animationStatex -> KeyframeAnimations.animate(this, animationDefinition, animationStatex.getAccumulatedTime(), 1.0F, ANIMATION_VECTOR_CACHE)
        );
    }

    protected void applyStatic(AnimationDefinition animationDefinition) {
        KeyframeAnimations.animate(this, animationDefinition, 0L, 1.0F, ANIMATION_VECTOR_CACHE);
    }
}
