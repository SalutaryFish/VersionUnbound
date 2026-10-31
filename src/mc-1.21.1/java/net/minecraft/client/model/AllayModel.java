package net.minecraft.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.animal.allay.Allay;

public class AllayModel extends HierarchicalModel<Allay> implements ArmedModel {
    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart body;
    private final ModelPart right_arm;
    private final ModelPart left_arm;
    private final ModelPart right_wing;
    private final ModelPart left_wing;
    private static final float FLYING_ANIMATION_X_ROT = (float) (Math.PI / 4);
    private static final float MAX_HAND_HOLDING_ITEM_X_ROT_RAD = -1.134464F;
    private static final float MIN_HAND_HOLDING_ITEM_X_ROT_RAD = (float) (-Math.PI / 3);

    public AllayModel(ModelPart root) {
        super(RenderType::entityTranslucent);
        this.root = root.getChild("root");
        this.head = this.root.getChild("head");
        this.body = this.root.getChild("body");
        this.right_arm = this.body.getChild("right_arm");
        this.left_arm = this.body.getChild("left_arm");
        this.right_wing = this.body.getChild("right_wing");
        this.left_wing = this.body.getChild("left_wing");
    }

    @Override
    public ModelPart root() {
        return this.root;
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshDefinition = new MeshDefinition();
        PartDefinition partDefinition = meshDefinition.getRoot();
        PartDefinition partDefinition2 = partDefinition.addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offset(0.0F, 23.5F, 0.0F));
        partDefinition2.addOrReplaceChild(
            "head",
            CubeListBuilder.create().texOffs(0, 0).addBox(-2.5F, -5.0F, -2.5F, 5.0F, 5.0F, 5.0F, new CubeDeformation(0.0F)),
            PartPose.offset(0.0F, -3.99F, 0.0F)
        );
        PartDefinition partDefinition3 = partDefinition2.addOrReplaceChild(
            "body",
            CubeListBuilder.create()
                .texOffs(0, 10)
                .addBox(-1.5F, 0.0F, -1.0F, 3.0F, 4.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(0, 16)
                .addBox(-1.5F, 0.0F, -1.0F, 3.0F, 5.0F, 2.0F, new CubeDeformation(-0.2F)),
            PartPose.offset(0.0F, -4.0F, 0.0F)
        );
        partDefinition3.addOrReplaceChild(
            "right_arm",
            CubeListBuilder.create().texOffs(23, 0).addBox(-0.75F, -0.5F, -1.0F, 1.0F, 4.0F, 2.0F, new CubeDeformation(-0.01F)),
            PartPose.offset(-1.75F, 0.5F, 0.0F)
        );
        partDefinition3.addOrReplaceChild(
            "left_arm",
            CubeListBuilder.create().texOffs(23, 6).addBox(-0.25F, -0.5F, -1.0F, 1.0F, 4.0F, 2.0F, new CubeDeformation(-0.01F)),
            PartPose.offset(1.75F, 0.5F, 0.0F)
        );
        partDefinition3.addOrReplaceChild(
            "right_wing",
            CubeListBuilder.create().texOffs(16, 14).addBox(0.0F, 1.0F, 0.0F, 0.0F, 5.0F, 8.0F, new CubeDeformation(0.0F)),
            PartPose.offset(-0.5F, 0.0F, 0.6F)
        );
        partDefinition3.addOrReplaceChild(
            "left_wing",
            CubeListBuilder.create().texOffs(16, 14).addBox(0.0F, 1.0F, 0.0F, 0.0F, 5.0F, 8.0F, new CubeDeformation(0.0F)),
            PartPose.offset(0.5F, 0.0F, 0.6F)
        );
        return LayerDefinition.create(meshDefinition, 32, 32);
    }

    /**
     * Sets this entity's model rotation angles
     */
    public void setupAnim(Allay entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        this.root().getAllParts().forEach(ModelPart::resetPose);
        float f = ageInTicks * 20.0F * (float) (Math.PI / 180.0) + limbSwing;
        float g = Mth.cos(f) * (float) Math.PI * 0.15F + limbSwingAmount;
        float h = ageInTicks - entity.tickCount;
        float i = ageInTicks * 9.0F * (float) (Math.PI / 180.0);
        float j = Math.min(limbSwingAmount / 0.3F, 1.0F);
        float k = 1.0F - j;
        float l = entity.getHoldingItemAnimationProgress(h);
        if (entity.isDancing()) {
            float m = ageInTicks * 8.0F * (float) (Math.PI / 180.0) + limbSwingAmount;
            float n = Mth.cos(m) * 16.0F * (float) (Math.PI / 180.0);
            float o = entity.getSpinningProgress(h);
            float p = Mth.cos(m) * 14.0F * (float) (Math.PI / 180.0);
            float q = Mth.cos(m) * 30.0F * (float) (Math.PI / 180.0);
            this.root.yRot = entity.isSpinning() ? (float) (Math.PI * 4) * o : this.root.yRot;
            this.root.zRot = n * (1.0F - o);
            this.head.yRot = q * (1.0F - o);
            this.head.zRot = p * (1.0F - o);
        } else {
            this.head.xRot = headPitch * (float) (Math.PI / 180.0);
            this.head.yRot = netHeadYaw * (float) (Math.PI / 180.0);
        }

        this.right_wing.xRot = 0.43633232F * (1.0F - j);
        this.right_wing.yRot = (float) (-Math.PI / 4) + g;
        this.left_wing.xRot = 0.43633232F * (1.0F - j);
        this.left_wing.yRot = (float) (Math.PI / 4) - g;
        this.body.xRot = j * (float) (Math.PI / 4);
        float m = l * Mth.lerp(j, (float) (-Math.PI / 3), -1.134464F);
        this.root.y = this.root.y + (float)Math.cos(i) * 0.25F * k;
        this.right_arm.xRot = m;
        this.left_arm.xRot = m;
        float n = k * (1.0F - l);
        float o = 0.43633232F - Mth.cos(i + (float) (Math.PI * 3.0 / 2.0)) * (float) Math.PI * 0.075F * n;
        this.left_arm.zRot = -o;
        this.right_arm.zRot = o;
        this.right_arm.yRot = 0.27925268F * l;
        this.left_arm.yRot = -0.27925268F * l;
    }

    @Override
    public void translateToHand(HumanoidArm side, PoseStack poseStack) {
        float f = 1.0F;
        float g = 3.0F;
        this.root.translateAndRotate(poseStack);
        this.body.translateAndRotate(poseStack);
        poseStack.translate(0.0F, 0.0625F, 0.1875F);
        poseStack.mulPose(Axis.XP.rotation(this.right_arm.xRot));
        poseStack.scale(0.7F, 0.7F, 0.7F);
        poseStack.translate(0.0625F, 0.0F, 0.0F);
    }
}
